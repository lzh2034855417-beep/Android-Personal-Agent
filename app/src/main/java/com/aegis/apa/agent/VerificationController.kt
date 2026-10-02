package com.aegis.apa.agent

import com.aegis.apa.model.*
import java.time.Instant
import java.util.UUID
import java.util.concurrent.Executor

data class VerificationUiState(
    val records: List<VerificationRecord> = emptyList(),
    val selectedId: String? = null,
    val loading: Boolean = true,
    val saving: Boolean = false,
    val storageFailure: VerificationStorageFailure? = null,
    val unsavedRecord: VerificationRecord? = null
)

class VerificationController(
    private val store: VerificationStore,
    private val clock: () -> Instant,
    private val executor: Executor,
    private val onState: (VerificationUiState) -> Unit,
    private val publishExecutor: Executor = Executor { it.run() }
) {
    private val lock = Any()
    private var currentState = VerificationUiState()
    val state: VerificationUiState get() = synchronized(lock) { currentState }
    private var scopeId: String? = null
    private var disposed = false
    private var generation = 0L
    private var publicationVersion = 0L
    private var creatingId: String? = null
    private var clearing = false
    private var pendingWrites = 0
    private val desiredRecords = linkedMapOf<String, VerificationRecord>()
    private val idVersions = mutableMapOf<String, Long>()
    private val deleting = mutableSetOf<String>()
    private val queue = ArrayDeque<() -> Unit>()
    private var running = false

    init {
        synchronized(lock) {
            publish()
            enqueue {
                val loaded = store.load()
                synchronized(lock) {
                    if (loaded.failure == null) scopeId = loaded.scopeId
                    currentState = currentState.copy(records = loaded.records, loading = false, storageFailure = loaded.failure)
                    publish()
                }
            }
        }
    }

    fun createBaseline(title: String, target: VerificationTarget, summary: DiagnosticVerificationSummary) = synchronized(lock) {
        val scope = scopeId
        if (!canMutate() || creatingId != null || scope == null) return@synchronized
        val now = clock()
        val record = VerificationRecord(UUID.randomUUID().toString(), 1, 1, scope, now, now, 0,
            title, VerificationState.BASELINE_SAVED, target, summary, null, null, null)
        creatingId = record.id
        currentState = currentState.copy(selectedId = record.id)
        save(record)
    }

    fun recordChange(id: String, expectedRevision: Long, change: VerificationChange) =
        update(id, expectedRevision) { record, now ->
            VerificationRecordReducer.recordChange(record, change.copy(confirmedAt = now), now)
        }

    fun recordRetest(id: String, expectedRevision: Long, summary: DiagnosticVerificationSummary) =
        update(id, expectedRevision) { record, now -> VerificationRecordReducer.recordRetest(record, summary, now) }

    fun close(id: String, expectedRevision: Long, reason: VerificationCloseReason) =
        update(id, expectedRevision) { record, now -> VerificationRecordReducer.close(record, reason, now) }

    fun delete(id: String) = synchronized(lock) {
        if (!canMutate() || id in deleting || findRecord(id) == null) return@synchronized
        deleting += id
        desiredRecords.remove(id)
        val version = invalidate(id)
        val submittedGeneration = generation
        mutation {
            val result = store.delete(id)
            synchronized(lock) {
                if (submittedGeneration == generation && idVersions[id] == version) {
                    deleting -= id
                    if (creatingId == id) creatingId = null
                    applyResult(result)
                    if (result.failure == null && currentState.selectedId == id) {
                        currentState = currentState.copy(selectedId = null)
                    }
                }
            }
        }
    }

    /** Explicit user action; independent from ordinary chat clearing. */
    fun clear() = synchronized(lock) {
        if (disposed || currentState.loading || clearing) return@synchronized
        clearing = true
        val submittedGeneration = ++generation
        desiredRecords.clear()
        mutation {
            val result = store.clear()
            synchronized(lock) {
                if (submittedGeneration == generation) {
                    clearing = false
                    creatingId = null
                    deleting.clear()
                    applyResult(result)
                    if (result.failure == null) currentState = currentState.copy(selectedId = null)
                }
            }
        }
    }

    fun select(id: String?) = synchronized(lock) {
        if (disposed || (id != null && findRecord(id) == null)) return@synchronized
        currentState = currentState.copy(selectedId = id)
        publish()
    }

    /** Stop page callbacks, while already submitted legitimate writes keep their place in the queue. */
    fun dispose() = synchronized(lock) { disposed = true }

    private fun canMutate() = !disposed && !currentState.loading && !clearing && scopeId != null
    private fun findRecord(id: String) = desiredRecords[id] ?: currentState.records.firstOrNull { it.id == id }
    private fun invalidate(id: String): Long = ((idVersions[id] ?: 0) + 1).also { idVersions[id] = it }

    private fun update(id: String, expectedRevision: Long, reduce: (VerificationRecord, Instant) -> VerificationRecord) =
        synchronized(lock) {
            if (!canMutate() || id in deleting) return@synchronized
            val record = findRecord(id) ?: return@synchronized
            if (record.revision != expectedRevision) return@synchronized
            val updated = try { reduce(record, clock()) } catch (_: VerificationTransitionException) { return@synchronized }
            save(updated)
        }

    private fun save(record: VerificationRecord) {
        desiredRecords[record.id] = record
        val version = invalidate(record.id)
        val submittedGeneration = generation
        mutation {
            val valid = synchronized(lock) {
                submittedGeneration == generation && idVersions[record.id] == version &&
                    desiredRecords[record.id]?.revision == record.revision && record.id !in deleting
            }
            val result = if (valid) store.upsert(record) else null
            synchronized(lock) {
                if (result != null && submittedGeneration == generation && idVersions[record.id] == version &&
                    desiredRecords[record.id]?.revision == record.revision) {
                    if (creatingId == record.id) creatingId = null
                    applyResult(result)
                    if (result.failure == null) desiredRecords.remove(record.id)
                }
            }
        }
    }

    private fun applyResult(result: VerificationLoadResult) {
        currentState = currentState.copy(storageFailure = result.failure)
        // A rejected write can still return the latest successfully read archive. Reconcile it
        // without presenting the rejected candidate as saved, or treating an unreadable file as empty.
        if (result.scopeId != null && result.scopeId == scopeId) {
            currentState = currentState.copy(records = result.records)
        }
        if (result.failure == null) {
            scopeId = result.scopeId
            currentState = currentState.copy(records = result.records)
        }
    }

    private fun mutation(action: () -> Unit) {
        pendingWrites++
        currentState = currentState.copy(storageFailure = null)
        publish()
        enqueue {
            try { action() }
            finally {
                synchronized(lock) {
                    pendingWrites--
                    publish()
                }
            }
        }
    }

    /** Only one job is submitted at a time, even when the supplied Executor uses many threads. */
    private fun enqueue(action: () -> Unit) {
        queue.addLast(action)
        if (!running) {
            running = true
            executeNext()
        }
    }

    private fun executeNext() {
        val action = queue.removeFirst()
        executor.execute {
            try { action() }
            finally {
                synchronized(lock) {
                    if (queue.isEmpty()) running = false else executeNext()
                }
            }
        }
    }

    private fun publish() {
        currentState = currentState.copy(saving = pendingWrites > 0,
            unsavedRecord = desiredRecords[currentState.selectedId] ?: desiredRecords.values.lastOrNull())
        if (disposed) return
        val snapshot = currentState
        val version = ++publicationVersion
        publishExecutor.execute {
            synchronized(lock) { if (!disposed && publicationVersion == version) onState(snapshot) }
        }
    }
}
