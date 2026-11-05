package ui.dsl

import kotlin.test.Test
import kotlin.test.assertEquals

class DslContextTest {

    @Test
    fun `the word after a joinByKey alias label starts a new path instead of taking the operator slot`() {
        val text = """
            rule "r" {
              when
                count(joinByKey("period", credit: credits 
        """.trimIndent()

        val context = analyzeDslContext(text = text, cursorPos = text.length)

        assertEquals(expected = "credits", actual = context.precedingField)
        assertEquals(expected = null, actual = context.precedingOperator)
    }
}
