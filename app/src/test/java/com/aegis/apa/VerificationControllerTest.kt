package com.aegis.apa

import com.aegis.apa.agent.*
import com.aegis.apa.model.*
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.time.Instant
import java.util.concurrent.Executor
import javax.crypto.KeyGenerator

class VerificationControllerTest {
    @get:Rule val folder = TemporaryFolder()
    private val key = KeyGenerator.getInstance("AES").apply { init(128) }.generateKey()
    private var now = VerificationFixtures.end.plusSeconds(7200)
    private val worker = QueueExecutor()
    private val published = mutableListOf<VerificationUiState>()
    private fun store(file: File = File(folder.root, "verification.enc")) = VerificationStore(file, { key }, { now })
    private fun controller(store: VerificationStore) = VerificationController(store, { now }, worker, published::add)
    private fun seed(store: VerificationStore, id: String = "a"): VerificationRecord {
        val record = VerificationFixtures.record().copy(id = id, scopeId = requireNotNull(store.load().scopeId))
        assertNull(store.upsert(record).failure)
        return record
    }
    private fun loaded(store: VerificationStore): VerificationController = controller(store).also { worker.runAll() }

    // Removing the id invalidation would let the queued retest revive a deleted record.
    @Test fun deleteDuringQueuedSaveCannotRestoreRecord() {
        val store = store(); val record = seed(store); val controller = loaded(store)
        controller.recordRetest(record.id, 2, VerificationFixtures.summary(70.0))
        controller.delete(record.id)
        worker.runAll()
        assertTrue(controller.state.records.isEmpty())
        assertTrue(store.load().records.isEmpty())
        assertNull(controller.state.unsavedRecord)
    }

    // A stale caller revision must not replace the newly edited adjustment.
    @Test fun oldRetestAfterChangeEditIsIgnored() {
        val store = store(); val record = seed(store); val controller = loaded(store)
        controller.recordChange(record.id, 2, VerificationFixtures.change().copy(description = "new adjustment"))
        controller.recordRetest(record.id, 2, VerificationFixtures.summary(70.0))
        worker.runAll()
        val saved = controller.state.records.single()
        assertEquals("new adjustment", saved.change?.description)
        assertNull(saved.retest)
        assertEquals(3L, saved.revision)
        assertEquals(saved, store.load().records.single())
    }

    @Test fun editInvalidatesAlreadyQueuedRetest() {
        val store = store(); val record = seed(store); val controller = loaded(store)
        controller.recordRetest(record.id, 2, VerificationFixtures.summary(70.0))
        controller.recordChange(record.id, 3, VerificationFixtures.change().copy(description = "edited"))
        worker.runAll()
        assertEquals("edited", store.load().records.single().change?.description)
        assertNull(store.load().records.single().retest)
        assertEquals(4L, controller.state.records.single().revision)
    }

    // Completing a save cannot restore the selection captured when it started.
    @Test fun selectionSwitchDoesNotReceiveOldResult() {
        val store = store(); seed(store); seed(store, "b"); val controller = loaded(store)
        controller.select("a")
        controller.recordChange("a", 2, VerificationFixtures.change().copy(description = "edited a"))
        controller.select("b")
        worker.runAll()
        assertEquals("b", controller.state.selectedId)
        assertEquals(2L, controller.state.records.single { it.id == "b" }.revision)
        assertEquals("edited a", controller.state.records.single { it.id == "a" }.change?.description)
    }

    @Test fun doubleSubmitDoesNotCreateTwoRecords() {
        val store = store(); val controller = loaded(store)
        repeat(2) { controller.createBaseline("trial", VerificationTarget.Overall, VerificationFixtures.summary()) }
        assertTrue(controller.state.records.isEmpty())
        assertNotNull(controller.state.unsavedRecord)
        assertTrue(controller.state.saving)
        worker.runAll()
        assertEquals(1, store.load().records.size)
        assertEquals(1, controller.state.records.size)
        assertNull(controller.state.unsavedRecord)
        assertFalse(controller.state.saving)
        assertEquals(store.load().scopeId, controller.state.records.single().scopeId)
    }

