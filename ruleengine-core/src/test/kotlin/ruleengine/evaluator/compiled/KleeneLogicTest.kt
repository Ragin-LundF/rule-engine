package ruleengine.evaluator.compiled

import ruleengine.core.domain.dto.ConditionVerdict
import ruleengine.core.domain.dto.field.FieldSchema
import ruleengine.evaluator.compiled.logic.AndExpression
import ruleengine.evaluator.compiled.logic.NotExpression
import ruleengine.evaluator.compiled.logic.OrExpression
import ruleengine.evaluator.context.PreparedRuleContext
import ruleengine.evaluator.context.RuleContext
import ruleengine.evaluator.trace.TraceCollector
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * The truth table the combinators implement, one row at a time.
 *
 * Worth spelling out rather than inferring from rule-level tests: the whole backwards-compatibility
 * argument rests on `and` and `or` agreeing with two-valued logic once an undecided answer collapses to
 * false at the top of a rule, and on `not` being the single place that does not.
 */
class KleeneLogicTest {

    private val context = PreparedRuleContext.prepare(
        ctx = RuleContext.of(),
        schema = FieldSchema(name = "empty", fields = emptyMap()),
    )

    // ── and ───────────────────────────────────────────────────────────────────

    @Test
    fun `and is true only when every child is true`() {
        assertEquals(expected = ConditionVerdict.TRUE, actual = and(ConditionVerdict.TRUE, ConditionVerdict.TRUE))
    }

    @Test
    fun `and is false as soon as one child is false, whatever the others say`() {
        assertEquals(expected = ConditionVerdict.FALSE, actual = and(ConditionVerdict.TRUE, ConditionVerdict.FALSE))
        assertEquals(
            expected = ConditionVerdict.FALSE,
            actual = and(ConditionVerdict.UNKNOWN, ConditionVerdict.FALSE),
        )
        assertEquals(
            expected = ConditionVerdict.FALSE,
            actual = and(ConditionVerdict.FALSE, ConditionVerdict.UNKNOWN),
        )
    }

    @Test
    fun `and is undecided when nothing is false and something could not be decided`() {
        assertEquals(
            expected = ConditionVerdict.UNKNOWN,
            actual = and(ConditionVerdict.TRUE, ConditionVerdict.UNKNOWN),
        )
        assertEquals(
            expected = ConditionVerdict.UNKNOWN,
            actual = and(ConditionVerdict.UNKNOWN, ConditionVerdict.UNKNOWN),
        )
    }

    // ── or ────────────────────────────────────────────────────────────────────

    @Test
    fun `or is true as soon as one child is true, whatever the others say`() {
        assertEquals(expected = ConditionVerdict.TRUE, actual = or(ConditionVerdict.FALSE, ConditionVerdict.TRUE))
        assertEquals(
            expected = ConditionVerdict.TRUE,
            actual = or(ConditionVerdict.UNKNOWN, ConditionVerdict.TRUE),
        )
        assertEquals(
            expected = ConditionVerdict.TRUE,
            actual = or(ConditionVerdict.TRUE, ConditionVerdict.UNKNOWN),
        )
    }

    @Test
    fun `or is false only when every child is false`() {
        assertEquals(expected = ConditionVerdict.FALSE, actual = or(ConditionVerdict.FALSE, ConditionVerdict.FALSE))
    }

    @Test
    fun `or is undecided when nothing is true and something could not be decided`() {
        assertEquals(
            expected = ConditionVerdict.UNKNOWN,
            actual = or(ConditionVerdict.FALSE, ConditionVerdict.UNKNOWN),
        )
    }

    // ── not ───────────────────────────────────────────────────────────────────

    @Test
    fun `not inverts a decided child either way`() {
        assertEquals(expected = ConditionVerdict.FALSE, actual = not(ConditionVerdict.TRUE, unknownAware = false))
        assertEquals(expected = ConditionVerdict.TRUE, actual = not(ConditionVerdict.FALSE, unknownAware = false))
        assertEquals(expected = ConditionVerdict.FALSE, actual = not(ConditionVerdict.TRUE, unknownAware = true))
        assertEquals(expected = ConditionVerdict.TRUE, actual = not(ConditionVerdict.FALSE, unknownAware = true))
    }

    /**
     * The one divergence, and the reason `unknownAware` exists: a rule that never asked about missing
     * data keeps reading an undecided child as false, so its negation stays true.
     */
    @Test
    fun `not reads an undecided child as false unless the rule asked otherwise`() {
        assertEquals(expected = ConditionVerdict.TRUE, actual = not(ConditionVerdict.UNKNOWN, unknownAware = false))
        assertEquals(
            expected = ConditionVerdict.UNKNOWN,
            actual = not(ConditionVerdict.UNKNOWN, unknownAware = true),
        )
    }

    // ── short circuiting ──────────────────────────────────────────────────────

    @Test
    fun `and keeps looking for a false after an undecided child`() {
        val evaluated = mutableListOf<String>()
        val expression = AndExpression(
            children = listOf(
                Fixed(verdict = ConditionVerdict.UNKNOWN, name = "first", evaluated = evaluated),
                Fixed(verdict = ConditionVerdict.FALSE, name = "second", evaluated = evaluated),
            )
        )

        assertEquals(expected = ConditionVerdict.FALSE, actual = expression.evaluate(context = context))
        assertEquals(expected = listOf("first", "second"), actual = evaluated)
    }

    @Test
    fun `and stops at the first false`() {
        val evaluated = mutableListOf<String>()
        val expression = AndExpression(
            children = listOf(
                Fixed(verdict = ConditionVerdict.FALSE, name = "first", evaluated = evaluated),
                Fixed(verdict = ConditionVerdict.TRUE, name = "second", evaluated = evaluated),
            )
        )

        assertEquals(expected = ConditionVerdict.FALSE, actual = expression.evaluate(context = context))
        assertEquals(expected = listOf("first"), actual = evaluated)
    }

    @Test
    fun `or keeps looking for a true after an undecided child`() {
        val evaluated = mutableListOf<String>()
        val expression = OrExpression(
            children = listOf(
                Fixed(verdict = ConditionVerdict.UNKNOWN, name = "first", evaluated = evaluated),
                Fixed(verdict = ConditionVerdict.TRUE, name = "second", evaluated = evaluated),
            )
        )

        assertEquals(expected = ConditionVerdict.TRUE, actual = expression.evaluate(context = context))
        assertEquals(expected = listOf("first", "second"), actual = evaluated)
    }

    private fun and(vararg verdicts: ConditionVerdict): ConditionVerdict {
        return AndExpression(children = verdicts.map { verdict -> Fixed(verdict = verdict) })
            .evaluate(context = context)
    }

    private fun or(vararg verdicts: ConditionVerdict): ConditionVerdict {
        return OrExpression(children = verdicts.map { verdict -> Fixed(verdict = verdict) })
            .evaluate(context = context)
    }

    private fun not(verdict: ConditionVerdict, unknownAware: Boolean): ConditionVerdict {
        return NotExpression(child = Fixed(verdict = verdict), unknownAware = unknownAware)
            .evaluate(context = context)
    }

    /** A child that answers what the test told it to, and records that it was asked. */
    private class Fixed(
        private val verdict: ConditionVerdict,
        private val name: String = "child",
        private val evaluated: MutableList<String> = mutableListOf(),
    ) : CompiledExpression {
        override val cost: EvaluationCost = EvaluationCost.VERY_CHEAP

        override fun evaluate(context: PreparedRuleContext, trace: TraceCollector?): ConditionVerdict {
            evaluated += name
            return verdict
        }
    }
}
