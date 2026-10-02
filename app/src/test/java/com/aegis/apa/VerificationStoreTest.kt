package com.aegis.apa

import com.aegis.apa.agent.*
import com.aegis.apa.model.*
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.nio.ByteBuffer
import java.time.Instant
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.spec.GCMParameterSpec

class VerificationStoreTest {
    @get:Rule val folder = TemporaryFolder()
    private val key = KeyGenerator.getInstance("AES").apply { init(128) }.generateKey()
    private var now: Instant = VerificationFixtures.end.plusSeconds(7200)
    private fun file() = File(folder.root, "verification.enc")
    private fun store(file: File = file()) = VerificationStore(file, { key }, { now })
    private fun record(store: VerificationStore, id: String = "record-1") = VerificationFixtures.record().copy(
        id = id, scopeId = requireNotNull(store.load().scopeId))

    @Test fun `intermediate preview v1 encrypted archive loads and remains editable`() {
        file().writeBytes(encrypt(VerificationFixtures.legacyPreviewBytes()))
        val store = store()
        assertEquals(listOf(VerificationFixtures.record()), store.load().records)
        val edited = VerificationFixtures.record().copy(revision = 3, title = "Edited preview")
        assertNull(store.upsert(edited).failure)
        assertEquals(listOf(edited), store().load().records)
    }

    @Test fun `absent target reserves a slot and projected retest persists without fake metrics`() {
        val target = VerificationTarget.AppGroup(listOf("com.example.target"))
        val baseline = VerificationFixtures.fullSnapshot().let { it.copy(apps = it.apps.take(19) +
            it.apps.first().copy(packageNames = target.packageNames)) }
        val retest = VerificationFixtures.fullSnapshot()
        persistProjectedRetest(baseline, retest, target, 172)
    }

    @Test fun `complete twenty group projected baseline change and retest persist losslessly`() {
        val baseline = VerificationFixtures.fullSnapshot()
        val retest = baseline.copy(apps = baseline.apps.map { it.copy(estimatedPowerMah = 80.0) })
        persistProjectedRetest(baseline, retest, VerificationTarget.AppGroup(listOf("com.example.app01")), 181)
    }

    private fun persistProjectedRetest(before: PowerDiagnosticSnapshot, after: PowerDiagnosticSnapshot,
        target: VerificationTarget.AppGroup, expectedRetestMetrics: Int) {
        val context = VerificationFixtures.context().copy(scenarioNote = "")
        fun project(snapshot: PowerDiagnosticSnapshot) = VerificationSummaryProjector.project(snapshot,
            VerificationFixtures.productionMetadata(), context, target)!!
        val baseline = project(before)
        val retest = project(after)
        val store = store()
        val controller = VerificationController(store, { now }, java.util.concurrent.Executor { it.run() }, {})
        controller.createBaseline("", target, baseline)
        assertNull("baseline save", controller.state.storageFailure)
        val saved = controller.state.records.single()
        println("Baseline metrics=" + baseline.metrics.size + " record bytes=" +
            ByteBuffer.wrap(VerificationArchive.encode(saved.scopeId, listOf(saved)))
                .getInt(8 + saved.scopeId.toByteArray(Charsets.UTF_8).size + 4) +
            " retest groups=" + retest.metrics.map { it.key.packageNames }.filter { it.isNotEmpty() }.distinct().size)
        now = now.plusSeconds(1)
        controller.recordChange(saved.id, saved.revision, VerificationFixtures.change().copy(
            description = "", rollback = "", sideEffects = ""))
        assertNull("manual change save", controller.state.storageFailure)
        now = now.plusSeconds(1)
        controller.recordRetest(saved.id, controller.state.records.single().revision, retest)
        assertNull("retest save", controller.state.storageFailure)
        assertNull(controller.state.unsavedRecord)
        val restored = store().load().records.single()
        assertEquals(VerificationState.RETEST_SAVED, restored.state)
        assertEquals(baseline, restored.baseline)
        assertEquals(retest, restored.retest)
        assertEquals(expectedRetestMetrics, restored.retest!!.metrics.size)
        assertTrue(restored.retest!!.metrics.all { it.value != null && it.status == EvidenceFieldStatus.PARSED })
        if (expectedRetestMetrics == 172) {
            assertFalse(restored.retest!!.metrics.any { it.key.packageNames == target.packageNames })
            assertEquals(19, restored.retest!!.metrics.map { it.key.packageNames }.filter { it.isNotEmpty() }.distinct().size)
        }
        println("Projected retest metrics=$expectedRetestMetrics record bytes=" +
            ByteBuffer.wrap(VerificationArchive.encode(restored.scopeId, listOf(restored)))
                .getInt(8 + restored.scopeId.toByteArray(Charsets.UTF_8).size + 4))
        controller.dispose()
    }

