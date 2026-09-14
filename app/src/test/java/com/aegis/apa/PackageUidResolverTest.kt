package com.aegis.apa

import com.aegis.apa.tool.PackageUidResolver
import org.junit.Assert.assertEquals
import org.junit.Test

class PackageUidResolverTest {
    @Test
    fun keepsEverySortedPackageForASharedUid() {
        val packages = PackageUidResolver.resolve(
            """
                package:com.example.two uid:10123
                package:com.example.one uid:10123
                package:com.example.two uid:10123
            """.trimIndent()
        )

        assertEquals(
            listOf("com.example.one", "com.example.two"),
            packages.getValue(10123)
        )
    }

    @Test
    fun resolvesPackageAndUidFromDumpsysPackageBlock() {
        val packages = PackageUidResolver.resolve(
            """
                Packages:
                  Package [com.example.chat] (abc123):
                    userId=10123
                    versionCode=42
                  Package [com.example.reader] (def456):
                    userId=10456
            """.trimIndent()
        )

        assertEquals(listOf("com.example.chat"), packages.getValue(10123))
        assertEquals(listOf("com.example.reader"), packages.getValue(10456))
    }

    @Test
    fun composesWorkProfileUidFromPackageAppId() {
        val packages = PackageUidResolver.resolve(
            """
                Package [com.example.work] (abc123):
                  appId=10123
            """.trimIndent(),
            targetUids = setOf(1_010_123)
        )

        assertEquals(listOf("com.example.work"), packages.getValue(1_010_123))
    }

    @Test(timeout = 3_000)
    fun resolvesManyWorkProfilePackagesWithoutCrossProductScan() {
        val count = 10_000
        val output = buildString {
            repeat(count) { index ->
                appendLine("Package [com.example.app$index] (id$index):")
                appendLine("  appId=${10_000 + index}")
            }
        }
        val targetUids = (0 until count).mapTo(linkedSetOf()) { index ->
            1_000_000 + 10_000 + index
        }

        val packages = PackageUidResolver.resolve(output, targetUids)

        assertEquals(listOf("com.example.app0"), packages.getValue(1_010_000))
        assertEquals(listOf("com.example.app9999"), packages.getValue(1_019_999))
    }
}
