package com.aegis.apa

import com.aegis.apa.agent.VerificationRecordReducer
import com.aegis.apa.model.*
import org.junit.Assert.*
import org.junit.Test

class VerificationModelTest {
    // Catches UTF-16 truncation dropping supplementary characters from every editable note.
    @Test fun notesRetainFiveHundredCodePointsAtConstructionAndCopy() {
        val input = "😀".repeat(500) + "x"
        val expected = "😀".repeat(500)
        val change = VerificationFixtures.change().copy(description = input, rollback = input, sideEffects = input)
        assertEquals(expected, change.description)
        assertEquals(expected, change.rollback)
        assertEquals(expected, change.sideEffects)
        assertEquals(expected, VerificationFixtures.context().copy(scenarioNote = input).scenarioNote)
    }

    // Catches keeping isolated surrogates, stripping real pairs, or leaving control characters.
    @Test fun malformedSurrogatesBecomeReplacementCharactersAndControlsBecomeSpaces() {
        val input = "\uD800a\uDC00\uD83D\uDE00\uD800\uD800\uDC00\u0000\n\u0085"
        val expected = "\uFFFDa\uFFFD😀\uFFFD\uD800\uDC00   "
        assertEquals(expected, VerificationFixtures.record().copy(title = input).title)
        val change = VerificationFixtures.change().copy(description = input, rollback = input, sideEffects = input)
        assertEquals(expected, change.description)
        assertEquals(expected, change.rollback)
        assertEquals(expected, change.sideEffects)
        assertEquals(expected, VerificationFixtures.context().copy(scenarioNote = input).scenarioNote)
    }

    // Catches claiming grapheme limits: combining marks each consume their own code point.
    @Test fun chineseAndCombiningMarksUseCodePointLimits() {
        assertEquals("中".repeat(80), VerificationFixtures.record().copy(title = "中".repeat(81)).title)
        assertEquals("e\u0301".repeat(40), VerificationFixtures.record().copy(title = "e\u0301".repeat(41)).title)
        assertEquals("a".repeat(79) + "e", VerificationFixtures.record().copy(title = "a".repeat(79) + "e\u0301").title)
    }

    @Test
    fun changeEditInvalidatesRetest() {
        // Catches retaining a retest after its adjustment evidence changes.
        val original = VerificationFixtures.record()
        val now = original.updatedAt.plusSeconds(60)
        val result = VerificationRecordReducer.recordChange(original, original.change!!.copy(description = "New adjustment"), now)
        assertEquals(3L, result.revision)
        assertNull(result.retest)
        assertEquals(VerificationState.CHANGE_RECORDED, result.state)
        assertSame(original.baseline, result.baseline)
        assertEquals(now, result.updatedAt)
        assertNotNull(original.retest)
    }

    @Test
    fun textIsControlFreeAndBoundedAtConstructionAndCopy() {
        // Catches allowing copied or directly constructed notes to bypass storage limits.
        val record = VerificationFixtures.record().copy(title = "a\u0000\nb" + "x".repeat(100))
        assertEquals(80, record.title.length)
        assertFalse(record.title.any(Char::isISOControl))
        val change = VerificationFixtures.change().copy(
            description = "a\u0000" + "x".repeat(600), rollback = "r\r" + "x".repeat(600),
            sideEffects = "s\t" + "x".repeat(600)
        )
        listOf(change.description, change.rollback, change.sideEffects).forEach {
            assertEquals(500, it.length)
            assertFalse(it.any(Char::isISOControl))
        }
        val context = VerificationFixtures.context().copy(scenarioNote = "a\n" + "x".repeat(600))
        assertEquals(500, context.scenarioNote.length)
        assertFalse(context.scenarioNote.any(Char::isISOControl))
    }

    @Test
    fun packageSetsAreCanonicalKeys() {
        // Catches UID-era identity and duplicate/order-dependent package keys.
        assertEquals(listOf("a", "z"), VerificationTarget.AppGroup(listOf("z", "a", "z")).packageNames)
        assertEquals(VerificationMetricKey(VerificationMetricKind.APP_TOTAL_POWER, listOf("a", "z")),
            VerificationMetricKey(VerificationMetricKind.APP_TOTAL_POWER, listOf("z", "a", "a")))
        assertEquals(emptyList<String>(), VerificationMetricKey(VerificationMetricKind.OVERALL_POWER, listOf("a")).packageNames)
    }

    @Test
    fun reducerAcceptsTheLifecycleAndReplacementRetest() {
        // Catches a lost revision/state transition or accidental baseline replacement.
        val baseline = VerificationFixtures.record().copy(state = VerificationState.BASELINE_SAVED, change = null, retest = null, revision = 0)
        val changed = VerificationRecordReducer.recordChange(baseline, VerificationFixtures.change(), baseline.updatedAt)
        val retested = VerificationRecordReducer.recordRetest(changed, VerificationFixtures.summary(80.0), baseline.updatedAt)
        val replacement = VerificationRecordReducer.recordRetest(retested, VerificationFixtures.summary(70.0), baseline.updatedAt)
        val closed = VerificationRecordReducer.close(replacement, VerificationCloseReason.ROLLED_BACK, baseline.updatedAt)
        assertEquals(VerificationState.RETEST_SAVED, replacement.state)
        assertEquals(70.0, replacement.retest!!.metrics.single().value!!, 0.0)
        assertEquals(4L, closed.revision)
        assertEquals(VerificationState.CLOSED, closed.state)
        assertEquals(VerificationCloseReason.ROLLED_BACK, closed.closeReason)
        assertSame(baseline.baseline, closed.baseline)
    }

    @Test
    fun reducerRejectsRetestBeforeChangeAndEveryMutationAfterClose() {
        // Catches illegal transitions being silently accepted.
        val baseline = VerificationFixtures.record().copy(state = VerificationState.BASELINE_SAVED, change = null, retest = null)
        assertThrows(VerificationTransitionException::class.java) {
            VerificationRecordReducer.recordRetest(baseline, VerificationFixtures.summary(), baseline.updatedAt)
        }
        val closed = VerificationRecordReducer.close(baseline, VerificationCloseReason.ABANDONED, baseline.updatedAt)
        assertThrows(VerificationTransitionException::class.java) {
            VerificationRecordReducer.recordChange(closed, VerificationFixtures.change(), closed.updatedAt)
        }
        assertThrows(VerificationTransitionException::class.java) {
            VerificationRecordReducer.recordRetest(closed, VerificationFixtures.summary(), closed.updatedAt)
        }
        assertThrows(VerificationTransitionException::class.java) {
            VerificationRecordReducer.close(closed, VerificationCloseReason.COMPLETED, closed.updatedAt)
        }
    }
}
