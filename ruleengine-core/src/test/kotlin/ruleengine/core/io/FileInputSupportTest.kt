package ruleengine.core.io

import ruleengine.core.errors.InputTooLargeException
import java.io.ByteArrayInputStream
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class FileInputSupportTest {
    @Test
    fun `stream input above the limit is refused`() {
        val failure = assertFailsWith<InputTooLargeException> {
            FileInputSupport.readBoundedText(
                stream = ByteArrayInputStream(ByteArray(size = 11)),
                kind = "rule file",
                name = "big.rule",
                maxBytes = 10
            )
        }

        assertEquals(expected = "big.rule", actual = failure.path.toString())
        assertEquals(expected = 10L, actual = failure.maxBytes)
        assertEquals(expected = "rule file", actual = failure.kind)
    }

    @Test
    fun `stream input at the limit is read`() {
        val text = FileInputSupport.readBoundedText(
            stream = ByteArrayInputStream("0123456789".toByteArray(Charsets.UTF_8)),
            kind = "rule file",
            name = "exact.rule",
            maxBytes = 10
        )

        assertEquals(expected = "0123456789", actual = text)
    }

    @Test
    fun `stream input keeps non ascii characters intact`() {
        val text = FileInputSupport.readBoundedText(
            stream = ByteArrayInputStream("äöü — ok".toByteArray(Charsets.UTF_8)),
            kind = "schema",
            name = "schema.yaml"
        )

        assertEquals(expected = "äöü — ok", actual = text)
    }

    @Test
    fun `stream input rejects malformed utf8`() {
        assertFailsWith<CharacterCodingException> {
            FileInputSupport.readBoundedText(
                stream = ByteArrayInputStream(byteArrayOf(0x61, 0xC3.toByte())),
                kind = "rule file",
                name = "broken.rule"
            )
        }
    }
}
