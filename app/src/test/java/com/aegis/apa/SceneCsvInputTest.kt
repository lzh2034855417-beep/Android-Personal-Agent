package com.aegis.apa

import com.aegis.apa.tool.SceneCsvInput
import org.junit.Assert.*
import org.junit.Test
import java.nio.charset.CharacterCodingException

class SceneCsvInputTest {
    @Test fun readsUtf8WithoutChangingChineseLabels() {
        assertEquals("电量,应用\n80,示例", SceneCsvInput.read("电量,应用\n80,示例".byteInputStream()))
    }
    @Test fun oversizedInputFailsBeforeItCanBeParsed() {
        assertThrows(IllegalArgumentException::class.java) {
            SceneCsvInput.read(ByteArray(SceneCsvInput.MAX_BYTES + 1).inputStream())
        }
    }
    @Test fun invalidUtf8IsRejectedInsteadOfSilentlyReplacingCharacters() {
        assertThrows(CharacterCodingException::class.java) {
            SceneCsvInput.read(byteArrayOf(0xc3.toByte(), 0x28).inputStream())
        }
    }
}
