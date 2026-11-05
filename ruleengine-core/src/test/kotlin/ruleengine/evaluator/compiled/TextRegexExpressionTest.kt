package ruleengine.evaluator.compiled

import ruleengine.compiler.Compiler
import ruleengine.core.domain.dto.ConditionVerdict
import ruleengine.core.domain.dto.OperatorId
import ruleengine.core.domain.dto.field.FieldDefinition
import ruleengine.core.domain.dto.field.FieldId
import ruleengine.core.domain.dto.field.FieldSchema
import ruleengine.core.domain.dto.field.FieldType
import ruleengine.dsl.parser.Parser
import ruleengine.evaluator.compiled.text.RegexExtractExpression
import ruleengine.evaluator.compiled.text.TextRegexExpression
import ruleengine.evaluator.context.PreparedRuleContext
import ruleengine.evaluator.context.RuleContext
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class TextRegexExpressionTest {
    private val schema = FieldSchema(
        name = "test",
        fields = mapOf(
            FieldId(value = "purpose") to FieldDefinition(
                id = FieldId(value = "purpose"),
                type = FieldType.TEXT,
                operators = setOf(OperatorId(value = "regex"))
            )
        )
    )

    @Test
    fun `regex matches normal input`() {
        val prepared = preparedContext(value = "TXN-12345")
        val expression = TextRegexExpression(
            field = FieldId(value = "purpose"),
            pattern = Regex(pattern = "TXN-[0-9]+")
        )

        assertEquals(expected = ConditionVerdict.TRUE, actual = expression.evaluate(context = prepared))
    }

    /** Padding a value must not hide a blocklisted word: the whole text is matched, however long. */
    @Test
    fun `padded input is matched in full by regex and not regex`() {
        val padded = preparedContext(value = " ".repeat(n = 20_000) + "casino")

        assertEquals(
            expected = ConditionVerdict.TRUE,
            actual = ruleVerdict(condition = "purpose regex \"casino\"", prepared = padded)
        )
        assertEquals(
            expected = ConditionVerdict.FALSE,
            actual = ruleVerdict(condition = "not purpose regex \"casino\"", prepared = padded)
        )
    }

    @Test
    fun `catastrophic backtracking is cut off as undecided`() {
        val prepared = preparedContext(value = CATASTROPHIC_INPUT)
        val expression = TextRegexExpression(
            field = FieldId(value = "purpose"),
            pattern = Regex(pattern = CATASTROPHIC_PATTERN)
        )

        val (verdict, millis) = timed { expression.evaluate(context = prepared) }

        assertEquals(expected = ConditionVerdict.UNKNOWN, actual = verdict)
        assertTrue(actual = millis < MAX_EXPECTED_MILLIS, message = "took $millis ms")
    }

    @Test
    fun `extraction returns the group and nothing when backtracking is cut off`() {
        val extractor = RegexExtractExpression(
            field = FieldId(value = "purpose"),
            pattern = Regex(pattern = "ref ([0-9]+)"),
            groupIndex = 1
        )
        assertEquals(
            expected = "42",
            actual = extractor.extract(context = preparedContext(value = " ".repeat(n = 20_000) + "ref 42"))
        )

        val catastrophic = RegexExtractExpression(
            field = FieldId(value = "purpose"),
            pattern = Regex(pattern = CATASTROPHIC_PATTERN),
            groupIndex = 0
        )
        val (extracted, millis) = timed { catastrophic.extract(context = preparedContext(value = CATASTROPHIC_INPUT)) }

        assertNull(actual = extracted)
        assertTrue(actual = millis < MAX_EXPECTED_MILLIS, message = "took $millis ms")
    }

    @Test
    fun `a pattern that overflows the stack is undecided for regex and extracts nothing`() {
        val prepared = preparedContext(value = STACK_OVERFLOW_INPUT)
        val expression = TextRegexExpression(
            field = FieldId(value = "purpose"),
            pattern = Regex(pattern = STACK_OVERFLOW_PATTERN)
        )
        val extractor = RegexExtractExpression(
            field = FieldId(value = "purpose"),
            pattern = Regex(pattern = STACK_OVERFLOW_PATTERN),
            groupIndex = 0
        )

        assertEquals(expected = ConditionVerdict.UNKNOWN, actual = expression.evaluate(context = prepared))
        assertNull(actual = extractor.extract(context = prepared))
    }

    private fun <T> timed(block: () -> T): Pair<T, Long> {
        val start = System.nanoTime()
        val result = block()
        return result to (System.nanoTime() - start) / NANOS_PER_MILLI
    }

    private fun ruleVerdict(condition: String, prepared: PreparedRuleContext): ConditionVerdict {
        val rule = """
            rule "regex-check" {
              when
                $condition
              then
                flag "matched"

              not_exists
                flag "undecided"
            }
        """.trimIndent()
        val compiled = Compiler.compileRules(asts = Parser(input = rule).parseRules(), schema = schema).single()
        return compiled.expression.evaluate(context = prepared, trace = null)
    }

    private companion object {
        // Exponential backtracking: 30 word characters followed by a character that cannot end the match.
        // The back-reference keeps the JDK from memoising the loop, which defuses `^(\w+\s?)*$` alone.
        const val CATASTROPHIC_PATTERN = "^(\\w+\\s?)*\\1$"
        val CATASTROPHIC_INPUT = "a".repeat(n = 30) + "!"
        // An alternation under `*` recurses once per repetition, so a long input overflows the stack.
        const val STACK_OVERFLOW_PATTERN = "^(a|b)*$"
        val STACK_OVERFLOW_INPUT = "ab".repeat(n = 100_000)
        const val MAX_EXPECTED_MILLIS = 5_000L
        const val NANOS_PER_MILLI = 1_000_000L
    }

    private fun preparedContext(value: String): PreparedRuleContext {
        return PreparedRuleContext.prepare(
            ctx = RuleContext.of("purpose" to value),
            schema = schema
        )
    }
}

