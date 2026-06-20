package ruleengine.dsl

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import ruleengine.dsl.ast.AndAst
import ruleengine.dsl.ast.ArithmeticOperatorAst
import ruleengine.dsl.ast.ArithmeticValueAst
import ruleengine.dsl.ast.ComparisonExpressionAst
import ruleengine.dsl.ast.ComparisonOperatorAst
import ruleengine.dsl.ast.ConditionAst
import ruleengine.dsl.ast.FieldAccessAst
import ruleengine.dsl.ast.FieldSegmentAst
import ruleengine.dsl.ast.FilterSegmentAst
import ruleengine.dsl.ast.FunctionCallValueAst
import ruleengine.dsl.ast.LiteralValueAst
import ruleengine.dsl.ast.NotAst
import ruleengine.dsl.ast.NumberLiteral
import ruleengine.dsl.ast.OrAst
import ruleengine.dsl.parser.Parser

class ParserExpressionTest {

    private fun parseCondition(input: String): ComparisonExpressionAst {
        val rule = """
            rule "test" {
              when
                $input
              then
                flag "ok"
            }
        """.trimIndent()
        val ast = Parser(input = rule).parseRules().single()
        return assertIs<ComparisonExpressionAst>(value = ast.condition)
    }

    @Test
    fun `parses simple function comparison`() {
        val expr = parseCondition("count(transactions) > 100")

        val left = assertIs<FunctionCallValueAst>(value = expr.left)
        assertEquals(expected = "count", actual = left.name)
        assertEquals(expected = 1, actual = left.arguments.size)
        val arg = assertIs<FieldAccessAst>(value = left.arguments[0])
        assertEquals(expected = listOf(FieldSegmentAst(name = "transactions")), actual = arg.path)

        assertEquals(expected = ComparisonOperatorAst.GT, actual = expr.operator)

        val right = assertIs<LiteralValueAst>(value = expr.right)
        assertIs<NumberLiteral>(value = right.literal)
    }

    @Test
    fun `parses dotted projection in function argument`() {
        val expr = parseCondition("sum(transactions.amount) > 1000")

        val left = assertIs<FunctionCallValueAst>(value = expr.left)
        assertEquals(expected = "sum", actual = left.name)
        val arg = assertIs<FieldAccessAst>(value = left.arguments[0])
        assertEquals(
            expected = listOf(FieldSegmentAst(name = "transactions"), FieldSegmentAst(name = "amount")),
            actual = arg.path
        )

        assertEquals(expected = ComparisonOperatorAst.GT, actual = expr.operator)
    }

    @Test
    fun `parses filtered projection in function argument`() {
        val expr = parseCondition("""sum(transactions[label == "risk"].amount) > 10""")

        val left = assertIs<FunctionCallValueAst>(value = expr.left)
        val arg = assertIs<FieldAccessAst>(value = left.arguments[0])
        assertEquals(expected = 3, actual = arg.path.size)
        assertIs<FieldSegmentAst>(value = arg.path[0])
        assertIs<FilterSegmentAst>(value = arg.path[1])
        assertIs<FieldSegmentAst>(value = arg.path[2])

        assertEquals(expected = ComparisonOperatorAst.GT, actual = expr.operator)
    }

    @Test
    fun `parses two aggregates with arithmetic on right side`() {
        val expr = parseCondition(
            """sum(transactions[label == "risk"].amount) > sum(transactions[amount > 0].amount) * 0.03"""
        )

        assertIs<FunctionCallValueAst>(value = expr.left)
        assertEquals(expected = ComparisonOperatorAst.GT, actual = expr.operator)

        val right = assertIs<ArithmeticValueAst>(value = expr.right)
        assertEquals(expected = ArithmeticOperatorAst.MULTIPLY, actual = right.operator)
        assertIs<FunctionCallValueAst>(value = right.left)
        assertIs<LiteralValueAst>(value = right.right)
    }

    @Test
    fun `parses arithmetic precedence - addition and multiplication`() {
        val expr = parseCondition("amount + fee * 2 <= 100")

        val left = assertIs<ArithmeticValueAst>(value = expr.left)
        assertEquals(expected = ArithmeticOperatorAst.ADD, actual = left.operator)
        assertIs<FieldAccessAst>(value = left.left)

        val rightOfAdd = assertIs<ArithmeticValueAst>(value = left.right)
        assertEquals(expected = ArithmeticOperatorAst.MULTIPLY, actual = rightOfAdd.operator)

        assertEquals(expected = ComparisonOperatorAst.LTE, actual = expr.operator)
    }

    @Test
    fun `parses parenthesized arithmetic`() {
        val expr = parseCondition("(amount + fee) * 2 <= 100")

        val left = assertIs<ArithmeticValueAst>(value = expr.left)
        assertEquals(expected = ArithmeticOperatorAst.MULTIPLY, actual = left.operator)

        val leftOfMul = assertIs<ArithmeticValueAst>(value = left.left)
        assertEquals(expected = ArithmeticOperatorAst.ADD, actual = leftOfMul.operator)

        assertEquals(expected = ComparisonOperatorAst.LTE, actual = expr.operator)
    }

    @Test
    fun `legacy named-operator condition still parses as ConditionAst`() {
        val rule = """
            rule "legacy" {
              when
                amount greater_than 100
              then
                flag "ok"
            }
        """.trimIndent()
        val ast = Parser(input = rule).parseRules().single()
        val cond = assertIs<ConditionAst>(value = ast.condition)
        assertEquals(expected = "amount", actual = cond.field)
        assertEquals(expected = "greater_than", actual = cond.operator)
    }

    @Test
    fun `legacy symbolic operator on plain field still parses as ConditionAst`() {
        val rule = """
            rule "legacy-sym" {
              when
                amount >= 500
              then
                flag "ok"
            }
        """.trimIndent()
        val ast = Parser(input = rule).parseRules().single()
        assertIs<ConditionAst>(value = ast.condition)
    }

    @Test
    fun `and expression still parses`() {
        val rule = """
            rule "and-test" {
              when
                amount greater_than 100 and country equals "us"
              then
                flag "ok"
            }
        """.trimIndent()
        val ast = Parser(input = rule).parseRules().single()
        assertIs<AndAst>(value = ast.condition)
    }

    @Test
    fun `or expression still parses`() {
        val rule = """
            rule "or-test" {
              when
                amount greater_than 100 or country equals "us"
              then
                flag "ok"
            }
        """.trimIndent()
        val ast = Parser(input = rule).parseRules().single()
        assertIs<OrAst>(value = ast.condition)
    }

    @Test
    fun `not expression still parses`() {
        val rule = """
            rule "not-test" {
              when
                not amount greater_than 100
              then
                flag "ok"
            }
        """.trimIndent()
        val ast = Parser(input = rule).parseRules().single()
        assertIs<NotAst>(value = ast.condition)
    }
}
