package ruleengine.dsl

import ruleengine.dsl.ast.condition.AndAst
import ruleengine.dsl.ast.condition.ComparisonExpressionAst
import ruleengine.dsl.ast.condition.ComparisonOperatorAst
import ruleengine.dsl.ast.condition.ConditionAst
import ruleengine.dsl.ast.condition.ExpressionAst
import ruleengine.dsl.ast.condition.NotAst
import ruleengine.dsl.ast.condition.OrAst
import ruleengine.dsl.ast.literal.NumberLiteral
import ruleengine.dsl.ast.path.FieldSegmentAst
import ruleengine.dsl.ast.path.FilterSegmentAst
import ruleengine.dsl.ast.path.JoinSegmentAst
import ruleengine.dsl.ast.path.JoinSourceAst
import ruleengine.dsl.ast.path.SliceSegmentAst
import ruleengine.dsl.ast.value.ArithmeticOperatorAst
import ruleengine.dsl.ast.value.ArithmeticValueAst
import ruleengine.dsl.ast.value.FieldAccessAst
import ruleengine.dsl.ast.value.FunctionCallValueAst
import ruleengine.dsl.ast.value.LiteralValueAst
import ruleengine.dsl.ast.value.VariableRefAst
import ruleengine.dsl.diagnostics.ParseException
import ruleengine.dsl.parser.Parser
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertTrue

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

    // ── `contains` routing ────────────────────────────────────────────────────
    //
    // `contains` is the one named operator the modern expression path understands. These tests pin
    // which spelling goes down which path: only the legacy path enforces a field's declared
    // `operators:` list and normalizes the literal, so a plain field comparison silently changing
    // path would change the meaning of every text rule in every project.

    private fun conditionOf(input: String): ExpressionAst {
        return Parser(
            input = """
                rule "test" {
                  when
                    $input
                  then
                    flag "ok"
                }
            """.trimIndent()
        ).parseRules().single().condition
    }

    @Test
    fun `a plain field contains a literal stays on the legacy path`() {
        assertIs<ConditionAst>(value = conditionOf(input = """purpose contains "rent""""))
    }

    @Test
    fun `a plain field contains a literal keeps its ignoreCase modifier on the legacy path`() {
        val condition = assertIs<ConditionAst>(
            value = conditionOf(input = """purpose contains "rent" ignoreCase""")
        )
        assertEquals(expected = true, actual = condition.ignoreCase)
    }

    @Test
    fun `containsAny stays on the legacy path`() {
        val condition = assertIs<ConditionAst>(value = conditionOf(input = """tags containsAny ["a", "b"]"""))
        assertEquals(expected = "containsAny", actual = condition.operator)
    }

    @Test
    fun `a variable contains a literal takes the expression path`() {
        val expr = assertIs<ComparisonExpressionAst>(value = conditionOf(input = """${'$'}topics contains "billing""""))
        assertEquals(expected = ComparisonOperatorAst.CONTAINS, actual = expr.operator)
        assertIs<VariableRefAst>(value = expr.left)
    }

    @Test
    fun `a negated variable contains a literal takes the expression path`() {
        val negated = assertIs<NotAst>(value = conditionOf(input = """not ${'$'}topics contains "billing""""))
        val expr = assertIs<ComparisonExpressionAst>(value = negated.child)
        assertEquals(expected = ComparisonOperatorAst.CONTAINS, actual = expr.operator)
    }

    @Test
    fun `a field contains a variable takes the expression path`() {
        val expr = assertIs<ComparisonExpressionAst>(value = conditionOf(input = "purpose contains ${'$'}needle"))
        assertEquals(expected = ComparisonOperatorAst.CONTAINS, actual = expr.operator)
        assertIs<VariableRefAst>(value = expr.right)
    }

    // ── joinByKey ─────────────────────────────────────────────────────────────

    private fun path(vararg names: String): FieldAccessAst {
        return FieldAccessAst(path = names.map { name -> FieldSegmentAst(name = name) })
    }

    private val creditDebitJoin = JoinSegmentAst(
        key = "period",
        sources = listOf(
            JoinSourceAst(alias = "credit", path = path("credits", "amount")),
            JoinSourceAst(alias = "debit", path = path("debits", "amount")),
        )
    )

    @Test
    fun `joinByKey parses as the first segment of a filtered path`() {
        val expr = parseCondition(
            """count(joinByKey("period", credit: credits.amount, debit: debits.amount)[credit > debit]) >= 1"""
        )

        val count = assertIs<FunctionCallValueAst>(value = expr.left)
        val argument = assertIs<FieldAccessAst>(value = count.arguments.single())
        assertEquals(expected = creditDebitJoin, actual = argument.path[0])
        assertIs<FilterSegmentAst>(value = argument.path[1])
        assertEquals(expected = 2, actual = argument.path.size)
    }

    @Test
    fun `joinByKey composes with a slice and a member read`() {
        val expr = parseCondition(
            """sum(take(joinByKey("period", credit: credits.amount, debit: debits.amount), 2).credit) > 0"""
        )

        val sum = assertIs<FunctionCallValueAst>(value = expr.left)
        val argument = assertIs<FieldAccessAst>(value = sum.arguments.single())
        assertEquals(
            expected = listOf(
                creditDebitJoin,
                SliceSegmentAst(fromEnd = false, count = "2"),
                FieldSegmentAst(name = "credit"),
            ),
            actual = argument.path
        )
    }

    /** Validation names a missing key; the parser keeps the call so it can. */
    @Test
    fun `joinByKey without a key literal still parses`() {
        val expr = parseCondition("count(joinByKey(credit: credits.amount, debit: debits.amount)) > 0")

        val argument = assertIs<FieldAccessAst>(value = assertIs<FunctionCallValueAst>(value = expr.left).arguments[0])
        assertEquals(expected = null, actual = assertIs<JoinSegmentAst>(value = argument.path[0]).key)
    }

    /** It used to pass validation and then fail compilation with a bare NumberFormatException. */
    @Test
    fun `a number with two dots is a parse error with its position`() {
        val rule = """
            rule "test" {
              when
                count(orders) > 1.2.3
              then
                flag "x"
            }
        """.trimIndent()

        val error = assertFailsWith<ParseException> { Parser(input = rule).parseRules() }

        assertEquals(expected = 3, actual = error.line)
    }

    /**
     * A condition falls back to the legacy named-operator path when the modern parse throws, which
     * replaces the message with that path's own. An assignment has no such fallback, so it is where
     * the wording and position can be checked — the same approach `CollectionSortTest` takes.
     */
    private fun assignmentParseError(value: String): ParseException {
        val rule = """
            rule "test" {
              when
                count(credits) > 0
              then
                set rows = $value
            }
        """.trimIndent()
        return assertFailsWith<ParseException> { Parser(input = rule).parseRules() }
    }

    @Test
    fun `a joinByKey source without its alias is a parse error at the source`() {
        val error = assignmentParseError(value = """count(joinByKey("period", credits.amount, debit: debits.amount))""")

        assertEquals(expected = 5, actual = error.line)
        assertEquals(expected = 42, actual = error.column)
        assertTrue(actual = error.message.orEmpty().contains(other = "missing its ':'"), message = error.message)
    }

    @Test
    fun `a joinByKey source that is not a path is a parse error`() {
        val error = assignmentParseError(value = """count(joinByKey("period", credit: 5, debit: debits.amount))""")

        assertEquals(expected = 5, actual = error.line)
        assertTrue(
            actual = error.message.orEmpty().contains(other = "must be a collection path"),
            message = error.message
        )
    }
}
