package com.aegis.apa

import com.aegis.apa.agent.CloudProviderCatalog
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CloudProviderCatalogTest {
    @Test
    fun providerNamesAndShortLabelsAreUnique() {
        val providers = CloudProviderCatalog.providers
        assertEquals(5, providers.size)
        assertEquals(providers.size, providers.map { it.name }.distinct().size)
        assertEquals(providers.size, providers.map { it.shortLabel }.distinct().size)
    }

    @Test
    fun releaseProviderRoutesMatchExpectedModels() {
        val expectedModels = mapOf(
            "DeepSeek" to "deepseek-v4-flash",
            "OpenAI · GPT" to "gpt-5.6-terra",
            "Anthropic · Claude" to "claude-sonnet-5",
            "Xiaomi · MiMo" to "mimo-v2.5",
            "Moonshot · Kimi" to "kimi-k3"
        )

        expectedModels.forEach { (providerName, model) ->
            val provider = CloudProviderCatalog.find(providerName)
            assertNotNull(provider)
            assertEquals(model, provider?.model)
            assertTrue(provider?.endpoint?.startsWith("https://") == true)
        }
    }

    @Test
    fun unknownProviderIsRejected() {
        assertEquals(null, CloudProviderCatalog.find("unknown"))
    }
}
