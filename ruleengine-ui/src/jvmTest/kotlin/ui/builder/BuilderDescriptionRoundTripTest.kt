package ui.builder

import ruleengine.dsl.parser.Parser
import ui.builder.model.BuilderRule
import ui.builder.model.mutable.BuilderEditorState
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Round-trips the optional `description` clause through DSL → Builder → DSL.
 *
 * The Builder replaces the rule's text in the Code editor with what
 * [BuilderToRuleDsl.generate] produces, so anything the Builder fails to carry is not just hidden —
 * it is deleted from the file the moment the user touches an unrelated condition.
 */
class BuilderDescriptionRoundTripTest {

    private fun roundTrip(dsl: String): String {
        val ast = Parser(input = dsl).parseRules().single()
        val builderRule = RuleAstToBuilderMapper.map(rule = ast)
        assertTrue(
            actual = builderRule is BuilderRule.Supported,
            message = "Fixture must be editable in the Builder, was: $builderRule"
        )
        val state = BuilderEditorState.fromBuilderRule(rule = builderRule)
        return assertNotNull(actual = BuilderToRuleDsl.generate(state = state))
    }

    private fun ruleWith(description: String?): String {
        val clause = if (description == null) "" else "  description \"$description\"\n"
        return """
            |rule "r" {
            |$clause  when
            |    amount >= 500
            |  then
            |    label "a"
            |}
        """.trimMargin()
    }

    @Test
    fun `a description survives the round trip`() {
        val generated = roundTrip(dsl = ruleWith(description = "A payment of at least 500."))
        assertEquals(
            expected = "A payment of at least 500.",
            actual = Parser(input = generated).parseRules().single().description
        )
    }

    @Test
    fun `a rule without a description does not gain an empty clause`() {
        val generated = roundTrip(dsl = ruleWith(description = null))

        assertFalse(
            actual = generated.contains(other = "description"),
            message = "Expected no description clause, got:\n$generated"
        )
        assertNull(actual = Parser(input = generated).parseRules().single().description)
    }

    @Test
    fun `a description containing quotes survives the round trip`() {
        // Unescaped, the inner quote would close the literal early and corrupt every rule after it.
        val generated = roundTrip(dsl = ruleWith(description = "Matches a purpose of \\\"rent\\\"."))

        assertEquals(
            expected = "Matches a purpose of \"rent\".",
            actual = Parser(input = generated).parseRules().single().description
        )
    }

    @Test
    fun `a description containing a backslash survives the round trip`() {
        val generated = roundTrip(dsl = ruleWith(description = "Path separator is \\\\ on Windows."))

        assertEquals(
            expected = "Path separator is \\ on Windows.",
            actual = Parser(input = generated).parseRules().single().description
        )
    }

    @Test
    fun `a description typed into the builder reaches the generated dsl`() {
        val ast = Parser(input = ruleWith(description = null)).parseRules().single()
        val state = BuilderEditorState.fromBuilderRule(rule = RuleAstToBuilderMapper.map(rule = ast))

        state.description = "Newly written in the Builder."
        val generated = assertNotNull(actual = BuilderToRuleDsl.generate(state = state))

        assertEquals(
            expected = "Newly written in the Builder.",
            actual = Parser(input = generated).parseRules().single().description
        )
    }

    @Test
    fun `clearing the description in the builder removes the clause`() {
        val ast = Parser(input = ruleWith(description = "To be removed.")).parseRules().single()
        val state = BuilderEditorState.fromBuilderRule(rule = RuleAstToBuilderMapper.map(rule = ast))

        state.description = "   "
        val generated = assertNotNull(actual = BuilderToRuleDsl.generate(state = state))

        assertNull(actual = Parser(input = generated).parseRules().single().description)
    }

    @Test
    fun `a pasted multi line description is collapsed onto one line`() {
        val ast = Parser(input = ruleWith(description = null)).parseRules().single()
        val state = BuilderEditorState.fromBuilderRule(rule = RuleAstToBuilderMapper.map(rule = ast))

        state.description = "First line.\n   Second line."
        val generated = assertNotNull(actual = BuilderToRuleDsl.generate(state = state))

        assertEquals(
            expected = "First line. Second line.",
            actual = Parser(input = generated).parseRules().single().description
        )
        assertEquals(
            expected = 1,
            actual = generated.lines().count { line -> line.contains(other = "description") },
            message = "The clause must stay on one line:\n$generated"
        )
    }

    @Test
    fun `the description clause precedes when in the generated dsl`() {
        val generated = roundTrip(dsl = ruleWith(description = "Ordering matters."))
        val lines = generated.lines().map { line -> line.trim() }

        assertTrue(
            actual = lines.indexOfFirst { it.startsWith("description") } <
                lines.indexOfFirst { it == "when" },
            message = "description must come before when:\n$generated"
        )
    }
}
