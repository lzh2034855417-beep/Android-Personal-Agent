package com.aegis.apa

import com.aegis.apa.agent.*
import java.io.ByteArrayInputStream
import java.io.InputStream
import java.io.IOException
import org.junit.Assert.*
import org.junit.Test

class CloudResponseReaderTest {
    @Test fun readsUtf8WithoutDamagingChinese() {
        assertEquals("设备状态正常", CloudResponseReader.read(ByteArrayInputStream("设备状态正常".toByteArray())))
    }
    @Test fun acceptsExactByteLimit() {
        assertEquals(CloudResponseReader.MAX_BYTES, CloudResponseReader.read(ByteArrayInputStream(ByteArray(CloudResponseReader.MAX_BYTES) { 65 })).length)
    }
    @Test fun oversizedStreamStopsAfterLimitPlusOneAndCloses() {
        var consumed = 0
        var closed = false
        val stream = object : InputStream() {
            override fun read(): Int { consumed++; return 65 }
            override fun close() { closed = true }
        }
        val error = assertThrows(AgentFailureException::class.java) { CloudResponseReader.read(stream) }
        assertEquals(AgentFailure.RESPONSE_TOO_LARGE, error.reason)
        assertEquals(CloudResponseReader.MAX_BYTES + 1, consumed)
        assertTrue(closed)
    }
    @Test fun rejectsMalformedUtf8() {
        val error = assertThrows(AgentFailureException::class.java) {
            CloudResponseReader.read(ByteArrayInputStream(byteArrayOf(0xC3.toByte(), 0x28)))
        }
        assertEquals(AgentFailure.INVALID_RESPONSE, error.reason)
    }
    @Test fun closesStreamAfterNetworkFailure() {
        var closed = false
        val stream = object : InputStream() {
            override fun read(): Int = throw IOException("private-network-detail")
            override fun close() { closed = true }
        }
        assertThrows(IOException::class.java) { CloudResponseReader.read(stream) }
        assertTrue(closed)
    }
}