    @Test fun `encrypted round trip has no package or note and empty scope survives reopening`() {
        val store = store()
        val scope = store.load().scopeId
        assertFalse(scope.isNullOrBlank())
        val record = record(store)
        assertNull(store.upsert(record).failure)
        val raw = file().readBytes()
        assertFalse(String(raw, Charsets.ISO_8859_1).contains("com.example.app"))
        assertFalse(String(raw, Charsets.ISO_8859_1).contains("Disable optional sync"))
        assertEquals(listOf(record), store().load().records)
        assertNull(store.delete(record.id).failure)
        assertEquals(scope, store().load().scopeId)
        assertNull(store.clear().failure)
        assertEquals(scope, store().load().scopeId)
    }

    @Test fun `twenty first and oversized record leave ciphertext unchanged`() {
        val store = store()
        val first = record(store)
        (1..20).forEach { assertNull(store.upsert(first.copy(id = "r$it")).failure) }
        val original = file().readBytes()
        assertEquals(VerificationStorageFailure.LIMIT, store.upsert(first.copy(id = "r21")).failure)
        assertArrayEquals(original, file().readBytes())
        val huge = first.copy(id = "r1", revision = first.revision + 1,
            target = VerificationTarget.AppGroup((1..40).map { "p$it" + "中".repeat(500) }))
        assertEquals(VerificationStorageFailure.LIMIT, store.upsert(huge).failure)
        assertArrayEquals(original, file().readBytes())
    }

    @Test fun `authentication damage and wrong key differ from unavailable key`() {
        val store = store()
        assertNull(store.upsert(record(store)).failure)
        val original = file().readBytes()
        val wrong = KeyGenerator.getInstance("AES").apply { init(128) }.generateKey()
        assertEquals(VerificationStorageFailure.DAMAGED, VerificationStore(file(), { wrong }, { now }).load().failure)
        assertEquals(VerificationStorageFailure.KEY_UNAVAILABLE,
            VerificationStore(file(), { throw IllegalStateException("locked") }, { now }).load().failure)
        file().writeBytes(original.also { it[it.lastIndex] = (it.last().toInt() xor 1).toByte() })
        assertEquals(VerificationStorageFailure.DAMAGED, store.load().failure)
        val damaged = file().readBytes()
        assertEquals(VerificationStorageFailure.DAMAGED, store.upsert(VerificationFixtures.record()).failure)
        assertArrayEquals(damaged, file().readBytes())
        assertNull(store.clear().failure)
        assertTrue(store.load().records.isEmpty())
    }

    @Test fun `unsupported schema oversized ciphertext and hostile archive remain untouched`() {
        val store = store()
        val scope = requireNotNull(store.load().scopeId)
        val unknown = VerificationArchive.encode(scope, emptyList()).also { ByteBuffer.wrap(it).putInt(2) }
        file().writeBytes(encrypt(unknown))
        val original = file().readBytes()
        assertEquals(VerificationStorageFailure.UNSUPPORTED, store.upsert(VerificationFixtures.record()).failure)
        assertArrayEquals(original, file().readBytes())
        file().writeBytes(ByteArray(1024 * 1024 + 29))
        assertEquals(VerificationStorageFailure.LIMIT, store.load().failure)
        val hostile = VerificationArchive.encode(scope, emptyList()).also { ByteBuffer.wrap(it).putInt(4, Int.MAX_VALUE) }
        file().writeBytes(encrypt(hostile))
        assertEquals(VerificationStorageFailure.LIMIT, store.load().failure)
    }

