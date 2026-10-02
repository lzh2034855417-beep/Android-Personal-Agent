package com.aegis.apa

import com.aegis.apa.agent.*
import com.aegis.apa.model.*
import org.junit.Assert.*
import org.junit.Test
import java.nio.ByteBuffer

class VerificationArchiveTest {
    private val now = VerificationFixtures.end.plusSeconds(7200)
    private val record = VerificationFixtures.record()
    private fun encode(records: List<VerificationRecord> = listOf(record)) = VerificationArchive.encode("local-scope", records)
    private fun failure(bytes: ByteArray, want: VerificationStorageFailure) {
        try { VerificationArchive.decode(bytes, now); fail("accepted invalid archive") }
        catch (e: VerificationArchiveException) { assertEquals(want, e.failure) }
    }

    @Test fun `round trip preserves complete records including nanoseconds and stable IDs`() {
        val sample = record.copy(createdAt = record.createdAt.plusNanos(321), updatedAt = record.updatedAt.plusNanos(654))
        val result = VerificationArchive.decode(encode(listOf(sample)), now)
        assertEquals(listOf(sample), result.records)
        assertEquals("local-scope", result.scopeId)
        assertTrue(result.expiredIds.isEmpty())
        assertTrue(String(encode(), Charsets.UTF_8).contains("retest_saved"))
    }

    @Test fun `expiration is exact thirty days from creation and never update`() {
        val edited = record.copy(updatedAt = record.createdAt.plusSeconds(29 * 86400))
        val bytes = encode(listOf(edited))
        assertEquals(1, VerificationArchive.decode(bytes, record.createdAt.plusSeconds(30 * 86400 - 1)).records.size)
        val expired = VerificationArchive.decode(bytes, record.createdAt.plusSeconds(30 * 86400))
        assertTrue(expired.records.isEmpty())
        assertEquals(listOf("record-1"), expired.expiredIds)
        assertEquals("local-scope", expired.scopeId)
    }

    @Test fun `twenty records retained but twenty first refused`() {
        assertEquals(20, VerificationArchive.decode(encode((1..20).map { record.copy(id = "r$it") }), now).records.size)
        expectEncodeFailure(VerificationStorageFailure.LIMIT) { encode((1..21).map { record.copy(id = "r$it") }) }
    }

    @Test fun `single record limit counts chinese bytes rather than characters`() {
        val ascii = record.copy(target = VerificationTarget.AppGroup((1..40).map { "p$it" + "a".repeat(500) }))
        assertEquals(listOf(ascii), VerificationArchive.decode(encode(listOf(ascii)), now).records)
        val chinese = ascii.copy(target = VerificationTarget.AppGroup((1..40).map { "p$it" + "中".repeat(500) }))
        expectEncodeFailure(VerificationStorageFailure.LIMIT) { encode(listOf(chinese)) }
    }

    @Test fun `scope mismatches duplicates invalid revisions and creation changes are rejected`() {
        expectEncodeFailure(VerificationStorageFailure.DAMAGED) { encode(listOf(record.copy(scopeId = "other"))) }
        expectEncodeFailure(VerificationStorageFailure.DAMAGED) { encode(listOf(record, record)) }
        expectEncodeFailure(VerificationStorageFailure.DAMAGED) { encode(listOf(record.copy(revision = -1))) }
        val bytes = encode()
        val scope = "local-scope".toByteArray()
        val second = indexOf(bytes, scope, indexOf(bytes, scope) + scope.size)
        bytes[second] = 'x'.code.toByte()
        failure(bytes, VerificationStorageFailure.DAMAGED)
    }

    @Test fun `unknown schema and wire IDs are unsupported not empty`() {
        val schema = encode().also { ByteBuffer.wrap(it).putInt(2) }
        failure(schema, VerificationStorageFailure.UNSUPPORTED)
        val unknown = encode()
        val at = indexOf(unknown, "retest_saved".toByteArray())
        unknown[at] = 'x'.code.toByte()
        failure(unknown, VerificationStorageFailure.UNSUPPORTED)
    }

    @Test fun `negative oversized lengths malformed utf8 and trailing bytes fail bounded reads`() {
        failure(encode().also { ByteBuffer.wrap(it).putInt(4, -2) }, VerificationStorageFailure.DAMAGED)
        failure(encode().also { ByteBuffer.wrap(it).putInt(4, Int.MAX_VALUE) }, VerificationStorageFailure.LIMIT)
        failure(encode().also { it[8] = 0xc0.toByte() }, VerificationStorageFailure.DAMAGED)
        failure(encode() + byteArrayOf(0), VerificationStorageFailure.DAMAGED)
        failure(ByteArray(1024 * 1024 + 1), VerificationStorageFailure.LIMIT)
        val hostileRecord = encode().also { ByteBuffer.wrap(it).putInt(23, 49153) }
        failure(hostileRecord, VerificationStorageFailure.LIMIT)
    }

