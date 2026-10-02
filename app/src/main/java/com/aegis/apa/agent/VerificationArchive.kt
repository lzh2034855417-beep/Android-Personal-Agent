package com.aegis.apa.agent

import com.aegis.apa.model.*
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.nio.ByteBuffer
import java.nio.CharBuffer
import java.nio.charset.CodingErrorAction
import java.time.Duration
import java.time.Instant

data class VerificationArchiveData(val scopeId: String, val records: List<VerificationRecord>, val expiredIds: List<String>)
class VerificationArchiveException(val failure: VerificationStorageFailure) : IllegalArgumentException(failure.name)

object VerificationArchive {
    const val MAX_BYTES = 1024 * 1024
    const val MAX_RECORD_BYTES = 48 * 1024
    private const val MAX_FIELD_BYTES = 4096
    private const val MAX_RECORDS = 20
    private val retention = Duration.ofDays(30)

    fun encode(scopeId: String, records: List<VerificationRecord>): ByteArray = guarded {
        checkData(scopeId.isNotBlank())
        limit(records.size <= MAX_RECORDS)
        checkData(records.map { it.id }.distinct().size == records.size)
        val bytes = BoundedBytes(MAX_BYTES)
        DataOutputStream(bytes).use { out ->
            out.writeInt(1)
            out.text(scopeId)
            out.writeInt(records.size)
            records.forEach { record ->
                validate(record, scopeId)
                val recordBytes = BoundedBytes(MAX_RECORD_BYTES).also { buffer ->
                    DataOutputStream(buffer).use { it.record(record) }
                }.toByteArray()
                limit(recordBytes.size <= MAX_RECORD_BYTES)
                limit(bytes.size().toLong() + 4 + recordBytes.size <= MAX_BYTES)
                out.writeInt(recordBytes.size)
                out.write(recordBytes)
            }
        }
        limit(bytes.size() <= MAX_BYTES)
        bytes.toByteArray()
    }

    fun decode(bytes: ByteArray, now: Instant): VerificationArchiveData = guarded {
        limit(bytes.size <= MAX_BYTES)
        DataInputStream(ByteArrayInputStream(bytes)).use { input ->
            supported(input.readInt() == 1)
            val scope = input.text().also { checkData(it.isNotBlank()) }
            val count = input.count(MAX_RECORDS)
            val records = List(count) {
                val length = input.readInt()
                checkData(length >= 0)
                limit(length <= MAX_RECORD_BYTES)
                checkData(length <= input.available())
                val raw = ByteArray(length).also(input::readFully)
                DataInputStream(ByteArrayInputStream(raw)).use { recordInput ->
                    recordInput.record().also {
                        checkData(recordInput.available() == 0)
                        validate(it, scope)
                    }
                }
            }
            checkData(input.available() == 0)
            checkData(records.map { it.id }.distinct().size == records.size)
            val (expired, active) = records.partition { Duration.between(it.createdAt, now) >= retention }
            VerificationArchiveData(scope, active, expired.map { it.id })
        }
    }

    private fun validate(record: VerificationRecord, scope: String) {
        supported(record.schemaVersion == 1 && record.ruleVersion == 1)
        checkData(record.id.isNotBlank() && record.scopeId == scope && record.revision >= 0)
        checkData(record.updatedAt >= record.createdAt)
        listOfNotNull(record.baseline, record.retest).forEach { summary ->
            limit(summary.sources.size <= 20 && summary.metrics.size <= 200)
            val targetPackages = (record.target as? VerificationTarget.AppGroup)?.packageNames
            val groups = summary.metrics.map { it.key.packageNames } + listOfNotNull(targetPackages)
            limit(groups.filter { it.isNotEmpty() }.distinct().size <= 20)
        }
    }

    private fun DataOutputStream.record(r: VerificationRecord) {
        text(r.id); writeInt(r.schemaVersion); writeInt(r.ruleVersion); text(r.scopeId)
        instant(r.createdAt); instant(r.updatedAt); writeLong(r.revision); text(r.title, 80)
        text(r.state.wireId)
        when (val target = r.target) {
            VerificationTarget.Overall -> text("overall")
            is VerificationTarget.AppGroup -> { text("app_group"); packages(target.packageNames) }
        }
        summary(r.baseline)
        writeBoolean(r.change != null)
        r.change?.let {
            text(it.description, 500); text(it.rollback, 500); instant(it.confirmedAt)
            text(it.similarScenario.wireId); text(it.noOtherChanges.wireId); text(it.sideEffects, 500)
        }
        writeBoolean(r.retest != null); r.retest?.let { summary(it) }
        nullableText(r.closeReason?.wireId)
    }