    @Test fun `pending directory blocks writes and deletion without changing original`() {
        val store = store()
        val first = record(store)
        assertNull(store.upsert(first).failure)
        val original = file().readBytes()
        assertTrue(File(folder.root, "verification.enc.pending").mkdir())
        assertEquals(VerificationStorageFailure.WRITE_FAILED, store.delete(first.id).failure)
        assertArrayEquals(original, file().readBytes())
        assertEquals(listOf(first), store.load().records)
    }

    @Test fun `expiration cleanup preserves scope and failure preserves file`() {
        val store = store()
        val first = record(store)
        assertNull(store.upsert(first).failure)
        val original = file().readBytes()
        now = first.createdAt.plusSeconds(30 * 86400)
        val blocker = File(folder.root, "verification.enc.pending")
        assertTrue(blocker.mkdir())
        val failed = store.load()
        assertEquals(VerificationStorageFailure.WRITE_FAILED, failed.failure)
        assertEquals(listOf(first.id), failed.expiredIds)
        assertArrayEquals(original, file().readBytes())
        assertTrue(blocker.delete())
        assertNull(store.load().failure)
        assertEquals(first.scopeId, store().load().scopeId)
        assertTrue(store().load().records.isEmpty())
    }

    @Test fun `latest mutations merge and queued stale saves cannot resurrect delete or clear`() {
        val store = store()
        val first = record(store)
        val executor = Executors.newSingleThreadExecutor()
        try {
            val saved = executor.submit<VerificationLoadResult> { store.upsert(first) }
            val deleted = executor.submit<VerificationLoadResult> { store.delete(first.id) }
            assertNull(saved.get(5, TimeUnit.SECONDS).failure)
            assertNull(deleted.get(5, TimeUnit.SECONDS).failure)
            assertEquals(VerificationStorageFailure.WRITE_FAILED, store.upsert(first).failure)
            assertEquals(VerificationStorageFailure.WRITE_FAILED, store.upsert(first.copy(revision = 99)).failure)
            assertTrue(store.load().records.isEmpty())
            assertNull(store.upsert(first.copy(id = "new")).failure)
            assertNull(store.clear().failure)
            assertEquals(VerificationStorageFailure.WRITE_FAILED, store.upsert(first.copy(id = "new")).failure)
            assertTrue(store.load().records.isEmpty())
        } finally { executor.shutdownNow() }
    }

    @Test fun `concurrent stores share serialized latest state and deletion tombstones`() {
        val firstStore = store()
        val secondStore = store()
        val first = record(firstStore)
        val gate = CountDownLatch(1)
        val executor = Executors.newFixedThreadPool(2)
        try {
            val a = executor.submit<VerificationLoadResult> { gate.await(); firstStore.upsert(first) }
            val b = executor.submit<VerificationLoadResult> { gate.await(); secondStore.upsert(first.copy(id = "record-2")) }
            gate.countDown()
            assertNull(a.get(5, TimeUnit.SECONDS).failure)
            assertNull(b.get(5, TimeUnit.SECONDS).failure)
            assertEquals(setOf("record-1", "record-2"), firstStore.load().records.map { it.id }.toSet())
            assertNull(firstStore.delete(first.id).failure)
            assertEquals(VerificationStorageFailure.WRITE_FAILED, secondStore.upsert(first).failure)
        } finally { executor.shutdownNow() }
    }

