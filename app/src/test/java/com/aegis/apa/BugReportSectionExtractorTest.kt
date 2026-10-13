package com.aegis.apa

import com.aegis.apa.tool.BugReportReadLimits
import com.aegis.apa.tool.BugReportReadResult
import com.aegis.apa.tool.BugReportRejectReason
import com.aegis.apa.tool.BugReportSectionExtractor
import com.aegis.apa.model.DiagnosticSourceStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.io.FilterInputStream
import java.io.InputStream
import java.util.Random
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class BugReportSectionExtractorTest {
    @Test
    fun nestedXiaomiReportIgnoresBinaryWifiServiceOutsideDiagnosticWhitelist() {
        val wifi = "DUMP OF SERVICE android.hardware.wifi.IWifi/default:\n" +
            "\u0000".repeat(20_000) + "\n"
        val inner = zipOf("bugreport-dada.txt" to (reportText + "\n" + wifi + reportText))
        val result = BugReportSectionExtractor.extract(zipBytesOf(
            "bugreport-Xiaomi 15.zip" to inner).inputStream(), "bugreport.zip")
        assertTrue("Unselected Wi-Fi binary data must not reject diagnostics: $result",
            result is BugReportReadResult.Success)
        assertTrue((result as BugReportReadResult.Success).sections.first().output.contains("245.5"))
    }

    @Test
    fun nulContaminatedDiagnosticValuesCannotBeJoinedIntoValidNumbers() {
        val text = "DUMP OF SERVICE batterystats:\n" +
            "UID u0a123: 24\u00005.5 mAh\n" +
            "UID u0a124: 10.0 mAh\n"
        val result = BugReportSectionExtractor.extract(text.byteInputStream(), "bugreport.txt")
            as BugReportReadResult.Success
        val battery = result.sections.single()
        assertTrue(!battery.output.contains("245.5"))
        assertTrue(battery.output.contains("10.0"))
        assertEquals(DiagnosticSourceStatus.TRUNCATED, battery.status)
    }

    @Test
    fun largeOemReportKeepsStatisticsAfterBatteryHistory() {
        val history = "  +0ms battery history event\n".repeat(100)
        val summary = "Statistics since last charge:\n" +
            "Time on battery: 16h 58m 57s 458ms (100.0%) realtime\n" +
            "Estimated power use (mAh):\n" +
            "  Capacity: 2556, Computed drain: 1096, actual drain: 1096\n"
        val text = "DUMP OF SERVICE batterystats:\nBattery History [Format: 2]:\n" +
            history + "Per-PID Stats:\n" + summary + "DUMP OF SERVICE alarm:\nAlarm Stats:\n"
        val result = BugReportSectionExtractor.extract(text.byteInputStream(), "bugreport.txt",
            BugReportReadLimits(maxBatteryStatsSectionChars = 400)) as BugReportReadResult.Success
        val battery = result.sections.single { it.source == "batterystats" }
        assertEquals(DiagnosticSourceStatus.AVAILABLE, battery.status)
        assertTrue(battery.output.contains("actual drain: 1096"))
        assertTrue(!battery.output.contains("battery history event"))
    }

    @Test
    fun defaultBoundsAcceptAnOemReportLargerThan192MiBWithoutRetainingItsLogs() {
        val padding = object : InputStream() {
            var remaining = 199L * 1024 * 1024
            override fun read(): Int = if (remaining-- > 0) '\n'.code else -1
            override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
                if (remaining <= 0) return -1
                val count = minOf(remaining, length.toLong()).toInt()
                java.util.Arrays.fill(buffer, offset, offset + count, '\n'.code.toByte())
                remaining -= count
                return count
            }
        }
        val input = java.io.SequenceInputStream(padding, reportText.byteInputStream())
        val result = BugReportSectionExtractor.extract(input, "bugreport.txt")
        assertTrue("Expected bounded large report import, got $result", result is BugReportReadResult.Success)
    }

    @Test
    fun qualcommDecimalBootRecordsKeepTheirOwnTypeNumbering() {
        for ((type, expected) in listOf(5 to "LPDDR3", 6 to "LPDDR4", 8 to "LPDDR5")) {
            val result = BugReportSectionExtractor.extract(zipOf(
                "bugreport-device.txt" to reportText,
                "bootlog.txt" to "B - 624403 - Manufacturer ID = 255, Device Type = $type"
            ).inputStream(), "bugreport.zip") as BugReportReadResult.Success
            val info = parsedHardware(result)
            assertEquals("美光 (Micron)", info?.ramVendor)
            assertEquals(expected, info?.ramType)
        }
    }

    @Test
    fun onePlusDdrAttachmentsUseTheirOwnDecimalAndNamedFields() {
        for ((path, body, expected) in listOf(
            Triple("FS/sys/project_info/ddr_manufacture_info", "255\n", "美光 (Micron)"),
            Triple("FS/sys/project_info/ddr_manufacture_info", "6\n", "海力士 (SK hynix)"),
            Triple("FS/sys/project_info/component_info/ddr", "VER:\t12GB\nMANU:\tSamsung 18nm 1.0\n", "三星 (Samsung)")
        )) {
            val result = BugReportSectionExtractor.extract(zipOf("bugreport-device.txt" to reportText,
                path to body).inputStream(), "bugreport.zip") as BugReportReadResult.Success
            assertEquals(expected, parsedHardware(result)?.ramVendor)
            assertEquals(null, parsedHardware(result)?.ramType)
        }
    }

    @Test
    fun samsungDdrAttachmentUsesDdrvAndDoesNotInferTypeFromDsf() {
        val result = BugReportSectionExtractor.extract(zipOf("bugreport-device.txt" to reportText,
            "FS/sys/class/sec/sec_hw_param/ddr_info" to "\"DDRV\":\"HYN\",\"DSF\":\"5.0\""
        ).inputStream(), "bugreport.zip") as BugReportReadResult.Success
        assertEquals("海力士 (SK hynix)", parsedHardware(result)?.ramVendor)
        assertEquals(null, parsedHardware(result)?.ramType)
    }

    @Test
    fun unrelatedComponentsAndInvalidRamAttachmentsAreIgnored() {
        for ((path, body) in listOf(
            "FS/sys/project_info/component_info/lcd" to "MANU: Samsung",
            "FS/sys/project_info/ddr_manufacture_info" to "256",
            "FS/sys/project_info/ddr_manufacture_info" to "6\u0000",
            "FS/sys/class/sec/sec_hw_param/ddr_info" to "\"DDRV\":\"NA\"",
            "bootlog.txt" to "Manufacturer ID = 255, Device Type = 8",
            "bootlog.txt" to "B - 123 - Manufacturer ID = 256, Device Type = 8"
        )) {
            val result = BugReportSectionExtractor.extract(zipOf("bugreport-device.txt" to reportText,
                path to body).inputStream(), "bugreport.zip") as BugReportReadResult.Success
            assertTrue(result.sections.none { it.source == "hardware" })
        }
    }

    @Test
    fun ramAttachmentsRespectTinyReadBoundsAndCancellation() {
        val data = zipOf("FS/sys/project_info/ddr_manufacture_info" to "1".repeat(4097),
            "bugreport-device.txt" to reportText)
        val tooLarge = BugReportSectionExtractor.extract(data.inputStream(), "bugreport.zip")
        assertEquals(BugReportReadResult.Rejected(BugReportRejectReason.ENTRY_TOO_LARGE), tooLarge)
        assertEquals(BugReportReadResult.Cancelled, BugReportSectionExtractor.extract(
            data.inputStream(), "bugreport.zip", isCancelled = { true }))
    }

    private fun parsedHardware(result: BugReportReadResult.Success): com.aegis.apa.tool.HardwareSupplyInfo? =
        com.aegis.apa.tool.DeviceProfileParser.parse(result.sections.single { it.source == "hardware" }.output,
            com.aegis.apa.tool.DeviceProfileAccess.STANDARD).hardwareSupplyInfo

    @Test
    fun k60BootRecordWithoutDdrPrefixReadsMicronModeRegisterId() {
        val record = "135\tB -   1247450 - Manufacturer ID = 0xff, Device Type = 0x8"
        for (name in listOf("bootlog_copy.txt", "dumpstate_board.txt", "bugreport-device.txt")) {
            val entries = if (name.startsWith("bugreport")) arrayOf(name to "$record\n$reportText")
                else arrayOf("bugreport-device.txt" to reportText, name to record)
            val result = BugReportSectionExtractor.extract(zipOf(*entries).inputStream(), "bugreport.zip")
                as BugReportReadResult.Success
            val raw = result.sections.single { it.source == "hardware" }.output
            val info = com.aegis.apa.tool.DeviceProfileParser.parse(raw,
                com.aegis.apa.tool.DeviceProfileAccess.STANDARD).hardwareSupplyInfo
            assertEquals("美光 (Micron)", info?.ramVendor)
            assertEquals("LPDDR5", info?.ramType)
            assertEquals(com.aegis.apa.tool.HardwareSupplySource.ANDROID_BUGREPORT, info?.source)
        }
    }

    @Test
    fun ramFieldsAreReadFromMainReportAndBoardAttachment() {
        for ((name, body) in listOf(
            "bugreport-device.txt" to "DDR Manufacturer ID: 0x01CE\nDDR Device Type: 0x9\n$reportText",
            "dumpstate_board.txt" to "DDR Manufacturer ID: 0x01CE\nDDR Device Type: 0x9"
        )) {
            val entries = if (name.startsWith("bugreport")) arrayOf(name to body)
                else arrayOf("bugreport-device.txt" to reportText, name to body)
            val result = BugReportSectionExtractor.extract(zipOf(*entries).inputStream(), "bugreport.zip")
                as BugReportReadResult.Success
            val hardware = result.sections.single { it.source == "hardware" }.output
            val parsed = com.aegis.apa.tool.DeviceProfileParser.parse(hardware,
                com.aegis.apa.tool.DeviceProfileAccess.STANDARD).hardwareSupplyInfo
            assertEquals("三星 (Samsung)", parsed?.ramVendor)
            assertEquals("LPDDR5X", parsed?.ramType)
        }
    }

    @Test
    fun standardGetpropHeadingLetsK60HardwareMatchTheCurrentDevice() {
        val header = """
            ------ SYSTEM PROPERTIES (getprop) ------
            [ro.product.manufacturer]: [Xiaomi]
            [ro.product.model]: [22127RK46C]
            [ro.build.version.sdk]: [35]
            [ro.build.version.incremental]: [OS3.0.4.0.VMKCNXM]
            [ro.serialno]: [must-not-be-retained]
            ------ 0.048s was the duration of 'SYSTEM PROPERTIES' ------
            [ro.product.model]: [Wrong model outside properties]
        """.trimIndent()
        val nested = zipOf("bugreport-socrates.txt" to "$header\n$reportText",
            "FS/vendor/bootlog_copy.txt" to "UFS INQUIRY ID : SAMSUNG\nUFS Spec Version : 4.0")
        val result = BugReportSectionExtractor.extract(
            zipBytesOf("bugreport-socrates.zip" to nested).inputStream(), "bugreport.zip"
        ) as BugReportReadResult.Success
        assertEquals("22127RK46C", result.verificationHeaderFields["ro.product.model"])
        assertEquals(4, result.verificationHeaderFields.size)
        val cache = com.aegis.apa.tool.HardwareSupplyCache(java.io.File("unused-cache"), "Xiaomi", "22127RK46C")
        assertTrue(cache.matchesReport(result.verificationHeaderFields))
        assertTrue(!com.aegis.apa.tool.HardwareSupplyCache(java.io.File("unused-cache"), "Xiaomi", "OTHER")
            .matchesReport(result.verificationHeaderFields))
        val hardware = result.sections.single { it.source == "hardware" }
        assertEquals("Samsung", com.aegis.apa.tool.DeviceProfileParser.parse(
            hardware.output, com.aegis.apa.tool.DeviceProfileAccess.STANDARD).hardwareSupplyInfo?.storageVendor)
    }

    @Test
    fun k60CopiedBootlogPreservesNamedUfsManufacturer() {
        val result = BugReportSectionExtractor.extract(
            zipOf("bugreport-device.txt" to reportText,
                "FS/vendor/bootlog_copy.txt" to "UFS INQUIRY ID : SAMSUNG\nUFS Spec Version : 4.0").inputStream(),
            "bugreport.zip"
        ) as BugReportReadResult.Success
        val hardware = result.sections.single { it.source == "hardware" }.output
        val parsed = com.aegis.apa.tool.DeviceProfileParser.parse(
            hardware, com.aegis.apa.tool.DeviceProfileAccess.STANDARD).hardwareSupplyInfo
        assertEquals("Samsung", parsed?.storageVendor)
        assertEquals("UFS 4.0", parsed?.storageSpec)
    }

    @Test
    fun partialBootlogDoesNotEraseRamFromEarlierBootlogInSameArchive() {
        val result = BugReportSectionExtractor.extract(
            zipOf(
                "bugreport-device.txt" to reportText,
                "FS/vendor/bootlog.txt" to "DDR Manufacturer ID = 0x01CE\nDDR Device Type = 0x9",
                "FS/data/vendor/bootlog.txt" to "UFS INQUIRY ID = 0x06\nUFS Spec Version = 4.10"
            ).inputStream(), "bugreport.zip"
        ) as BugReportReadResult.Success
        val hardware = result.sections.single { it.source == "hardware" }.output
        assertTrue(hardware.contains("DDR Manufacturer ID = 0x01CE"))
        assertTrue(hardware.contains("DDR Device Type = 0x9"))
        assertTrue(hardware.contains("UFS INQUIRY ID = 0x06"))
        val parsed = com.aegis.apa.tool.DeviceProfileParser.parse(
            hardware, com.aegis.apa.tool.DeviceProfileAccess.STANDARD
        ).hardwareSupplyInfo
        assertEquals("三星 (Samsung)", parsed?.ramVendor)
        assertEquals("LPDDR5X", parsed?.ramType)
        assertEquals("SK hynix", parsed?.storageVendor)
    }

    @Test
    fun partialOuterBootlogDoesNotEraseRamFromSelectedNestedReport() {
        val nested = zipOf(
            "bugreport-device.txt" to reportText,
            "FS/vendor/bootlog.txt" to "DDR Manufacturer ID = 0x01AD\nDDR Device Type = 0x9"
        )
        val result = BugReportSectionExtractor.extract(
            zipBytesOf(
                "bugreport-Xiaomi.zip" to nested,
                "FS/data/vendor/bootlog.txt" to "UFS INQUIRY ID = 0x06".toByteArray()
            ).inputStream(), "bugreport.zip"
        ) as BugReportReadResult.Success
        val hardware = result.sections.single { it.source == "hardware" }.output
        assertTrue(hardware.contains("DDR Manufacturer ID = 0x01AD"))
        assertTrue(hardware.contains("UFS INQUIRY ID = 0x06"))
    }

    @Test
    fun nestedBootlogComplementsEarlierOuterFieldsAndKeepsFirstConflictingValue() {
        val nested = zipOf(
            "bugreport-device.txt" to reportText,
            "FS/vendor/bootlog.txt" to "DDR Manufacturer ID = 0x01AD\nDDR Device Type = 0x9"
        )
        val result = BugReportSectionExtractor.extract(
            zipBytesOf(
                "FS/bootlog.txt" to "DDR Manufacturer ID = 0x01CE\nUFS INQUIRY ID = 0x06".toByteArray(),
                "bugreport-Xiaomi.zip" to nested
            ).inputStream(), "bugreport.zip"
        ) as BugReportReadResult.Success
        val hardware = result.sections.single { it.source == "hardware" }.output
        assertTrue(hardware.contains("DDR Manufacturer ID = 0x01CE"))
        assertTrue(hardware.contains("DDR Device Type = 0x9"))
        assertTrue(hardware.contains("UFS INQUIRY ID = 0x06"))
    }

    @Test
    fun hardwareFromOneImportNeverLeaksIntoAnotherImport() {
        BugReportSectionExtractor.extract(
            zipOf("bugreport-device.txt" to reportText,
                "FS/bootlog.txt" to "DDR Manufacturer ID = 0x01CE").inputStream(), "first.zip"
        )
        val second = BugReportSectionExtractor.extract(
            zipOf("bugreport-device.txt" to reportText,
                "FS/bootlog.txt" to "UFS INQUIRY ID = 0x06").inputStream(), "second.zip"
        ) as BugReportReadResult.Success
        assertEquals("UFS INQUIRY ID = 0x06", second.sections.single { it.source == "hardware" }.output)
    }

    @Test
    fun legacyPositionalCancellationStillStopsBeforeReading() {
        val result = BugReportSectionExtractor.extract(reportText.byteInputStream(), "bugreport.txt", BugReportReadLimits(), { true })
        assertEquals(BugReportReadResult.Cancelled, result)
    }

    @Test
    fun progressCountsInputBytesAndDoesNotFloodCallbacks() {
        val bytes = ("irrelevant log\n".repeat(500_000) + reportText).toByteArray()
        val counts = mutableListOf<Long>()
        val result = BugReportSectionExtractor.extract(bytes.inputStream(), "bugreport.txt", onBytesRead = counts::add)
        assertTrue(result is BugReportReadResult.Success)
        assertEquals(bytes.size.toLong(), counts.last())
        assertTrue(counts.zipWithNext().all { (first, next) -> next > first })
        assertTrue(counts.size <= 8)
    }

    @Test
    fun cancellationFromProgressStopsLargeRead() {
        val bytes = ("irrelevant log\n".repeat(500_000) + reportText).toByteArray()
        var cancelled = false
        val result = BugReportSectionExtractor.extract(bytes.inputStream(), "bugreport.txt",
            isCancelled = { cancelled }, onBytesRead = { cancelled = true })
        assertEquals(BugReportReadResult.Cancelled, result)
    }

    @Test
    fun buffersProviderReadsWithoutChangingSelectedSections() {
        val bytes = ("irrelevant log\n".repeat(500_000) + reportText).toByteArray()
        var reads = 0
        val provider = object : FilterInputStream(bytes.inputStream()) {
            override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
                reads++
                return super.read(buffer, offset, length)
            }
        }
        val result = BugReportSectionExtractor.extract(provider, "bugreport.txt")
        assertTrue(result is BugReportReadResult.Success)
        assertTrue("Too many provider reads: $reads", reads < 150)
    }

    @Test
    fun excessiveNulsInsideZipIdentifyTheTextCheckNotTheArchiveType() {
        val result = BugReportSectionExtractor.extract(
            zipOf("bugreport.txt" to (reportText + "\u0000".repeat(1_025))).inputStream(),
            "bugreport.zip"
        ) as BugReportReadResult.Rejected
        assertEquals(BugReportRejectReason.UNSUPPORTED_FORMAT, result.reason)
        assertEquals("signature=zip;text=nul_limit", result.diagnosticDetail)
    }

    @Test
    fun excessiveNulsOutsideZipIdentifyTheMissingZipSignature() {
        val result = BugReportSectionExtractor.extract(
            (reportText + "\u0000".repeat(1_025)).byteInputStream(), "bugreport.zip"
        ) as BugReportReadResult.Rejected
        assertEquals(BugReportRejectReason.UNSUPPORTED_FORMAT, result.reason)
        assertEquals("signature=not_zip;text=nul_limit", result.diagnosticDetail)
    }

    @Test
    fun nonZipBinaryWithUnrelatedNameIdentifiesFilenameCheck() {
        val result = BugReportSectionExtractor.extract(
            byteArrayOf(1, 2, 3, 4).inputStream(), "device.zip"
        ) as BugReportReadResult.Rejected
        assertEquals(BugReportRejectReason.UNSUPPORTED_FORMAT, result.reason)
        assertEquals("signature=not_zip;text=name_unsupported", result.diagnosticDetail)
    }

    @Test
    fun keepsOnlyExactMainReportVerificationProperties() {
        // Catches retaining unique/extra identifiers or dropping allowlisted main-report metadata.
        val header = """
            ------ SYSTEM PROPERTIES ------
            [ro.product.manufacturer]: [Maker]
            [ro.product.model]: [Report Model]
            [ro.build.version.sdk]: [35]
            [ro.build.version.incremental]: [release-build]
            [ro.serialno]: [unique]
            [ro.build.fingerprint]: [unique-fingerprint]
            [ro.product.vendor.model]: [Vendor Model]
            ------ OTHER SECTION ------
            [ro.product.model]: [Forged Model]
        """.trimIndent()
        val result = BugReportSectionExtractor.extract((header + "\n" + reportText).byteInputStream(), "bugreport.txt") as BugReportReadResult.Success
        assertEquals(mapOf("ro.product.manufacturer" to "Maker", "ro.product.model" to "Report Model", "ro.build.version.sdk" to "35", "ro.build.version.incremental" to "release-build"), result.verificationHeaderFields)
        assertTrue(result.verificationHeaderFields.entries.sumOf { (key, value) -> (key + value).toByteArray().size } <= 4096)
    }

    @Test
    fun duplicateConflictAndOversizeValueCannotRecoverFromLaterHeader() {
        // Catches first/last-wins conflicts and accepting a later value after an invalid duplicate.
        val header = "------ SYSTEM PROPERTIES ------\n" +
            "[ro.product.manufacturer]: [Maker]\n[ro.product.manufacturer]: [Maker]\n" +
            "[ro.product.model]: [First]\n[ro.product.model]: [Second]\n[ro.product.model]: [First]\n" +
            "[ro.build.version.incremental]: [${"界".repeat(86)}]\n[ro.build.version.incremental]: [short]\n"
        val result = BugReportSectionExtractor.extract((header + reportText).byteInputStream(), "bugreport.txt") as BugReportReadResult.Success
        assertEquals(mapOf("ro.product.manufacturer" to "Maker"), result.verificationHeaderFields)
    }

    @Test
    fun allowedPropertiesAfterLargeUnrelatedBlockStillReachMetadata() {
        // Catches applying the retained 4KiB limit as a scan-prefix limit.
        val header = "------ SYSTEM PROPERTIES ------\n" +
            (1..100).joinToString("\n") { "[unrelated.property.$it]: [${"x".repeat(100)}]" } +
            "\n[ro.product.model]: [Late Model]\n"
        val result = BugReportSectionExtractor.extract((header + reportText).byteInputStream(), "bugreport.txt") as BugReportReadResult.Success
        assertEquals(mapOf("ro.product.model" to "Late Model"), result.verificationHeaderFields)
    }

    @Test
    fun selectedNestedReportProvidesHeadersAndOtherArchiveEntriesDoNot() {
        // Catches attaching identity from hardware logs or a second report.
        val selected = "------ SYSTEM PROPERTIES ------\n[ro.product.model]: [Nested Model]\n" + reportText
        val other = "------ SYSTEM PROPERTIES ------\n[ro.product.model]: [Other Model]\n" + reportText
        val result = BugReportSectionExtractor.extract(zipBytesOf(
            "FS/data/vendor/bootlog.txt" to other.toByteArray(),
            "bugreport-inner.zip" to zipOf("bugreport-inner.txt" to selected, "bugreport-other.txt" to other)
        ).inputStream(), "bugreport.zip") as BugReportReadResult.Success
        assertEquals(mapOf("ro.product.model" to "Nested Model"), result.verificationHeaderFields)
    }

    @Test
    fun truncatedPropertyLineCannotBecomePartialIdentifier() {
        // Catches parsing a bounded/truncated line as a complete property.
        val header = "------ SYSTEM PROPERTIES ------\n[ro.product.model]: [${"x".repeat(70_000)}]\n[ro.product.model]: [later]\n"
        val result = BugReportSectionExtractor.extract((header + reportText).byteInputStream(), "bugreport.txt") as BugReportReadResult.Success
        assertTrue(result.verificationHeaderFields.isEmpty())
    }

    @Test
    fun truncatedSectionBoundaryEndsPropertyProvenance() {
        // Catches attributing later unrelated section properties to the system property block.
        val header = "------ SYSTEM PROPERTIES ------\n------ OTHER ${"x".repeat(70_000)} ------\n[ro.product.model]: [Other Section Model]\n"
        val result = BugReportSectionExtractor.extract((header + reportText).byteInputStream(), "bugreport.txt") as BugReportReadResult.Success
        assertTrue(result.verificationHeaderFields.isEmpty())
    }

    @Test
    fun full256BytePropertyValueIsPreserved() {
        // Catches character-count bounds replacing the UTF-8 byte boundary.
        val value = "界".repeat(85) + "x"
        val header = "------ SYSTEM PROPERTIES ------\n[ro.product.model]: [$value]\n"
        val result = BugReportSectionExtractor.extract((header + reportText).byteInputStream(), "bugreport.txt") as BugReportReadResult.Success
        assertEquals(value, result.verificationHeaderFields["ro.product.model"])
    }

    @Test
    fun cancelledOrOversizeReadCannotProduceMetadataSuccess() {
        // Catches exposing captured identity from a failed/partial import.
        val report = "------ SYSTEM PROPERTIES ------\n[ro.product.model]: [Model]\n" + reportText
        assertEquals(BugReportReadResult.Cancelled, BugReportSectionExtractor.extract(report.byteInputStream(), "bugreport.txt", isCancelled = { true }))
        assertEquals(rejected(BugReportRejectReason.ENTRY_TOO_LARGE), BugReportSectionExtractor.extract(report.byteInputStream(), "bugreport.txt", limits = BugReportReadLimits(maxEntryBytes = 50)))
    }

    private val reportText = """
        ------ DUMPSYS batterystats (dumpsys batterystats --checkin) ------
        Estimated power use (mAh):
          UID u0a123: 245.5
        ------ DUMPSYS alarm (dumpsys alarm) ------
        u0a123: 180 wakeups
    """.trimIndent()

    @Test
    fun defaultLimitsCoverObservedXiaomi17ProMaxEngineeringReport() {
        val limits = BugReportReadLimits()

        assertTrue(limits.maxEntries >= 855)
        assertTrue(limits.maxSkippedBytes >= 269_797_153L)
        assertTrue(limits.maxEntryBytes >= 162_451_080L)
        assertTrue(limits.maxTotalBytes >= 162_451_080L)
    }

    @Test
    fun extractsWhitelistedSectionsFromPlainText() {
        val result = BugReportSectionExtractor.extract(
            input = reportText.byteInputStream(),
            displayName = "bugreport.txt"
        )

        assertTrue(result is BugReportReadResult.Success)
        assertEquals(
            listOf("batterystats", "alarm"),
            (result as BugReportReadResult.Success).sections.map { it.source }
        )
        assertTrue(result.sections.first().output.contains("UID u0a123: 245.5"))
    }

    @Test
    fun sparseNulBytesKeepOtherSectionsButDoNotReconstructContaminatedValues() {
        val reportWithSparseNuls = reportText.replace("245.5", "245\u0000.5")

        val result = BugReportSectionExtractor.extract(
            input = reportWithSparseNuls.byteInputStream(),
            displayName = "bugreport-Xiaomi.txt"
        )

        assertTrue(result is BugReportReadResult.Success)
        result as BugReportReadResult.Success
        assertTrue(!result.sections.first().output.contains("245.5"))
        assertEquals(DiagnosticSourceStatus.TRUNCATED, result.sections.first().status)
        assertTrue(result.sections.single { it.source == "alarm" }.output.contains("180 wakeups"))
    }

    @Test
    fun rejectsReportTextWithExcessiveNulBytes() {
        val binaryLikeReport = reportText + "\u0000".repeat(1_025)

        val result = BugReportSectionExtractor.extract(
            input = binaryLikeReport.byteInputStream(),
            displayName = "bugreport-device.txt"
        )

        assertEquals(rejected(BugReportRejectReason.UNSUPPORTED_FORMAT), result)
    }

    @Test
    fun largeZipReportWithMoreThan1024SparseNulsStillExtractsDiagnostics() {
        // A fixed report-wide count wrongly rejects sparse padding in large OEM reports.
        val padding = ("ordinary vendor log " + "x".repeat(100) + "\u0000\n").repeat(2_048)
        val result = BugReportSectionExtractor.extract(
            zipOf("bugreport-Xiaomi.txt" to (padding + reportText)).inputStream(),
            "bugreport-Xiaomi.zip"
        )
        assertTrue("Sparse NUL padding must not reject a large report: $result", result is BugReportReadResult.Success)
        result as BugReportReadResult.Success
        assertEquals(listOf("batterystats", "alarm"), result.sections.map { it.source })
        assertTrue(result.sections.first().output.contains("UID u0a123: 245.5"))
    }

    @Test
    fun sparseNulPaddingBeforeValidPlainTextDoesNotCauseEarlyRejection() {
        val padding = "\u0000".repeat(2_048) + "\n" + ("ordinary log " + "x".repeat(100) + "\n").repeat(2_048)
        val result = BugReportSectionExtractor.extract(
            (padding + reportText).byteInputStream(), "bugreport.txt"
        )
        assertTrue("Leading padding must be judged against the complete bounded report: $result", result is BugReportReadResult.Success)
        result as BugReportReadResult.Success
        assertTrue(result.sections.first().output.contains("UID u0a123: 245.5"))
    }

    @Test
    fun largeHighNulDensityReportIsStillRejectedEvenWithServiceMarkers() {
        val binaryLikeReport = reportText + ("x".repeat(90) + "\u0000".repeat(10) + "\n").repeat(2_048)
        val result = BugReportSectionExtractor.extract(
            zipOf("bugreport.txt" to binaryLikeReport).inputStream(), "bugreport.zip"
        ) as BugReportReadResult.Rejected
        assertEquals(BugReportRejectReason.UNSUPPORTED_FORMAT, result.reason)
    }

    @Test
    fun extractsWhitelistedSectionsFromPriorityServiceMarkers() {
        val priorityReport = """
            DUMP OF SERVICE CRITICAL power:
            Wake Locks: size=2
            DUMP OF SERVICE HIGH thermalservice:
            Thermal Status: 1
        """.trimIndent()

        val result = BugReportSectionExtractor.extract(
            input = priorityReport.byteInputStream(),
            displayName = "bugreport-priority.txt"
        )

        assertTrue(result is BugReportReadResult.Success)
        assertEquals(
            listOf("power", "thermalservice"),
            (result as BugReportReadResult.Success).sections.map { it.source }
        )
    }

    @Test
    fun readsReportTextInsideZip() {
        val result = BugReportSectionExtractor.extract(
            input = zipOf("bugreport-device.txt" to reportText).inputStream(),
            displayName = "bugreport-device.zip"
        )

        assertTrue(result is BugReportReadResult.Success)
        assertEquals(
            listOf("batterystats", "alarm"),
            (result as BugReportReadResult.Success).sections.map { it.source }
        )
    }

    @Test
    fun extractsOnlySupplierLinesFromBootLogAfterMainReport() {
        val bootLog = """
            user email: private@example.com
            DDR Device Type = 0x9
            private-prefix DDR Manufacturer ID = 0x01CE, serial=must-not-be-retained
            UFS INQUIRY ID = 0x06
            UFS Spec Version = 4.1
        """.trimIndent()
        val result = BugReportSectionExtractor.extract(
            input = zipOf(
                "bugreport-device.txt" to reportText,
                "FS/data/vendor/bootlog.txt" to bootLog
            ).inputStream(),
            displayName = "bugreport-device.zip"
        )

        assertTrue(result is BugReportReadResult.Success)
        val sections = (result as BugReportReadResult.Success).sections.associateBy { it.source }
        val hardware = sections.getValue("hardware").output
        assertTrue(hardware.contains("DDR Manufacturer ID = 0x01CE"))
        assertTrue(hardware.contains("UFS INQUIRY ID = 0x06"))
        assertTrue(!hardware.contains("private@example.com"))
        assertTrue(!hardware.contains("private-prefix"))
        assertTrue(!hardware.contains("must-not-be-retained"))
    }

    @Test
    fun deduplicatesRepeatedHardwareFieldsToBoundOutputSize() {
        val repeatedFields = buildString {
            repeat(10_000) {
                appendLine("DDR Device Type = 0x9")
                appendLine("DDR Manufacturer ID = 0x01CE")
                appendLine("UFS INQUIRY ID = 0x06")
                appendLine("UFS Spec Version = 4.1")
            }
        }
        val result = BugReportSectionExtractor.extract(
            input = zipOf(
                "bugreport-device.txt" to reportText,
                "FS/data/vendor/bootlog.txt" to repeatedFields
            ).inputStream(),
            displayName = "bugreport-device.zip"
        )

        assertTrue(result is BugReportReadResult.Success)
        val hardware = (result as BugReportReadResult.Success)
            .sections
            .single { it.source == "hardware" }
            .output
        assertEquals(4, hardware.lineSequence().count())
        assertEquals(1, hardware.lineSequence().count { it == "DDR Device Type = 0x9" })
        assertTrue(hardware.length < 256)
    }

    @Test
    fun hardwareLogBeforeMainReportDoesNotConsumeDiagnosticBudget() {
        val bootLog = buildString {
            repeat(8) { appendLine("DDR Device Type = 0x9") }
        }
        val result = BugReportSectionExtractor.extract(
            input = zipOf(
                "FS/data/vendor/bootlog.txt" to bootLog,
                "bugreport-device.txt" to reportText
            ).inputStream(),
            displayName = "bugreport-device.zip",
            limits = BugReportReadLimits(
                maxEntryBytes = 1_024,
                maxTotalBytes = reportText.toByteArray().size.toLong(),
                maxSkippedBytes = 1_024
            )
        )

        assertTrue(result is BugReportReadResult.Success)
        assertEquals(
            listOf("batterystats", "alarm", "hardware"),
            (result as BugReportReadResult.Success).sections.map { it.source }
        )
    }

    @Test
    fun keepsValidReportWhenOptionalTrailingBootLogIsOversized() {
        val result = BugReportSectionExtractor.extract(
            input = zipBytesOf(
                "bugreport-device.txt" to reportText.toByteArray(),
                "FS/data/vendor/bootlog.txt" to ByteArray(513) { 7 }
            ).inputStream(),
            displayName = "bugreport-device.zip",
            limits = BugReportReadLimits(
                maxEntryBytes = 512,
                maxTotalBytes = 1024,
                maxSkippedBytes = 1024
            )
        )

        assertTrue(result is BugReportReadResult.Success)
        assertEquals(
            listOf("batterystats", "alarm"),
            (result as BugReportReadResult.Success).sections.map { it.source }
        )
    }

    @Test
    fun keepsValidReportWhenUnrelatedTrailingDataExceedsDiscoveryBudget() {
        val result = BugReportSectionExtractor.extract(
            input = zipBytesOf(
                "bugreport-device.txt" to reportText.toByteArray(),
                "FS/trailing.bin" to ByteArray(65) { 7 }
            ).inputStream(),
            displayName = "bugreport-device.zip",
            limits = BugReportReadLimits(
                maxEntryBytes = 512,
                maxTotalBytes = 1024,
                maxSkippedBytes = 64
            )
        )

        assertTrue(result is BugReportReadResult.Success)
    }

    @Test
    fun keepsFirstValidReportWhenLaterReportCandidateIsMalformed() {
        val result = BugReportSectionExtractor.extract(
            input = zipBytesOf(
                "bugreport-device.txt" to reportText.toByteArray(),
                "bugreport-malformed.txt" to (reportText + "\u0000".repeat(1_025)).toByteArray()
            ).inputStream(),
            displayName = "bugreport-device.zip",
            limits = BugReportReadLimits(
                maxEntryBytes = 4096,
                maxTotalBytes = 8192,
                maxSkippedBytes = 8192
            )
        )

        assertTrue(result is BugReportReadResult.Success)
        assertEquals(
            listOf("batterystats", "alarm"),
            (result as BugReportReadResult.Success).sections.map { it.source }
        )
    }

    @Test
    fun ignoresOversizedUnrelatedAttachmentBeforeMainReport() {
        val result = BugReportSectionExtractor.extract(
            input = zipBytesOf(
                "FS/data.bin" to ByteArray(512) { 7 },
                "bugreport-device.txt" to reportText.toByteArray()
            ).inputStream(),
            displayName = "bugreport-device.zip",
            limits = BugReportReadLimits(maxEntryBytes = 256)
        )

        assertTrue(result is BugReportReadResult.Success)
        assertEquals(
            listOf("batterystats", "alarm"),
            (result as BugReportReadResult.Success).sections.map { it.source }
        )
    }

    @Test
    fun unrelatedAttachmentDoesNotConsumeMainReportBudget() {
        val result = BugReportSectionExtractor.extract(
            input = zipBytesOf(
                "FS/data.bin" to ByteArray(512) { 7 },
                "bugreport-device.txt" to reportText.toByteArray()
            ).inputStream(),
            displayName = "bugreport-device.zip",
            limits = BugReportReadLimits(
                maxEntryBytes = 256,
                maxTotalBytes = 256,
                maxSkippedBytes = 1024
            )
        )

        assertTrue(result is BugReportReadResult.Success)
    }

    @Test
    fun rejectsWhenDiscardedAttachmentsExceedTheirOwnBudget() {
        val result = BugReportSectionExtractor.extract(
            input = zipBytesOf(
                "FS/data.bin" to ByteArray(513) { 7 },
                "bugreport-device.txt" to reportText.toByteArray()
            ).inputStream(),
            displayName = "bugreport-device.zip",
            limits = BugReportReadLimits(maxSkippedBytes = 512)
        )

        assertEquals(rejected(BugReportRejectReason.TOTAL_TOO_LARGE), result)
    }

    @Test
    fun directoryPayloadCannotBypassDiscardBudget() {
        val result = BugReportSectionExtractor.extract(
            input = zipBytesOf(
                "FS/" to ByteArray(513) { 7 },
                "bugreport-device.txt" to reportText.toByteArray()
            ).inputStream(),
            displayName = "bugreport-device.zip",
            limits = BugReportReadLimits(maxSkippedBytes = 512)
        )

        assertEquals(rejected(BugReportRejectReason.TOTAL_TOO_LARGE), result)
    }

    @Test
    fun cancellationStopsDiscardingDirectoryPayloadPromptly() {
        val payload = ByteArray(1024 * 1024).also { Random(13).nextBytes(it) }
        val archive = zipBytesOf(
            "FS/" to payload,
            "bugreport-device.txt" to reportText.toByteArray()
        )
        val counted = CountingInputStream(archive.inputStream())

        val result = BugReportSectionExtractor.extract(
            input = counted,
            displayName = "bugreport-device.zip",
            isCancelled = { counted.bytesRead > 16 * 1024 }
        )

        assertEquals(BugReportReadResult.Cancelled, result)
        assertTrue(
            "Cancellation should not drain a directory payload",
            counted.bytesRead < archive.size / 2
        )
    }

    @Test
    fun ignoresTextWhoseNameOnlyStartsWithBugreportWord() {
        val misleading = """
            DUMP OF SERVICE power:
            should not be selected
        """.trimIndent()
        val result = BugReportSectionExtractor.extract(
            input = zipOf(
                "bugreportjunk.txt" to misleading,
                "bugreport-device.txt" to reportText
            ).inputStream(),
            displayName = "bugreport-device.zip"
        )

        assertTrue(result is BugReportReadResult.Success)
        assertEquals(
            listOf("batterystats", "alarm"),
            (result as BugReportReadResult.Success).sections.map { it.source }
        )
    }

    @Test
    fun cancellationStopsDiscardingUnrelatedAttachmentPromptly() {
        val attachment = ByteArray(1024 * 1024).also { Random(11).nextBytes(it) }
        val archive = zipBytesOf(
            "FS/random.bin" to attachment,
            "bugreport-device.txt" to reportText.toByteArray()
        )
        val counted = CountingInputStream(archive.inputStream())

        val result = BugReportSectionExtractor.extract(
            input = counted,
            displayName = "bugreport-device.zip",
            isCancelled = { counted.bytesRead > 16 * 1024 }
        )

        assertEquals(BugReportReadResult.Cancelled, result)
        assertTrue(
            "Cancellation should not drain an unrelated compressed attachment",
            counted.bytesRead < archive.size / 2
        )
    }

    @Test
    fun ignoresOversizedSystraceTextAttachmentBeforeMainReport() {
        val result = BugReportSectionExtractor.extract(
            input = zipOf(
                "systrace.txt" to "trace".repeat(128),
                "bugreport-device.txt" to reportText
            ).inputStream(),
            displayName = "bugreport-device.zip",
            limits = BugReportReadLimits(maxEntryBytes = 256)
        )

        assertTrue(result is BugReportReadResult.Success)
        assertEquals(
            listOf("batterystats", "alarm"),
            (result as BugReportReadResult.Success).sections.map { it.source }
        )
    }

    @Test
    fun acceptsMainReportLargerThanLegacyEightMiBLimit() {
        val largeReport = reportText + "\n" + "x".repeat(8 * 1024 * 1024)

        val result = BugReportSectionExtractor.extract(
            input = zipOf("bugreport-large.txt" to largeReport).inputStream(),
            displayName = "bugreport-large.zip"
        )

        assertTrue(result is BugReportReadResult.Success)
        assertEquals(
            DiagnosticSourceStatus.TRUNCATED,
            (result as BugReportReadResult.Success).sections.last().status
        )
    }

    @Test
    fun retainsLateEvidenceFromLargeXiaomiDiagnosticSections() {
        val filler = "x\n".repeat(1_100_000)
        val largeReport = buildString {
            appendLine("DUMP OF SERVICE batterystats:")
            append(filler)
            appendLine("Estimated power use (mAh):")
            appendLine("  UID u0a123: 245.5")
            appendLine("DUMP OF SERVICE packages:")
            append(filler)
            appendLine("  Package [com.example.chat]")
            appendLine("    userId=10123")
        }

        val result = BugReportSectionExtractor.extract(
            input = largeReport.byteInputStream(),
            displayName = "bugreport-Xiaomi.txt"
        )

        assertTrue(result is BugReportReadResult.Success)
        val sections = (result as BugReportReadResult.Success).sections.associateBy { it.source }
        assertTrue(sections.getValue("batterystats").output.contains("UID u0a123: 245.5"))
        assertTrue(sections.getValue("packages").output.contains("userId=10123"))
    }

    @Test
    fun rejectsEntryWhoseNameContainsParentTraversal() {
        val result = BugReportSectionExtractor.extract(
            input = zipOf("../bugreport.txt" to reportText).inputStream(),
            displayName = "bugreport.zip"
        )

        assertEquals(rejected(BugReportRejectReason.UNSAFE_ENTRY_NAME), result)
    }

    @Test
    fun rejectsMoreThanConfiguredEntryCount() {
        val result = BugReportSectionExtractor.extract(
            input = zipOf("a.txt" to "a", "b.txt" to "b", "c.txt" to "c").inputStream(),
            displayName = "bugreport.zip",
            limits = BugReportReadLimits(maxEntries = 2)
        )

        assertEquals(rejected(BugReportRejectReason.TOO_MANY_ENTRIES), result)
    }

    @Test
    fun rejectsAnEntryOverItsByteLimit() {
        val result = BugReportSectionExtractor.extract(
            input = zipOf("bugreport.txt" to "x".repeat(17)).inputStream(),
            displayName = "bugreport.zip",
            limits = BugReportReadLimits(maxEntryBytes = 16)
        )

        assertEquals(rejected(BugReportRejectReason.ENTRY_TOO_LARGE), result)
    }

    @Test
    fun plainTextAlsoRespectsPerReportByteLimit() {
        val result = BugReportSectionExtractor.extract(
            input = reportText.byteInputStream(),
            displayName = "bugreport.txt",
            limits = BugReportReadLimits(maxEntryBytes = 32, maxTotalBytes = 1024)
        )

        assertEquals(rejected(BugReportRejectReason.ENTRY_TOO_LARGE), result)
    }

    @Test
    fun stopsInflatingImmediatelyAfterEntryLimitIsExceeded() {
        val payload = ByteArray(1024 * 1024).also { Random(7).nextBytes(it) }
        val archive = zipBytesOf("bugreport.txt" to payload)
        val counted = CountingInputStream(archive.inputStream())

        val result = BugReportSectionExtractor.extract(
            input = counted,
            displayName = "bugreport.zip",
            limits = BugReportReadLimits(maxEntryBytes = 16)
        )

        assertEquals(rejected(BugReportRejectReason.ENTRY_TOO_LARGE), result)
        assertTrue(
            "Rejected ZIP should not drain the remaining compressed payload",
            counted.bytesRead < archive.size / 2
        )
    }

    @Test
    fun rejectsArchiveOverItsTotalByteLimit() {
        val result = BugReportSectionExtractor.extract(
            input = zipOf("bugreport-a.txt" to "a".repeat(11), "bugreport-b.txt" to "b".repeat(11)).inputStream(),
            displayName = "bugreport.zip",
            limits = BugReportReadLimits(maxEntryBytes = 16, maxTotalBytes = 20)
        )

        assertEquals(rejected(BugReportRejectReason.TOTAL_TOO_LARGE), result)
    }

    @Test
    fun rejectsNestedZipBeforeReadingItsPayload() {
        val result = BugReportSectionExtractor.extract(
            input = zipOf("reports/inner.zip" to "not read").inputStream(),
            displayName = "bugreport.zip"
        )

        assertEquals(rejected(BugReportRejectReason.NESTED_ARCHIVE), result)
    }

    @Test
    fun ignoresNestedAttachmentBeforeMainXiaomiReport() {
        val result = BugReportSectionExtractor.extract(
            input = zipOf(
                "FS/extra-report.zip" to "nested attachment is not opened",
                "bugreport-device.txt" to reportText
            ).inputStream(),
            displayName = "bugreport-device.zip"
        )

        assertTrue(result is BugReportReadResult.Success)
        assertEquals(
            listOf("batterystats", "alarm"),
            (result as BugReportReadResult.Success).sections.map { it.source }
        )
    }

    @Test
    fun acceptsXiaomiReportAfterManyBoundedAttachments() {
        val attachments = (0 until 256).map { index ->
            "FS/attachment-$index.txt" to ""
        }
        val archiveEntries = attachments + listOf(
            "FS/extra-report.zip" to "nested attachment is not opened",
            "bugreport-device.txt" to reportText
        )

        val result = BugReportSectionExtractor.extract(
            input = zipOf(*archiveEntries.toTypedArray()).inputStream(),
            displayName = "bugreport-device.zip"
        )

        assertTrue(result is BugReportReadResult.Success)
        assertEquals(
            listOf("batterystats", "alarm"),
            (result as BugReportReadResult.Success).sections.map { it.source }
        )
    }

    @Test
    fun readsMainReportFromOneNestedXiaomiBugReportArchive() {
        val nestedReport = zipOf("bugreport-device.txt" to reportText)
        val result = BugReportSectionExtractor.extract(
            input = zipBytesOf(
                "FS/encrypt_voice_trigger.zip" to byteArrayOf(1, 2, 3),
                "bugreport-Xiaomi 17 Pro Max-2026-09-15-154937.zip" to nestedReport
            ).inputStream(),
            displayName = "bugreport-2026-09-15-154647.zip"
        )

        assertTrue(result is BugReportReadResult.Success)
        assertEquals(
            listOf("batterystats", "alarm"),
            (result as BugReportReadResult.Success).sections.map { it.source }
        )
    }

    @Test
    fun keepsOuterHardwareLogThatAppearsBeforeNestedXiaomiReport() {
        val nestedReport = zipOf("bugreport-device.txt" to reportText)
        val result = BugReportSectionExtractor.extract(
            input = zipBytesOf(
                "FS/data/vendor/bootlog.txt" to "DDR Manufacturer ID = 0x01CE".toByteArray(),
                "bugreport-Xiaomi-2026.zip" to nestedReport
            ).inputStream(),
            displayName = "bugreport-outer.zip"
        )

        assertTrue(result is BugReportReadResult.Success)
        assertEquals(
            listOf("batterystats", "alarm", "hardware"),
            (result as BugReportReadResult.Success).sections.map { it.source }
        )
    }

    @Test
    fun keepsOuterHardwareLogThatAppearsAfterNestedXiaomiReport() {
        val nestedReport = zipOf("bugreport-device.txt" to reportText)
        val result = BugReportSectionExtractor.extract(
            input = zipBytesOf(
                "bugreport-Xiaomi-2026.zip" to nestedReport,
                "FS/data/vendor/bootlog.txt" to "UFS INQUIRY ID = 0x06".toByteArray()
            ).inputStream(),
            displayName = "bugreport-outer.zip"
        )

        assertTrue(result is BugReportReadResult.Success)
        assertEquals(
            listOf("batterystats", "alarm", "hardware"),
            (result as BugReportReadResult.Success).sections.map { it.source }
        )
    }

    @Test
    fun returnsUnsupportedForUnknownBinary() {
        val result = BugReportSectionExtractor.extract(
            input = byteArrayOf(0, 1, 2, 0, 3).inputStream(),
            displayName = "dump.bin"
        )

        assertEquals(rejected(BugReportRejectReason.UNSUPPORTED_FORMAT), result)
    }

    @Test
    fun stopsWhenAlreadyCancelled() {
        val result = BugReportSectionExtractor.extract(
            input = reportText.byteInputStream(),
            displayName = "bugreport.txt",
            isCancelled = { true }
        )

        assertEquals(BugReportReadResult.Cancelled, result)
    }

    @Test
    fun rejectsNulContaminatedBootRecordWithoutLosingOtherHardware() {
        val result = BugReportSectionExtractor.extract(
            input = zipOf(
                "bugreport.txt" to reportText,
                "FS/data/vendor/bootlog.txt" to
                    "B - 123 - Manufacturer ID = 2\u000055, Device Type = 8\nUFS INQUIRY ID = 0x06"
            ).inputStream(),
            displayName = "bugreport.zip"
        ) as BugReportReadResult.Success
        val hardware = result.sections.first { it.source == "hardware" }.output
        assertTrue(!hardware.contains("DDR"))
        assertTrue(hardware.contains("UFS"))
    }

    private fun rejected(reason: BugReportRejectReason) = BugReportReadResult.Rejected(reason)

    private fun zipOf(vararg entries: Pair<String, String>): ByteArray =
        zipBytesOf(*entries.map { (name, value) -> name to value.toByteArray() }.toTypedArray())

    private fun zipBytesOf(vararg entries: Pair<String, ByteArray>): ByteArray {
        val bytes = ByteArrayOutputStream()
        ZipOutputStream(bytes).use { zip ->
            entries.forEach { (name, value) ->
                zip.putNextEntry(ZipEntry(name))
                zip.write(value)
                zip.closeEntry()
            }
        }
        return bytes.toByteArray()
    }

    private class CountingInputStream(input: InputStream) : FilterInputStream(input) {
        var bytesRead: Long = 0
            private set

        override fun read(): Int = super.read().also { if (it >= 0) bytesRead += 1 }

        override fun read(buffer: ByteArray, offset: Int, length: Int): Int =
            super.read(buffer, offset, length).also { if (it > 0) bytesRead += it }
    }
}
