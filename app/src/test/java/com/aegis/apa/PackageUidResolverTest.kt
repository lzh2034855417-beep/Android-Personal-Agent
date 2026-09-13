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
}
