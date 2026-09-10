package com.aegis.apa

import android.content.Context
import android.content.ContextWrapper
import android.content.SharedPreferences
import android.content.pm.ApplicationInfo
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.aegis.apa.agent.ApiKeyStore
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.xmlpull.v1.XmlPullParser

/** Packaged resource contract only: does not invoke backup, restore, or OEM migration. */
@RunWith(AndroidJUnit4::class)
class BackupPolicyTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    @Test
    fun packagedManifestKeepsBackupDisabled() {
        assertEquals(0, context.applicationInfo.flags and ApplicationInfo.FLAG_ALLOW_BACKUP)
    }

    @Test
    fun legacyCloudBackupExcludesCredentialPreferences() {
        assertCredentialExclusions("fullBackupContent", "full-backup-content", null)
    }

    @Test
    fun android31CloudBackupExcludesCredentialPreferences() {
        assertCredentialExclusions("dataExtractionRules", "data-extraction-rules", "cloud-backup")
    }

    @Test
    fun android31DeviceTransferExcludesCredentialPreferences() {
        assertCredentialExclusions("dataExtractionRules", "data-extraction-rules", "device-transfer")
    }

    private fun assertCredentialExclusions(attribute: String, root: String, section: String?) {
        val filename = credentialPreferenceFilename()
        val exclusions = mutableSetOf<Pair<String, String>>()
        var foundSection = section == null
        var activeSection: String? = null
        context.resources.getXml(manifestResource(attribute)).use { parser ->
            while (parser.next() != XmlPullParser.END_DOCUMENT) {
                if (parser.eventType == XmlPullParser.START_TAG) {
                    if (parser.depth == 1) assertEquals(root, parser.name)
                    if (section != null && parser.depth == 2) {
                        activeSection = parser.name
                        if (activeSection == section) foundSection = true
                    }
                    val expectedDepth = if (section == null) 2 else 3
                    if (parser.name == "exclude" && parser.depth == expectedDepth &&
                        (section == null || activeSection == section)
                    ) {
                        exclusions += Pair(
                            parser.getAttributeValue(null, "domain") ?: "",
                            parser.getAttributeValue(null, "path") ?: ""
                        )
                    }
                } else if (parser.eventType == XmlPullParser.END_TAG && parser.depth == 2) {
                    activeSection = null
                }
            }
        }
        assertTrue("Missing backup section: $section", foundSection)
        // Require explicit file exclusions in both storage domains; a broad wildcard or
        // a rule under the other transport must not accidentally satisfy this contract.
        for (domain in listOf("sharedpref", "device_sharedpref")) {
            assertTrue(
                "$attribute/$section must exclude $domain/$filename",
                domain to filename in exclusions
            )
        }
    }

    private fun manifestResource(attribute: String): Int {
        context.assets.openXmlResourceParser("AndroidManifest.xml").use { parser ->
            while (parser.next() != XmlPullParser.END_DOCUMENT) {
                if (parser.eventType == XmlPullParser.START_TAG && parser.name == "application") {
                    val resource = parser.getAttributeResourceValue(
                        "http://schemas.android.com/apk/res/android", attribute, 0
                    )
                    assertTrue("Manifest must link $attribute to an XML resource", resource != 0)
                    return resource
                }
            }
        }
        error("Packaged manifest has no application element")
    }

    private fun credentialPreferenceFilename(): String {
        var preferenceName: String? = null
        val stopBeforeReading = PreferenceAccessIntercepted()
        val recordingContext = object : ContextWrapper(context) {
            override fun getSharedPreferences(name: String, mode: Int): SharedPreferences {
                preferenceName = name
                throw stopBeforeReading
            }
        }
        try {
            // Exercise the production storage path without reading or changing real keys.
            ApiKeyStore.load(recordingContext)
        } catch (intercepted: PreferenceAccessIntercepted) {
            assertTrue(intercepted === stopBeforeReading)
        }
        assertEquals("apa_api_credentials", preferenceName)
        return "${requireNotNull(preferenceName)}.xml"
    }

    private class PreferenceAccessIntercepted : RuntimeException()
}
