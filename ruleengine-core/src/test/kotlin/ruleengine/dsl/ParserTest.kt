package ruleengine.dsl

import kotlin.test.Test
import kotlin.test.assertEquals
import ruleengine.dsl.parser.Parser

class ParserTest {
    @Test
    fun `parses simple rule into AST`() {
        val txt = """
            rule "rent-payment" {
              when
                purpose contains "miete"
                and amount >= 500

              then
                label "rent"
                category "housing"
            }
        """.trimIndent()

        val parser = Parser(txt)
        val rules = parser.parseRules()
        assertEquals(expected = 1, actual = rules.size)
        val r = rules[0]
        assertEquals(expected = "rent-payment", actual = r.id)
        assertEquals(expected = 2, actual = r.actions.size)
    }
}

