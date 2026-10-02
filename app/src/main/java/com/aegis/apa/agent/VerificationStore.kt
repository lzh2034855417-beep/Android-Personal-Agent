package com.aegis.apa.agent

import com.aegis.apa.model.VerificationRecord
import java.io.File
import java.io.ByteArrayOutputStream
import java.io.FileOutputStream
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.time.Instant
import java.time.Duration
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong
import javax.crypto.Cipher
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

enum class VerificationStorageFailure { DAMAGED, UNSUPPORTED, KEY_UNAVAILABLE, LIMIT, WRITE_FAILED }
data class VerificationLoadResult(val records: List<VerificationRecord>, val scopeId: String?,
    val failure: VerificationStorageFailure? = null, val expiredIds: List<String> = emptyList())
/** Every store for the same path shares its in-process mutation lock and deletion barriers. */
class VerificationStore(private val file: File, private val key: () -> SecretKey, private val clock: () -> Instant = Instant::now) {
    private class Coordinator {
        val generation = AtomicLong()
        val deletedIds = mutableSetOf<String>()
    }
    private val coordinator = coordinators.computeIfAbsent(file.canonicalPath) { Coordinator() }

    fun load(): VerificationLoadResult = synchronized(coordinator) { loadLatest() }

    fun upsert(record: VerificationRecord): VerificationLoadResult {
        val submittedGeneration = coordinator.generation.get()
        return synchronized(coordinator) {
            val latest = loadLatest()
            if (latest.failure != null) return@synchronized latest
            val existing = latest.records.firstOrNull { it.id == record.id }
            if (submittedGeneration != coordinator.generation.get() || record.id in coordinator.deletedIds ||
                record.scopeId != latest.scopeId || record.revision < 0 ||
                record.createdAt > clock() || Duration.between(record.createdAt, clock()) >= Duration.ofDays(30) ||
                (existing != null && (record.revision <= existing.revision || record.createdAt != existing.createdAt))) {
                return@synchronized latest.copy(failure = VerificationStorageFailure.WRITE_FAILED)
            }
            val records = latest.records.filterNot { it.id == record.id } + record
            val saved = persist(requireNotNull(latest.scopeId), records)
            if (saved != null) latest.copy(failure = saved) else latest.copy(records = records)
        }
    }

    fun delete(id: String): VerificationLoadResult = synchronized(coordinator) {
        val latest = loadLatest()
        if (latest.failure != null) return@synchronized latest
        val remaining = latest.records.filterNot { it.id == id }
        val failure = persist(requireNotNull(latest.scopeId), remaining)
        if (failure != null) latest.copy(failure = failure) else {
            coordinator.deletedIds += id
            latest.copy(records = remaining)
        }
    }

    /** Explicit recovery authorization: a healthy archive keeps its existing local scope. */
    fun clear(): VerificationLoadResult = synchronized(coordinator) {
        val latest = readLatest()
        if (latest.failure != null && latest.failure != VerificationStorageFailure.DAMAGED &&
            latest.failure != VerificationStorageFailure.UNSUPPORTED) return@synchronized latest
        val scope = latest.scopeId ?: UUID.randomUUID().toString()
        val failure = persist(scope, emptyList())
        if (failure != null) latest.copy(failure = failure) else {
            coordinator.deletedIds += latest.records.map { it.id } + latest.expiredIds
            coordinator.generation.incrementAndGet()
            VerificationLoadResult(emptyList(), scope)
        }
    }

    private fun loadLatest(): VerificationLoadResult {
        val latest = readLatest()
        if (latest.failure != null) return latest
        if (!file.exists() || latest.expiredIds.isNotEmpty()) {
            val scope = latest.scopeId ?: UUID.randomUUID().toString()
            val failure = persist(scope, latest.records)
            if (failure != null) return latest.copy(scopeId = scope, failure = failure)
            coordinator.deletedIds += latest.expiredIds
            return latest.copy(scopeId = scope)
        }
        return latest
    }

    private fun readLatest(): VerificationLoadResult {
        if (!file.exists()) return VerificationLoadResult(emptyList(), null)
        val encrypted = try {
            if (file.length() > MAX_ENCRYPTED_BYTES) return failed(VerificationStorageFailure.LIMIT)
            if (file.length() < 28) return failed(VerificationStorageFailure.DAMAGED)
            file.inputStream().use { input ->
                val bytes = ByteArrayOutputStream()
                val buffer = ByteArray(8192)
                while (bytes.size() <= MAX_ENCRYPTED_BYTES) {
                    val count = input.read(buffer, 0, minOf(buffer.size, MAX_ENCRYPTED_BYTES + 1 - bytes.size()))
                    if (count < 0) break
                    bytes.write(buffer, 0, count)
                }
                if (bytes.size() > MAX_ENCRYPTED_BYTES) return failed(VerificationStorageFailure.LIMIT)
                bytes.toByteArray()
            }
        } catch (_: Exception) { return failed(VerificationStorageFailure.DAMAGED) }
        val secretKey = try { key() } catch (_: Exception) { return failed(VerificationStorageFailure.KEY_UNAVAILABLE) }
        return try {
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.DECRYPT_MODE, secretKey, GCMParameterSpec(128, encrypted.copyOfRange(0, 12)))
            val data = VerificationArchive.decode(cipher.doFinal(encrypted.copyOfRange(12, encrypted.size)), clock())
            VerificationLoadResult(data.records, data.scopeId, expiredIds = data.expiredIds)
        } catch (e: VerificationArchiveException) { failed(e.failure) }
        catch (_: Exception) { failed(VerificationStorageFailure.DAMAGED) }
    }

    private fun persist(scopeId: String, records: List<VerificationRecord>): VerificationStorageFailure? {
        val plain = try { VerificationArchive.encode(scopeId, records) }
        catch (e: VerificationArchiveException) {
            return if (e.failure == VerificationStorageFailure.LIMIT) e.failure else VerificationStorageFailure.WRITE_FAILED
        }
        val secretKey = try { key() } catch (_: Exception) { return VerificationStorageFailure.KEY_UNAVAILABLE }
        val pending = File(file.absoluteFile.parentFile, "${file.name}.pending")
        var openedPending = false
        return try {
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.ENCRYPT_MODE, secretKey)
            check(cipher.iv.size == 12)
            val encrypted = cipher.iv + cipher.doFinal(plain)
            check(encrypted.size <= MAX_ENCRYPTED_BYTES)
            val parent = requireNotNull(file.absoluteFile.parentFile)
            check(parent.isDirectory || parent.mkdirs())
            FileOutputStream(pending).use { output ->
                openedPending = true
                output.write(encrypted)
                output.fd.sync()
            }
            Files.move(pending.toPath(), file.toPath(), StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
            null
        } catch (_: Exception) { VerificationStorageFailure.WRITE_FAILED }
        finally { if (openedPending) pending.delete() }
    }

    private fun failed(failure: VerificationStorageFailure) = VerificationLoadResult(emptyList(), null, failure)
    companion object {
        private const val MAX_ENCRYPTED_BYTES = VerificationArchive.MAX_BYTES + 28
        private val coordinators = ConcurrentHashMap<String, Coordinator>()
    }
}
