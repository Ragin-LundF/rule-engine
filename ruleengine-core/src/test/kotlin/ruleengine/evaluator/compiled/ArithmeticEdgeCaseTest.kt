package ruleengine.evaluator.compiled

import ruleengine.compiler.Compiler
import ruleengine.core.domain.dto.ConditionVerdict
import ruleengine.core.domain.dto.field.FieldDefinition
import ruleengine.core.domain.dto.field.FieldId
import ruleengine.core.domain.dto.field.FieldSchema
import ruleengine.core.domain.dto.field.FieldType
import ruleengine.dsl.ast.value.ArithmeticOperatorAst
import ruleengine.dsl.parser.Parser
import ruleengine.evaluator.compiled.value.ArithmeticCompiledValueExpression
import ruleengine.evaluator.compiled.value.LiteralCompiledValueExpression
import ruleengine.evaluator.compiled.value.result.NumberExpressionValue
import ruleengine.evaluator.context.PreparedRuleContext
import ruleengine.evaluator.context.RuleContext
import java.math.BigDecimal
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Arithmetic edge cases the engine has always had and nothing pinned.
 *
 * All three produce a missing value rather than an exception, which makes the comparison over them
 * undecidable — the engine never throws mid-evaluation and never invents an infinity.
 */
class ArithmeticEdgeCaseTest {

    private val schema = FieldSchema(
        name = "arithmetic-schema",
        fields = mapOf(
            FieldId(value = "amount") to FieldDefinition(
                id = FieldId(value = "amount"),
                type = FieldType.DECIMAL
            ),
            FieldId(value = "divisor") to FieldDefinition(
                id = FieldId(value = "divisor"),
                type = FieldType.DECIMAL
            ),
            FieldId(value = "label") to FieldDefinition(
                id = FieldId(value = "label"),
                type = FieldType.TEXT
            )
        )
    )

    @Test
    fun `dividing by zero is undecided rather than an error`() {
        assertEquals(
            expected = ConditionVerdict.UNKNOWN,
            actual = verdict(
                condition = "amount / divisor > 1",
                fields = arrayOf("amount" to 100, "divisor" to 0)
            ),
            message = "division by zero yields a missing value, so the comparison cannot be decided"
        )
    }

    @Test
    fun `dividing by a non-zero value decides normally`() {
        assertEquals(
            expected = ConditionVerdict.TRUE,
            actual = verdict(
                condition = "amount / divisor > 1",
                fields = arrayOf("amount" to 100, "divisor" to 4)
            ),
            message = "the ordinary division path must keep working"
        )
    }

    /** The documented precision contract: 10 decimal places, half-up. */
    @Test
    fun `an inexact division rounds to ten decimal places half-up`() {
        // 1 / 3 = 0.3333333333 at 10 places, so the quotient is above the tenth-place floor and
        // below the value one unit higher.
        assertEquals(
            expected = ConditionVerdict.TRUE,
            actual = verdict(
                condition = "amount / divisor > 0.3333333332",
                fields = arrayOf("amount" to 1, "divisor" to 3)
            ),
        )
        assertEquals(
            expected = ConditionVerdict.FALSE,
            actual = verdict(
                condition = "amount / divisor > 0.3333333333",
                fields = arrayOf("amount" to 1, "divisor" to 3)
            ),
            message = "rounded to 10 places the quotient is exactly 0.3333333333, so it is not greater"
        )
    }

    /** RULE-SPEC: a division that divides evenly is exact, even past ten decimal places. */
    @Test
    fun `an even division keeps every decimal place`() {
        assertEquals(
            expected = ConditionVerdict.TRUE,
            actual = verdict(
                condition = "amount / divisor == 0.061728394506",
                fields = arrayOf("amount" to "0.123456789012", "divisor" to 2)
            ),
            message = "rounding to ten places would give 0.0617283945"
        )
        assertEquals(
            expected = ConditionVerdict.TRUE,
            actual = verdict(condition = "amount / divisor == 2.5", fields = arrayOf("amount" to 10, "divisor" to 4))
        )
    }

    @Test
    fun `a quotient is exact when it terminates and has ten places otherwise`() {
        val context = PreparedRuleContext.prepare(ctx = RuleContext.of(), schema = schema)
        fun quotient(dividend: String, divisor: String): String {
            val expression = ArithmeticCompiledValueExpression(
                left = LiteralCompiledValueExpression(value = NumberExpressionValue(value = BigDecimal(dividend))),
                operator = ArithmeticOperatorAst.DIVIDE,
                right = LiteralCompiledValueExpression(value = NumberExpressionValue(value = BigDecimal(divisor))),
                cost = EvaluationCost.CHEAP,
            )
            return (expression.evaluate(context = context) as NumberExpressionValue).value.toPlainString()
        }

        assertEquals(expected = "0.3333333333", actual = quotient(dividend = "1", divisor = "3"))
        assertEquals(expected = "2.5", actual = quotient(dividend = "10", divisor = "4"))
        assertEquals(expected = "0.061728394506", actual = quotient(dividend = "0.123456789012", divisor = "2"))
        assertEquals(expected = "0.5", actual = quotient(dividend = "3", divisor = "6"))
        assertEquals(expected = "-0.1666666667", actual = quotient(dividend = "-1", divisor = "6"))
        assertEquals(expected = "40", actual = quotient(dividend = "1", divisor = "0.025"))
    }

    @Test
    fun `a non-numeric operand makes the expression undecided`() {
        assertEquals(
            expected = ConditionVerdict.UNKNOWN,
            actual = verdict(
                condition = "amount + label > 1",
                fields = arrayOf("amount" to 100, "label" to "abc")
            ),
            message = "text is not a number, so the sum has no value and the comparison cannot decide"
        )
    }

    @Test
    fun `a missing operand makes the expression undecided`() {
        assertEquals(
            expected = ConditionVerdict.UNKNOWN,
            actual = verdict(condition = "amount + divisor > 1", fields = arrayOf("amount" to 100)),
            message = "an absent operand propagates, as it does everywhere else"
        )
    }

    private fun verdict(condition: String, fields: Array<Pair<String, Any?>>): ConditionVerdict {
        val rule = """
            rule "arithmetic-test" {
              description "arithmetic edge cases"
              when
                $condition
              then
                flag "ok"

              not_exists
                flag "undecided"
            }
        """.trimIndent()
        val compiled = Compiler.compileRules(asts = Parser(input = rule).parseRules(), schema = schema).single()
        val prepared = PreparedRuleContext.prepare(ctx = RuleContext.of(*fields), schema = schema)
        return compiled.expression.evaluate(context = prepared, trace = null)
    }
}
