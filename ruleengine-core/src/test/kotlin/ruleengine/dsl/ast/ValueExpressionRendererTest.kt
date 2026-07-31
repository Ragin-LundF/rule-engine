package ruleengine.dsl.ast

import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * The renderer exists so a compiled comparison can name itself in the trace. Its output is read by
 * rule authors, so these assert the exact text, not just that something came back.
 */
class ValueExpressionRendererTest {

    private fun field(vararg names: String): FieldAccessAst {
        return FieldAccessAst(path = names.map { name -> FieldSegmentAst(name = name) })
    }

    @Test
    fun `renders a dotted field path`() {
        assertEquals(
            expected = "reports.income.daysOfReport",
            actual = ValueExpressionRenderer.render(expr = field("reports", "income", "daysOfReport")),
        )
    }

    @Test
    fun `renders an aggregate call over a path`() {
        val expr = FunctionCallValueAst(
            name = "sum",
            arguments = listOf(field("reports", "chargebacks", "transactionsCount")),
        )
        assertEquals(
            expected = "sum(reports.chargebacks.transactionsCount)",
            actual = ValueExpressionRenderer.render(expr = expr),
        )
    }

    /** A filter binds to the segment before it, so it must not be preceded by a dot. */
    @Test
    fun `renders a filter segment attached to its preceding field`() {
        val expr = FunctionCallValueAst(
            name = "count",
            arguments = listOf(
                FieldAccessAst(
                    path = listOf(
                        FieldSegmentAst(name = "reports"),
                        FieldSegmentAst(name = "accountData"),
                        FilterSegmentAst(
                            expression = ConditionAst(
                                field = "accountType",
                                operator = "==",
                                value = StringLiteral(value = "CHECKING"),
                            ),
                        ),
                    ),
                ),
            ),
        )
        assertEquals(
            expected = """count(reports.accountData[accountType == "CHECKING"])""",
            actual = ValueExpressionRenderer.render(expr = expr),
        )
    }

    @Test
    fun `renders nested arithmetic with explicit parentheses`() {
        val expr = ArithmeticValueAst(
            left = ArithmeticValueAst(
                left = field("a"),
                operator = ArithmeticOperatorAst.ADD,
                right = field("b"),
            ),
            operator = ArithmeticOperatorAst.MULTIPLY,
            right = LiteralValueAst(literal = NumberLiteral(value = "2")),
        )
        assertEquals(
            expected = "((a + b) * 2)",
            actual = ValueExpressionRenderer.render(expr = expr),
        )
    }

    @Test
    fun `renders every arithmetic operator`() {
        val operators = mapOf(
            ArithmeticOperatorAst.ADD to "+",
            ArithmeticOperatorAst.SUBTRACT to "-",
            ArithmeticOperatorAst.MULTIPLY to "*",
            ArithmeticOperatorAst.DIVIDE to "/",
        )
        operators.forEach { (operator, expected) ->
            val expr = ArithmeticValueAst(
                left = field("a"),
                operator = operator,
                right = field("b"),
            )
            assertEquals(expected = "(a $expected b)", actual = ValueExpressionRenderer.render(expr = expr))
        }
    }

    @Test
    fun `renders every literal kind`() {
        val literals = mapOf<LiteralAst, String>(
            StringLiteral(value = "rent") to "\"rent\"",
            NumberLiteral(value = "7.5") to "7.5",
            BooleanLiteral(value = true) to "true",
            ListLiteral(items = listOf(StringLiteral(value = "a"), NumberLiteral(value = "1"))) to "[\"a\", 1]",
            BetweenLiteral(low = "1", high = "9") to "1..9",
            ExtractionRefLiteral(groupIndex = 2) to "\$2",
        )
        literals.forEach { (literal, expected) ->
            assertEquals(
                expected = expected,
                actual = ValueExpressionRenderer.render(expr = LiteralValueAst(literal = literal)),
            )
        }
    }

    @Test
    fun `renders every comparison operator in a filter predicate`() {
        val operators = mapOf(
            ComparisonOperatorAst.EQ to "==",
            ComparisonOperatorAst.NEQ to "!=",
            ComparisonOperatorAst.GT to ">",
            ComparisonOperatorAst.GTE to ">=",
            ComparisonOperatorAst.LT to "<",
            ComparisonOperatorAst.LTE to "<=",
        )
        operators.forEach { (operator, expected) ->
            val comparison = ComparisonExpressionAst(
                left = field("amount"),
                operator = operator,
                right = LiteralValueAst(literal = NumberLiteral(value = "5")),
            )
            assertEquals(
                expected = "amount $expected 5",
                actual = ValueExpressionRenderer.renderExpression(expr = comparison),
            )
        }
    }

    @Test
    fun `renders boolean combinations inside a filter predicate`() {
        val and = AndAst(
            children = listOf(
                ConditionAst(field = "a", operator = "equals", value = NumberLiteral(value = "1")),
                NotAst(
                    child = ConditionAst(field = "b", operator = "equals", value = NumberLiteral(value = "2")),
                ),
            ),
        )
        assertEquals(
            expected = "a equals 1 and not b equals 2",
            actual = ValueExpressionRenderer.renderExpression(expr = and),
        )
    }
}
