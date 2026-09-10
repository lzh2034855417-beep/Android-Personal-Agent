package com.aegis.apa

import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.core.content.FileProvider
import java.io.File

import org.junit.Test
import org.junit.runner.RunWith

import org.junit.Assert.*

/**
 * Instrumented test, which will execute on an Android device.
 *
 * See [testing documentation](http://d.android.com/tools/testing).
 */
@RunWith(AndroidJUnit4::class)
class ExampleInstrumentedTest {
    @Test
    fun reportProviderReadsOnlyTheSharedReportDirectory() {
        val appContext = InstrumentationRegistry.getInstrumentation().targetContext
        val authority = "${appContext.packageName}.reports"
        val directory = File(appContext.cacheDir, "report-cards").apply { mkdirs() }
        val report = File.createTempFile("provider-test-", ".png", directory)
        try {
            report.writeBytes(byteArrayOf(1, 2, 3))
            val uri = FileProvider.getUriForFile(appContext, authority, report)
            val read = appContext.contentResolver.openInputStream(uri)!!.use { it.readBytes() }
            assertArrayEquals(byteArrayOf(1, 2, 3), read)
            val provider = appContext.packageManager.resolveContentProvider(authority, 0)!!
            assertFalse(provider.exported)
            assertTrue(provider.grantUriPermissions)
        } finally { report.delete() }
    }

    @Test
    fun reportProviderRejectsFilesOutsideReportDirectory() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val privateFile = File.createTempFile("private-test-", ".txt", context.cacheDir)
        try {
            assertThrows(IllegalArgumentException::class.java) {
                FileProvider.getUriForFile(context, "${context.packageName}.reports", privateFile)
            }
        } finally { privateFile.delete() }
    }
}