    @Test fun `empty archive retains its scope`() {
        assertEquals("local-scope", VerificationArchive.decode(encode(emptyList()), now).scopeId)
    }

    @Test fun `exact 48 KiB record accepted and next byte rejected`() {
        val base = record.copy(target = VerificationTarget.AppGroup((1..12).map { "p$it" + "a".repeat(3800) }))
        val encoded = encode(listOf(base))
        val frameSize = ByteBuffer.wrap(encoded).getInt(23)
        val remaining = 49152 - frameSize
        val exact = base.copy(target = VerificationTarget.AppGroup(base.target.let { it as VerificationTarget.AppGroup }.packageNames + "x".repeat(remaining - 4)))
        assertEquals(49152, ByteBuffer.wrap(encode(listOf(exact))).getInt(23))
        assertEquals(listOf(exact), VerificationArchive.decode(encode(listOf(exact)), now).records)
        val over = exact.copy(target = VerificationTarget.AppGroup((exact.target as VerificationTarget.AppGroup).packageNames.map {
            if (it.startsWith("x")) it + "x" else it
        }))
        expectEncodeFailure(VerificationStorageFailure.LIMIT) { encode(listOf(over)) }
    }

    @Test fun `nullable overall closed records and every source status survive wire format`() {
        val baseline = record.baseline.copy(reportedAt = null, context = VerificationSampleContext(
            VerificationDeviceContext(null, null, null, null, VerificationOwnership.UNKNOWN), VerificationScenario.OTHER,
            "", VerificationConfirmation.UNKNOWN), sources = DiagnosticSourceStatus.entries.associateBy { it.name },
            metrics = listOf(record.baseline.metrics.single().copy(key = VerificationMetricKey(VerificationMetricKind.OVERALL_POWER, emptyList()),
                value = null, window = MetricWindow("batterystats", VerificationMetricScope.UNKNOWN, null, null, null, null,
                    VerificationConfirmation.UNKNOWN), status = EvidenceFieldStatus.NOT_PRESENT)))
        val sample = record.copy(state = VerificationState.CLOSED, target = VerificationTarget.Overall,
            baseline = baseline, change = null, retest = null, closeReason = VerificationCloseReason.ABANDONED)
        assertEquals(listOf(sample), VerificationArchive.decode(encode(listOf(sample)), now).records)
    }

    @Test fun `invalid package field or too many saved application groups is rejected`() {
        expectEncodeFailure(VerificationStorageFailure.DAMAGED) { encode(listOf(record.copy(target = VerificationTarget.AppGroup(listOf(""))))) }
        val summaries = record.baseline.copy(metrics = (1..21).map { record.baseline.metrics.single().copy(
            key = VerificationMetricKey(VerificationMetricKind.APP_TOTAL_POWER, listOf("com.example.$it"))) })
        expectEncodeFailure(VerificationStorageFailure.LIMIT) { encode(listOf(record.copy(baseline = summaries))) }
    }

    @Test fun `target group consumes one of twenty saved groups`() {
        val twentyOthers = record.baseline.copy(metrics = (1..20).map { record.baseline.metrics.single().copy(
            key = VerificationMetricKey(VerificationMetricKind.APP_TOTAL_POWER, listOf("com.other.$it"))) })
        expectEncodeFailure(VerificationStorageFailure.LIMIT) { encode(listOf(record.copy(baseline = twentyOthers))) }
        val nineteenOthers = twentyOthers.copy(metrics = twentyOthers.metrics.take(19))
        assertEquals(1, VerificationArchive.decode(encode(listOf(record.copy(baseline = nineteenOthers))), now).records.size)
    }

    private fun expectEncodeFailure(want: VerificationStorageFailure, block: () -> Unit) {
        try { block(); fail("accepted invalid record") } catch (e: VerificationArchiveException) { assertEquals(want, e.failure) }
    }
    private fun indexOf(bytes: ByteArray, needle: ByteArray, start: Int = 0): Int =
        (start..bytes.size - needle.size).first { i -> needle.indices.all { bytes[i + it] == needle[it] } }
}