    private fun DataInputStream.record(): VerificationRecord {
        val id = text(); val schema = readInt(); val rules = readInt(); val scope = text()
        supported(schema == 1 && rules == 1)
        val created = instant(); val updated = instant(); val revision = readLong(); val title = text(80)
        val state = wire(VerificationState.entries) { it.wireId }
        val target = when (text()) {
            "overall" -> VerificationTarget.Overall
            "app_group" -> VerificationTarget.AppGroup(packages())
            else -> throw VerificationArchiveException(VerificationStorageFailure.UNSUPPORTED)
        }
        val baseline = summary()
        val change = if (flag()) VerificationChange(text(500), text(500), instant(),
            wire(VerificationConfirmation.entries) { it.wireId },
            wire(VerificationConfirmation.entries) { it.wireId }, text(500)) else null
        val retest = if (flag()) summary() else null
        val reason = nullableText()?.let { idValue -> findWire(idValue, VerificationCloseReason.entries) { it.wireId } }
        return VerificationRecord(id, schema, rules, scope, created, updated, revision, title, state, target, baseline, change, retest, reason)
    }

    private fun DataOutputStream.summary(s: DiagnosticVerificationSummary) {
        text(inputId(s.inputSource)); instant(s.generatedAt); nullableInstant(s.reportedAt)
        val device = s.context.device
        nullableText(device.manufacturer); nullableText(device.modelCode)
        writeBoolean(device.apiLevel != null); device.apiLevel?.let(::writeInt)
        nullableText(device.buildVersion); text(device.ownership.wireId)
        text(s.context.scenario.wireId); text(s.context.scenarioNote, 500); text(s.context.noCharging.wireId)
        writeInt(s.sources.size)
        s.sources.toSortedMap().forEach { (source, status) -> text(source); text(sourceId(status)) }
        writeInt(s.metrics.size)
        s.metrics.forEach { metric ->
            text(metric.key.kind.wireId); packages(metric.key.packageNames)
            writeBoolean(metric.value != null); metric.value?.let(::writeDouble)
            text(metric.unit.wireId); text(metric.estimate.wireId)
            val w = metric.window
            text(w.source); text(w.scope.wireId); nullableInstant(w.startedAt); nullableInstant(w.endedAt)
            writeBoolean(w.batteryDurationMillis != null); w.batteryDurationMillis?.let(::writeLong)
            nullableText(w.resetId); text(w.charging.wireId); text(fieldId(metric.status))
        }
        text(s.fingerprint)
    }

    private fun DataInputStream.summary(): DiagnosticVerificationSummary {
        val source = wire(DiagnosticInputSource.entries, ::inputId)
        val generated = instant(); val reported = nullableInstant()
        val manufacturer = nullableText(); val model = nullableText(); val api = if (flag()) readInt() else null
        val build = nullableText(); val ownership = wire(VerificationOwnership.entries) { it.wireId }
        val context = VerificationSampleContext(VerificationDeviceContext(manufacturer, model, api, build, ownership),
            wire(VerificationScenario.entries) { it.wireId }, text(500), wire(VerificationConfirmation.entries) { it.wireId })
        val sources = linkedMapOf<String, DiagnosticSourceStatus>()
        repeat(count(20)) {
            val name = text()
            checkData(!sources.containsKey(name))
            sources[name] = wire(DiagnosticSourceStatus.entries, ::sourceId)
        }
        val metrics = List(count(200)) {
            val kind = wire(VerificationMetricKind.entries) { it.wireId }
            val packages = packages()
            checkData(kind != VerificationMetricKind.OVERALL_POWER || packages.isEmpty())
            val value = if (flag()) readDouble() else null
            val unit = wire(VerificationUnit.entries) { it.wireId }
            val estimate = wire(VerificationEstimate.entries) { it.wireId }
            val windowSource = text(); val scope = wire(VerificationMetricScope.entries) { it.wireId }
            val start = nullableInstant(); val end = nullableInstant(); val duration = if (flag()) readLong() else null
            val reset = nullableText(); val charging = wire(VerificationConfirmation.entries) { it.wireId }
            VerificationMetric(VerificationMetricKey(kind, packages), value, unit, estimate,
                MetricWindow(windowSource, scope, start, end, duration, reset, charging), wire(EvidenceFieldStatus.entries, ::fieldId))
        }
        return DiagnosticVerificationSummary(source, generated, reported, context, sources, metrics, text())
    }