    @Test fun editingPendingBaselineDoesNotUnlockDuplicateCreation() {
        val store = store(); val controller = loaded(store)
        controller.createBaseline("trial", VerificationTarget.Overall, VerificationFixtures.summary())
        val id = requireNotNull(controller.state.unsavedRecord).id
        controller.recordChange(id, 0, VerificationFixtures.change())
        worker.runNext() // The obsolete baseline job completes before its replacement save.
        controller.createBaseline("trial", VerificationTarget.Overall, VerificationFixtures.summary())
        worker.runAll()
        assertEquals(1, store.load().records.size)
        assertEquals(VerificationState.CHANGE_RECORDED, store.load().records.single().state)
    }

    @Test fun failedWriteRemainsExplicitlyUnsavedAndKeepsSavedBaseline() {
        val file = File(folder.root, "verification.enc"); val store = store(file); seed(store)
        val controller = loaded(store)
        File(folder.root, "verification.enc.pending").mkdir()
        controller.select("a")
        controller.recordChange("a", 2, VerificationFixtures.change().copy(description = "unsaved"))
        worker.runAll()
        assertEquals(VerificationStorageFailure.WRITE_FAILED, controller.state.storageFailure)
        assertEquals(2L, controller.state.records.single().revision)
        assertEquals("unsaved", controller.state.unsavedRecord?.change?.description)
        assertEquals(3L, controller.state.unsavedRecord?.revision)
        assertEquals(2L, store.load().records.single().revision)
        assertFalse(controller.state.saving)
    }

    @Test fun failedEditAfterInFlightRetestShowsLatestDurableRecord() {
        val file = File(folder.root, "verification.enc"); seed(store(file))
        var keyCalls = 0
        var duringRead: (() -> Unit)? = null
        val hookedStore = VerificationStore(file, {
            keyCalls++
            duringRead?.invoke()
            key
        }, { now })
        val controller = loaded(hookedStore)
        keyCalls = 0
        duringRead = {
            if (keyCalls == 1) {
                controller.recordChange("a", 3, VerificationFixtures.change().copy(description = "unsaved edit"))
            } else if (keyCalls == 3) {
                File(folder.root, "verification.enc.pending").mkdir()
                duringRead = null
            }
        }
        controller.recordRetest("a", 2, VerificationFixtures.summary(70.0))
        worker.runAll()
        val durable = hookedStore.load().records.single()
        assertEquals(3L, durable.revision)
        assertEquals(70.0, durable.retest?.metrics?.single()?.value)
        assertEquals(durable, controller.state.records.single())
        assertEquals(4L, controller.state.unsavedRecord?.revision)
        assertEquals(VerificationStorageFailure.WRITE_FAILED, controller.state.storageFailure)
    }

    @Test fun failedLoadDoesNotInventScopeOrClaimSaved() {
        val file = File(folder.root, "verification.enc").apply { writeBytes(byteArrayOf(1)) }
        val controller = loaded(store(file))
        controller.createBaseline("blocked", VerificationTarget.Overall, VerificationFixtures.summary())
        worker.runAll()
        assertEquals(VerificationStorageFailure.DAMAGED, controller.state.storageFailure)
        assertTrue(controller.state.records.isEmpty())
        assertNull(controller.state.unsavedRecord)
        assertArrayEquals(byteArrayOf(1), file.readBytes())
    }

    @Test fun clearInvalidatesQueuedNewBaselineAndRetainsScope() {
        val store = store(); val controller = loaded(store); val scope = store.load().scopeId
        controller.createBaseline("trial", VerificationTarget.Overall, VerificationFixtures.summary())
        controller.clear()
        worker.runAll()
        assertTrue(store.load().records.isEmpty())
        assertEquals(scope, store.load().scopeId)
        assertTrue(controller.state.records.isEmpty())
        assertNull(controller.state.selectedId)
        assertNull(controller.state.unsavedRecord)
    }