    @Test fun `stale revisions scope and creation mutation preserve latest record`() {
        val store = store()
        val first = record(store)
        assertNull(store.upsert(first).failure)
        val latest = first.copy(revision = 3, title = "latest")
        assertNull(store.upsert(latest).failure)
        val original = file().readBytes()
        listOf(first, latest, latest.copy(revision = 4, scopeId = "other"),
            latest.copy(revision = 4, createdAt = now), latest.copy(revision = 4, schemaVersion = 2),
            latest.copy(revision = 4, ruleVersion = 2)).forEach {
            assertEquals(VerificationStorageFailure.WRITE_FAILED, store.upsert(it).failure)
            assertArrayEquals(original, file().readBytes())
        }
        assertEquals(listOf(latest), store.load().records)
    }

    @Test fun `new expired record cannot claim persistent save`() {
        val store = store()
        val first = record(store)
        now = first.createdAt.plusSeconds(30 * 86400)
        val original = file().readBytes()
        assertEquals(VerificationStorageFailure.WRITE_FAILED, store.upsert(first).failure)
        assertArrayEquals(original, file().readBytes())
        assertTrue(store.load().records.isEmpty())
    }

    @Test fun `fresh archive key failure and parent write failure are reported without a file`() {
        assertEquals(VerificationStorageFailure.KEY_UNAVAILABLE,
            VerificationStore(file(), { throw IllegalStateException("unavailable") }, { now }).load().failure)
        assertFalse(file().exists())
        val parentFile = folder.newFile("not-a-directory")
        val destination = File(parentFile, "verification.enc")
        assertEquals(VerificationStorageFailure.WRITE_FAILED, store(destination).load().failure)
        assertFalse(destination.exists())
    }

    @Test fun `failed clear preserves archive and does not poison future revision`() {
        val store = store()
        val first = record(store)
        assertNull(store.upsert(first).failure)
        val original = file().readBytes()
        val blocker = File(folder.root, "verification.enc.pending")
        assertTrue(blocker.mkdir())
        assertEquals(VerificationStorageFailure.WRITE_FAILED, store.clear().failure)
        assertArrayEquals(original, file().readBytes())
        assertTrue(blocker.delete())
        val edited = first.copy(revision = 3, title = "still editable")
        assertNull(store.upsert(edited).failure)
        assertEquals(listOf(edited), store.load().records)
    }

    @Test fun `clear preserves healthy ciphertext when key is temporarily unavailable`() {
        val initial = store()
        val first = record(initial)
        assertNull(initial.upsert(first).failure)
        val original = file().readBytes()
        var requests = 0
        val unavailable = VerificationStore(file(), {
            requests++
            if (requests == 1) throw IllegalStateException("temporarily unavailable")
            key
        }, { now })
        assertEquals(VerificationStorageFailure.KEY_UNAVAILABLE, unavailable.clear().failure)
        assertArrayEquals(original, file().readBytes())
        assertEquals(1, requests)
        assertEquals(listOf(first), initial.load().records)
    }

    @Test fun `clear preserves oversized ciphertext and limit error`() {
        val oversized = ByteArray(1024 * 1024 + 29) { 7 }
        file().writeBytes(oversized)
        assertEquals(VerificationStorageFailure.LIMIT, store().clear().failure)
        assertArrayEquals(oversized, file().readBytes())
    }

    @Test fun `explicit clear rebuilds unsupported archive with new scope`() {
        val store = store()
        val scope = requireNotNull(store.load().scopeId)
        val unsupported = VerificationArchive.encode(scope, emptyList()).also { ByteBuffer.wrap(it).putInt(2) }
        file().writeBytes(encrypt(unsupported))
        assertEquals(VerificationStorageFailure.UNSUPPORTED, store.load().failure)
        val cleared = store.clear()
        assertNull(cleared.failure)
        assertFalse(cleared.scopeId.isNullOrBlank())
        assertNotEquals(scope, cleared.scopeId)
        assertEquals(cleared.scopeId, store.load().scopeId)
        assertTrue(store.load().records.isEmpty())
    }

    private fun encrypt(plain: ByteArray): ByteArray {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, key, GCMParameterSpec(128, ByteArray(12) { it.toByte() }))
        return cipher.iv + cipher.doFinal(plain)
    }
}
