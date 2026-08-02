package ruleengine.dsl

import ruleengine.dsl.ast.ActionAst
import ruleengine.dsl.ast.ExtractionAst
import ruleengine.dsl.ast.StringLiteral
import ruleengine.dsl.diagnostics.ParseException
import ruleengine.dsl.parser.Parser
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

class ParserElseBranchTest {

    @Test
    fun `else block becomes the rule's else actions`() {
        val rule = parseSingle(
            body = """
                when
                  amount > 1000
                then
                  label "high"
                else
                  label "low"
            """.trimIndent()
        )

        assertEquals(expected = listOf("label"), actual = rule.actions.map { it.name })
        assertEquals(expected = listOf("label"), actual = rule.elseActions.map { it.name })
        assertEquals(expected = "high", actual = singleStringArgument(action = rule.actions.single()))
        assertEquals(expected = "low", actual = singleStringArgument(action = rule.elseActions.single()))
        assertTrue(actual = rule.hasElseBranch)
    }

    @Test
    fun `a rule without an else block has none`() {
        val rule = parseSingle(
            body = """
                when
                  amount > 1000
                then
                  label "high"
            """.trimIndent()
        )

        assertTrue(actual = rule.elseActions.isEmpty())
        assertTrue(actual = rule.elseAssignments.isEmpty())
        assertFalse(actual = rule.hasElseBranch)
    }

    @Test
    fun `else block carries set clauses and extractions like a then block`() {
        val rule = parseSingle(
            body = """
                when
                  amount > 1000
                then
                  set tier = 2
                else
                  set tier = 1
                  label "low"
                  extract purpose regex("ref-([0-9]+)", 1) category ${'$'}1
            """.trimIndent()
        )

        assertEquals(expected = listOf("tier"), actual = rule.assignments.map { it.name })
        assertEquals(expected = listOf("tier"), actual = rule.elseAssignments.map { it.name })
        assertEquals(expected = listOf("label", "category"), actual = rule.elseActions.map { it.name })
        assertIs<ExtractionAst.RegexExtraction>(value = rule.elseActions[1].extraction)
    }

    /**
     * The regression this guards: an argument-less action is followed by a literal check, and `else`
     * would be swallowed as that argument if a bare identifier counted as a literal.
     */
    @Test
    fun `a zero-argument action right before else does not swallow the keyword`() {
        val rule = parseSingle(
            body = """
                when
                  amount > 1000
                then
                  suppress
                else
                  label "low"
            """.trimIndent()
        )

        assertEquals(expected = listOf("suppress"), actual = rule.actions.map { it.name })
        assertTrue(actual = rule.actions.single().arguments.isEmpty())
        assertEquals(expected = listOf("label"), actual = rule.elseActions.map { it.name })
    }

    @Test
    fun `a set clause as the last then statement does not absorb the else block`() {
        val rule = parseSingle(
            body = """
                when
                  amount > 1000
                then
                  set tier = amount
                else
                  label "low"
            """.trimIndent()
        )

        assertEquals(expected = listOf("tier"), actual = rule.assignments.map { it.name })
        assertEquals(expected = listOf("label"), actual = rule.elseActions.map { it.name })
    }

    @Test
    fun `a second else block is rejected`() {
        val failure = assertFailsWith<ParseException> {
            parseSingle(
                body = """
                    when
                      amount > 1000
                    then
                      label "high"
                    else
                      label "low"
                    else
                      label "other"
                """.trimIndent()
            )
        }

        assertTrue(
            actual = failure.messageText.contains(other = "Duplicate 'else' block"),
            message = "unexpected message: ${failure.messageText}"
        )
    }

    @Test
    fun `an empty else block is rejected`() {
        val failure = assertFailsWith<ParseException> {
            parseSingle(
                body = """
                    when
                      amount > 1000
                    then
                      label "high"
                    else
                """.trimIndent()
            )
        }

        assertTrue(
            actual = failure.messageText.contains(other = "Empty 'else' block"),
            message = "unexpected message: ${failure.messageText}"
        )
    }

    @Test
    fun `else before then is not a branch`() {
        assertFailsWith<ParseException> {
            parseSingle(
                body = """
                    when
                      amount > 1000
                    else
                      label "low"
                    then
                      label "high"
                """.trimIndent()
            )
        }
    }