    @Test fun clearWaitsForStorageAndFailedDeleteKeepsSavedRecord() {
        val store = store(); seed(store); val controller = loaded(store)
        File(folder.root, "verification.enc.pending").mkdir()
        controller.delete("a")
        assertEquals(1, controller.state.records.size)
        worker.runAll()
        assertEquals(1, controller.state.records.size)
        assertEquals(VerificationStorageFailure.WRITE_FAILED, controller.state.storageFailure)
        controller.clear()
        assertEquals(1, controller.state.records.size)
        worker.runAll()
        assertEquals(1, controller.state.records.size)
    }

    @Test fun closePreventsRetestAndUsesClickTime() {
        val store = store(); seed(store); val controller = loaded(store)
        controller.close("a", 2, VerificationCloseReason.ROLLED_BACK)
        controller.recordRetest("a", 3, VerificationFixtures.summary(70.0))
        now = now.plusSeconds(600)
        worker.runAll()
        val saved = store.load().records.single()
        assertEquals(VerificationState.CLOSED, saved.state)
        assertEquals(VerificationCloseReason.ROLLED_BACK, saved.closeReason)
        assertEquals(VerificationFixtures.end.plusSeconds(7200), saved.updatedAt)
        assertEquals(3L, saved.revision)
    }

    @Test fun adjustmentConfirmationUsesClickTimeAndBaselineIsImmutable() {
        val store = store(); val baseline = seed(store).baseline; val controller = loaded(store)
        controller.recordChange("a", 2, VerificationFixtures.change())
        val clickedAt = now; now = now.plusSeconds(600)
        worker.runAll()
        val saved = store.load().records.single()
        assertEquals(clickedAt, saved.change?.confirmedAt)
        assertEquals(clickedAt, saved.updatedAt)
        assertEquals(baseline, saved.baseline)
    }

    @Test fun recoveryLoadsOnlyStructuredSavedRecordsAndChatClearIsIndependent() {
        val store = store(); val original = seed(store)
        val controller = loaded(store)
        MainSessionViewModel().clearConversation()
        val restored = loaded(store(File(folder.root, "verification.enc")))
        assertEquals(listOf(original), restored.state.records)
        assertEquals(controller.state.records, restored.state.records)
        assertNull(restored.state.selectedId)
        assertNull(restored.state.unsavedRecord)
        assertFalse(restored.state.loading)
    }

    @Test fun disposeSuppressesCallbacksButPreservesSubmittedPersistence() {
        val store = store(); val controller = loaded(store)
        controller.createBaseline("trial", VerificationTarget.Overall, VerificationFixtures.summary())
        val count = published.size
        controller.dispose()
        controller.createBaseline("ignored", VerificationTarget.Overall, VerificationFixtures.summary())
        worker.runAll()
        assertEquals(count, published.size)
        assertEquals(1, store.load().records.size)
    }

    @Test fun disposedQueuedPublicationsCannotReachOldPage() {
        val main = QueueExecutor(); val store = store()
        val controller = VerificationController(store, { now }, worker, published::add, main)
        worker.runAll()
        controller.dispose()
        main.runAll()
        assertTrue(published.isEmpty())
    }

    @Test fun queuedPublicationCannotRevertNewSelection() {
        val main = QueueExecutor(); val store = store(); seed(store); seed(store, "b")
        val controller = VerificationController(store, { now }, worker, published::add, main)
        worker.runAll(); main.runAll(); published.clear()
        controller.select("a")
        controller.recordRetest("a", 2, VerificationFixtures.summary(70.0))
        worker.runAll() // Result publication waits behind the active UI event.
        controller.select("b")
        main.runAll()
        assertTrue(published.isNotEmpty())
        assertTrue(published.all { it.selectedId == "b" })
    }

    private class QueueExecutor : Executor {
        private val queue = ArrayDeque<Runnable>()
        override fun execute(command: Runnable) { queue.addLast(command) }
        fun runNext() { queue.removeFirst().run() }
        fun runAll() { while (queue.isNotEmpty()) queue.removeFirst().run() }
    }
}