    private fun DataOutputStream.packages(values: List<String>) {
        checkData(values.all(String::isNotBlank))
        limit(values.size <= 100)
        writeInt(values.size); values.forEach { text(it) }
    }
    private fun DataInputStream.packages(): List<String> = List(count(100)) { text() }.also {
        checkData(it == it.distinct().sorted() && it.all(String::isNotBlank))
    }
    private fun DataOutputStream.instant(value: Instant) { writeLong(value.epochSecond); writeInt(value.nano) }
    private fun DataInputStream.instant(): Instant {
        val seconds = readLong(); val nanos = readInt()
        checkData(nanos in 0..999_999_999)
        return Instant.ofEpochSecond(seconds, nanos.toLong())
    }
    private fun DataOutputStream.nullableInstant(value: Instant?) { writeBoolean(value != null); value?.let { instant(it) } }
    private fun DataInputStream.nullableInstant(): Instant? = if (flag()) instant() else null
    private fun DataOutputStream.nullableText(value: String?) { writeBoolean(value != null); value?.let { text(it) } }
    private fun DataInputStream.nullableText(): String? = if (flag()) text() else null
    private fun DataOutputStream.text(value: String, chars: Int = Int.MAX_VALUE) {
        limit(value.length <= chars && value.length <= MAX_FIELD_BYTES)
        val encoded = Charsets.UTF_8.newEncoder().onMalformedInput(CodingErrorAction.REPORT)
            .onUnmappableCharacter(CodingErrorAction.REPORT).encode(CharBuffer.wrap(value))
        limit(encoded.remaining() <= MAX_FIELD_BYTES)
        val bytes = ByteArray(encoded.remaining()).also(encoded::get)
        writeInt(bytes.size); write(bytes)
    }
    private fun DataInputStream.text(chars: Int = Int.MAX_VALUE): String {
        val length = readInt()
        checkData(length >= 0); limit(length <= MAX_FIELD_BYTES); checkData(length <= available())
        val bytes = ByteArray(length).also(::readFully)
        return Charsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT)
            .onUnmappableCharacter(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(bytes)).toString().also {
                limit(it.length <= chars)
                if (chars != Int.MAX_VALUE) checkData(it.none(Char::isISOControl))
            }
    }
    private fun DataInputStream.flag(): Boolean = when (readUnsignedByte()) {
        0 -> false; 1 -> true; else -> throw VerificationArchiveException(VerificationStorageFailure.DAMAGED)
    }
    private fun DataInputStream.count(max: Int): Int = readInt().also { checkData(it >= 0); limit(it <= max) }
    private fun <T> DataInputStream.wire(values: List<T>, id: (T) -> String): T = findWire(text(), values, id)
    private fun <T> findWire(value: String, values: List<T>, id: (T) -> String): T = values.firstOrNull { id(it) == value }
        ?: throw VerificationArchiveException(VerificationStorageFailure.UNSUPPORTED)
    private fun inputId(value: DiagnosticInputSource) = when (value) {
        DiagnosticInputSource.BUGREPORT -> "bugreport"; DiagnosticInputSource.ROOT -> "root"
    }
    private fun sourceId(value: DiagnosticSourceStatus) = when (value) {
        DiagnosticSourceStatus.AVAILABLE -> "available"
        DiagnosticSourceStatus.UNSUPPORTED -> "unsupported"
        DiagnosticSourceStatus.PERMISSION_DENIED -> "permission_denied"
        DiagnosticSourceStatus.TIMED_OUT -> "timed_out"
        DiagnosticSourceStatus.TRUNCATED -> "truncated"
        DiagnosticSourceStatus.PARSE_FAILED -> "parse_failed"
    }
    private fun fieldId(value: EvidenceFieldStatus) = when (value) {
        EvidenceFieldStatus.PARSED -> "parsed"; EvidenceFieldStatus.NOT_PRESENT -> "not_present"
        EvidenceFieldStatus.NOT_PARSED -> "not_parsed"; EvidenceFieldStatus.SOURCE_UNAVAILABLE -> "source_unavailable"
        EvidenceFieldStatus.TRUNCATED -> "truncated"
    }
    private fun checkData(condition: Boolean) { if (!condition) throw VerificationArchiveException(VerificationStorageFailure.DAMAGED) }
    private fun limit(condition: Boolean) { if (!condition) throw VerificationArchiveException(VerificationStorageFailure.LIMIT) }
    private fun supported(condition: Boolean) { if (!condition) throw VerificationArchiveException(VerificationStorageFailure.UNSUPPORTED) }
    private class BoundedBytes(private val maximum: Int) : ByteArrayOutputStream() {
        override fun write(value: Int) { limit(count < maximum); super.write(value) }
        override fun write(bytes: ByteArray, offset: Int, length: Int) {
            limit(length.toLong() + count <= maximum)
            super.write(bytes, offset, length)
        }
    }
    private inline fun <T> guarded(block: () -> T): T = try { block() }
        catch (e: VerificationArchiveException) { throw e }
        catch (_: Exception) { throw VerificationArchiveException(VerificationStorageFailure.DAMAGED) }
}
