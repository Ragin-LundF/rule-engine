package ruleengine.dsl

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue
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

        val parser = Parser(input = txt)
        val rules = parser.parseRules()
        assertEquals(expected = 1, actual = rules.size)
        val r = rules[0]
        assertEquals(expected = "rent-payment", actual = r.id)
        assertEquals(expected = 2, actual = r.actions.size)
    }

    // ── implicit AND ──────────────────────────────────────────────────────────

    /** Conditions on consecutive lines are joined with `and`, with no keyword required. */
    @Test
    fun `conditions on consecutive lines are joined with implicit and`() {
        val implicit = """
            rule "r" {
              when
                country equals "ng"
                amount >= 10000
              then
                flag "review"
            }
        """.trimIndent()
        val explicit = """
            rule "r" {
              when
                country equals "ng"
                and amount >= 10000
              then
                flag "review"
            }
        """.trimIndent()

        assertEquals(
            expected = Parser(input = explicit).parseRules().single().condition,
            actual = Parser(input = implicit).parseRules().single().condition,
        )
    }

    @Test
    fun `three implicit conditions produce one flat and`() {
        val txt = """
            rule "r" {
              when
                a equals "x"
                b equals "y"
                c equals "z"
              then
                flag "hit"
            }
        """.trimIndent()

        val condition = Parser(input = txt).parseRules().single().condition
        val and = condition as ruleengine.dsl.ast.AndAst
        assertEquals(expected = 3, actual = and.children.size)
    }

    @Test
    fun `implicit and mixes with explicit keywords`() {
        val txt = """
            rule "r" {
              when
                a equals "x"
                and b equals "y"
                c equals "z"
              then
                flag "hit"
            }
        """.trimIndent()

        val and = Parser(input = txt).parseRules().single().condition as ruleengine.dsl.ast.AndAst
        assertEquals(expected = 3, actual = and.children.size)
    }

    /** `or` must still bind looser than the implicit `and`. */
    @Test
    fun `or still binds looser than implicit and`() {
        val txt = """
            rule "r" {
              when
                a equals "x"
                or b equals "y"
                c equals "z"
              then
                flag "hit"
            }
        """.trimIndent()

        val or = Parser(input = txt).parseRules().single().condition as ruleengine.dsl.ast.OrAst
        assertEquals(expected = 2, actual = or.children.size)
        // `b and c` on the right-hand side, because the implicit and binds tighter.
        assertIs<ruleengine.dsl.ast.AndAst>(value = or.children[1])
    }

    @Test
    fun `a group followed by a condition on the next line is an implicit and`() {
        val txt = """
            rule "r" {
              when
                (a equals "x"
                or b equals "y")
                c equals "z"
              then
                flag "hit"
            }
        """.trimIndent()

        val and = Parser(input = txt).parseRules().single().condition as ruleengine.dsl.ast.AndAst
        assertEquals(expected = 2, actual = and.children.size)
        assertIs<ruleengine.dsl.ast.OrAst>(value = and.children[0])
    }

    @Test
    fun `ignoreCase on the same line still attaches to its condition`() {
        val txt = """
            rule "r" {
              when
                a contains "x" ignoreCase
                b equals "y"
              then
                flag "hit"
            }
        """.trimIndent()

        val and = Parser(input = txt).parseRules().single().condition as ruleengine.dsl.ast.AndAst
        assertEquals(expected = 2, actual = and.children.size)
        val first = and.children[0] as ruleengine.dsl.ast.ConditionAst
        assertTrue(actual = first.ignoreCase)
    }

    // ── boolean and date literals ─────────────────────────────────────────────

    @Test
    fun `parses boolean literals in a condition`() {
        val txt = """
            rule "r" {
              when
                isActive equals true
                and verified equals false
              then
                flag "hit"
            }
        """.trimIndent()

        val and = Parser(input = txt).parseRules().single().condition as ruleengine.dsl.ast.AndAst
        val first = and.children[0] as ruleengine.dsl.ast.ConditionAst
        val second = and.children[1] as ruleengine.dsl.ast.ConditionAst

        assertEquals(expected = ruleengine.dsl.ast.BooleanLiteral(value = true), actual = first.value)
        assertEquals(expected = ruleengine.dsl.ast.BooleanLiteral(value = false), actual = second.value)
    }

    @Test
    fun `parses a boolean literal on the right of a symbolic comparison`() {
        val txt = """
            rule "r" {
              when
                isActive == true
              then
                flag "hit"
            }
        """.trimIndent()

        val comparison = Parser(input = txt).parseRules()
            .single().condition as ruleengine.dsl.ast.ComparisonExpressionAst
        val right = comparison.right as ruleengine.dsl.ast.LiteralValueAst
        assertEquals(expected = ruleengine.dsl.ast.BooleanLiteral(value = true), actual = right.literal)
    }

    @Test
    fun `parses between with quoted date bounds`() {
        val txt = """
            rule "r" {
              when
                createdAt between "2024-01-01" "2024-12-31"
              then
                flag "hit"
            }
        """.trimIndent()

        val condition = Parser(input = txt).parseRules().single().condition as ruleengine.dsl.ast.ConditionAst
        val between = condition.value as ruleengine.dsl.ast.BetweenLiteral
        assertEquals(expected = "2024-01-01", actual = between.low)
        assertEquals(expected = "2024-12-31", actual = between.high)
    }

    @Test
    fun `parses an action without arguments`() {
        // Action schemas may declare `argTypes: []`; the argument count is the validator's business,
        // so the parser must accept an action with no literal following it.
        val txt = """
            rule "suppress-noise" {
              when
                level equals "debug"

              then
                suppress
            }
        """.trimIndent()

        val rules = Parser(input = txt).parseRules()
        val actions = rules.single().actions

        assertEquals(expected = 1, actual = actions.size)
        assertEquals(expected = "suppress", actual = actions.single().name)
        assertEquals(expected = emptyList(), actual = actions.single().arguments)
    }

    @Test
    fun `parses a zero-argument action followed by another action`() {
        val txt = """
            rule "mixed-actions" {
              when
                level equals "debug"

              then
                suppress
                tag "noise"
                score 10
            }
        """.trimIndent()

        val actions = Parser(input = txt).parseRules().single().actions

        assertEquals(expected = listOf("suppress", "tag", "score"), actual = actions.map { it.name })
        assertEquals(expected = listOf(0, 1, 1), actual = actions.map { it.arguments.size })
    }
}

