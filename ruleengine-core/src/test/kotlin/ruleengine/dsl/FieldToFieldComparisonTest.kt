package ruleengine.dsl

import ruleengine.dsl.ast.ComparisonExpressionAst
import ruleengine.dsl.parser.Parser
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * Comparing one field against another, rather than against a literal.
 *
 * The legacy condition path cannot express it — its right-hand side is a literal — so a comparison
 * whose right side is a path has to take the value-expression route. Before that routing existed the
 * spelling failed to parse at all, with "Expected operator" pointing at the second field name.
 */
class FieldToFieldComparisonTest {

    @Test
    fun `an ordering comparison between two fields parses as a value comparison`() {
        val condition = parse(condition = "amount > limit")

        assertTrue(actual = condition is ComparisonExpressionAst, message = "got: $condition")
    }

    @Test
    fun `a comparison between two fields inside a filter parses`() {
        val condition = parse(condition = "count(orders[quantity >= threshold]) > 0")

        assertTrue(actual = condition is ComparisonExpressionAst, message = "got: $condition")
    }

    @Test
    fun `a comparison against a literal still takes the legacy path`() {
        val condition = parse(condition = "amount > 100")

        assertTrue(
            actual = condition !is ComparisonExpressionAst,
            message = "plain field-against-literal must keep its declared-operator checks, got: $condition"
        )
    }

    private fun parse(condition: String) = Parser(
        input = """
            rule "field-to-field" {
              when
                $condition
              then
                flag "ok"
            }
        """.trimIndent()
    ).parseRules().single().condition
}
