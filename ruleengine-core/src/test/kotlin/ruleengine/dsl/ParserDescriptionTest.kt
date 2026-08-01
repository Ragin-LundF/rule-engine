package ruleengine.dsl

import ruleengine.dsl.diagnostics.ParseException
import ruleengine.dsl.parser.Parser
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ParserDescriptionTest {

    @Test
    fun `parses the optional description clause into the AST`() {
        val txt = """
            rule "rent-payment" {
              description "A recurring payment whose purpose mentions rent."
              when
                purpose contains "rent"

              then
                label "rent"
            }
        """.trimIndent()

        val rules = Parser(input = txt).parseRules()
        assertEquals(
            expected = "A recurring payment whose purpose mentions rent.",
            actual = rules.single().description
        )
    }

    @Test
    fun `description is optional and defaults to null`() {
        val txt = """
            rule "rent-payment" {
              when
                purpose contains "rent"

              then
                label "rent"
            }
        """.trimIndent()

        val rules = Parser(input = txt).parseRules()
        assertNull(actual = rules.single().description)
    }

    @Test
    fun `description does not disturb the condition or the actions`() {
        val txt = """
            rule "vip" {
              description "The customer is flagged as vip."
              when
                tags containsAny ["vip", "premium"]

              then
                label "vip"
                score 10
            }
        """.trimIndent()

        val rule = Parser(input = txt).parseRules().single()
        assertEquals(expected = "vip", actual = rule.id)
        assertEquals(expected = 2, actual = rule.actions.size)
    }

    @Test
    fun `an empty description is kept as an empty string rather than dropped`() {
        val txt = """
            rule "r" {
              description ""
              when
                amount >= 1

              then
                label "a"
            }
        """.trimIndent()

        // The parser reports what was written; blank text is the validator's business, not the
        // parser's, so an empty clause must survive rather than be silently normalised to null.
        assertEquals(expected = "", actual = Parser(input = txt).parseRules().single().description)
    }

    @Test
    fun `a repeated description clause is rejected`() {
        val txt = """
            rule "r" {
              description "First."
              description "Second."
              when
                amount >= 1

              then
                label "a"
            }
        """.trimIndent()

        val ex = assertFailsWith<ParseException> { Parser(input = txt).parseRules() }
        assertTrue(
            actual = ex.message.orEmpty().contains("Duplicate 'description'"),
            message = "Expected a duplicate-description message, got: ${ex.message}"
        )
    }

    @Test
    fun `description without a string literal is rejected`() {
        val txt = """
            rule "r" {
              description
              when
                amount >= 1

              then
                label "a"
            }
        """.trimIndent()

        assertFailsWith<ParseException> { Parser(input = txt).parseRules() }
    }

    @Test
    fun `a field named description is still usable as a condition`() {
        // `description` is matched by text, not reserved by the lexer, so a schema may still declare
        // a field with that name. Only the position directly after `{` is claimed by the clause.
        val txt = """
            rule "r" {
              when
                description contains "rent"

              then
                label "a"
            }
        """.trimIndent()

        val rule = Parser(input = txt).parseRules().single()
        assertNull(actual = rule.description)
    }

    @Test
    fun `parses descriptions on every rule in a multi rule file`() {
        val txt = """
            rule "a" {
              description "First rule."
              when
                amount >= 1
              then
                label "a"
            }

            rule "b" {
              when
                amount >= 2
              then
                label "b"
            }

            rule "c" {
              description "Third rule."
              when
                amount >= 3
              then
                label "c"
            }
        """.trimIndent()

        val rules = Parser(input = txt).parseRules()
        assertEquals(
            expected = listOf("First rule.", null, "Third rule."),
            actual = rules.map { rule -> rule.description }
        )
    }
}