    @Test
    fun `position is still excluded from rule identity when an else block is present`() {
        val body = """
            when
              amount > 1000
            then
              label "high"
            else
              label "low"
        """.trimIndent()

        assertEquals(expected = parseSingle(body = body), actual = parseSingle(body = body))
    }

    @Test
    fun `a differing else block makes two otherwise equal rules unequal`() {
        val first = parseSingle(
            body = """
                when
                  amount > 1000
                then
                  label "high"
                else
                  label "low"
            """.trimIndent()
        )
        val second = parseSingle(
            body = """
                when
                  amount > 1000
                then
                  label "high"
                else
                  label "other"
            """.trimIndent()
        )

        assertFalse(actual = first == second)
    }

    // ── stop ──────────────────────────────────────────────────────────────────

    @Test
    fun `stop at the end of a then block sets the flag`() {
        val rule = parseSingle(
            body = """
                when
                  amount > 1000
                then
                  label "high"
                  stop
            """.trimIndent()
        )

        assertTrue(actual = rule.stopOnThen)
        assertFalse(actual = rule.stopOnElse)
        assertEquals(expected = listOf("label"), actual = rule.actions.map { it.name })
    }

    @Test
    fun `stop at the end of an else block sets only the else flag`() {
        val rule = parseSingle(
            body = """
                when
                  amount > 1000
                then
                  label "high"
                else
                  label "low"
                  stop
            """.trimIndent()
        )

        assertFalse(actual = rule.stopOnThen)
        assertTrue(actual = rule.stopOnElse)
    }

    @Test
    fun `both branches may stop`() {
        val rule = parseSingle(
            body = """
                when
                  amount > 1000
                then
                  label "high"
                  stop
                else
                  label "low"
                  stop
            """.trimIndent()
        )

        assertTrue(actual = rule.stopOnThen)
        assertTrue(actual = rule.stopOnElse)
    }

    /** `stop` is not an action, so it must not appear among them however the block is written. */
    @Test
    fun `stop is never read as an action`() {
        val rule = parseSingle(
            body = """
                when
                  amount > 1000
                then
                  stop
            """.trimIndent()
        )

        assertTrue(actual = rule.actions.isEmpty())
        assertTrue(actual = rule.stopOnThen)
    }

    /** An else block holding only `stop` is meaningful: halt when the condition does not hold. */
    @Test
    fun `an else block holding only stop is accepted`() {
        val rule = parseSingle(
            body = """
                when
                  amount > 1000
                then
                  label "high"
                else
                  stop
            """.trimIndent()
        )

        assertTrue(actual = rule.hasElseBranch)
        assertTrue(actual = rule.stopOnElse)
        assertTrue(actual = rule.elseActions.isEmpty())
    }

    @Test
    fun `an action after stop is rejected`() {
        val failure = assertFailsWith<ParseException> {
            parseSingle(
                body = """
                    when
                      amount > 1000
                    then
                      stop
                      label "high"
                """.trimIndent()
            )
        }

        assertTrue(
            actual = failure.messageText.contains(other = "'stop' must be the last statement"),
            message = "unexpected message: ${failure.messageText}"
        )
    }

    @Test
    fun `a set clause after stop is rejected`() {
        assertFailsWith<ParseException> {
            parseSingle(
                body = """
                    when
                      amount > 1000
                    then
                      stop
                      set tier = 1
                """.trimIndent()
            )
        }
    }

    @Test
    fun `stop before else is fine because else ends the block`() {
        val rule = parseSingle(
            body = """
                when
                  amount > 1000
                then
                  label "high"
                  stop
                else
                  label "low"
            """.trimIndent()
        )

        assertTrue(actual = rule.stopOnThen)
        assertEquals(expected = listOf("label"), actual = rule.elseActions.map { it.name })
    }

    @Test
    fun `a differing stop makes two otherwise equal rules unequal`() {
        val withStop = parseSingle(
            body = """
                when
                  amount > 1000
                then
                  label "high"
                  stop
            """.trimIndent()
        )
        val withoutStop = parseSingle(
            body = """
                when
                  amount > 1000
                then
                  label "high"
            """.trimIndent()
        )

        assertFalse(actual = withStop == withoutStop)
    }

    private fun singleStringArgument(action: ActionAst): String {
        return assertIs<StringLiteral>(value = action.arguments.single()).value
    }

    private fun parseSingle(body: String) = Parser(
        input = """
            rule "r" {
              description "d"
              $body
            }
        """.trimIndent()
    ).parseRules().single()
}
