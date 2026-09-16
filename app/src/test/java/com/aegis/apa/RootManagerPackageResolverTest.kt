package com.aegis.apa

import com.aegis.apa.tool.resolveKernelSuManagerPackage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class RootManagerPackageResolverTest {
    @Test
    fun nextOnlyInstallationReturnsNextPackage() {
        val installed = setOf("me.weishu.kernelsu.next")

        assertEquals(
            "me.weishu.kernelsu.next",
            resolveKernelSuManagerPackage(installed::contains)
        )
    }

    @Test
    fun originalPackageRemainsPreferredWhenBothAreInstalled() {
        val installed = setOf("me.weishu.kernelsu", "me.weishu.kernelsu.next")

        assertEquals(
            "me.weishu.kernelsu",
            resolveKernelSuManagerPackage(installed::contains)
        )
    }

    @Test
    fun missingKernelSuManagerReturnsNull() {
        assertNull(resolveKernelSuManagerPackage { false })
    }
}
