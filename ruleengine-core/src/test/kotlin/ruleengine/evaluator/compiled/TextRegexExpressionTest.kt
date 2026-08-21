package ruleengine.evaluator.compiled

import ruleengine.core.domain.dto.ConditionVerdict
import ruleengine.core.domain.dto.OperatorId
import ruleengine.core.domain.dto.field.FieldDefinition
import ruleengine.core.domain.dto.field.FieldId
import ruleengine.core.domain.dto.field.FieldSchema
import ruleengine.core.domain.dto.field.FieldType
import ruleengine.evaluator.compiled.text.TextRegexExpression
import ruleengine.evaluator.context.PreparedRuleContext
import ruleengine.evaluator.context.RuleContext
import kotlin.test.Test
import kotlin.test.assertEquals

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
    fun `regex matches normal input within limit`() {
        val prepared = preparedContext(value = "TXN-12345")
        val expression = TextRegexExpression(
            field = FieldId(value = "purpose"),
            pattern = Regex(pattern = "TXN-[0-9]+")
        )

        assertEquals(expected = ConditionVerdict.TRUE, actual = expression.evaluate(context = prepared))
    }

    @Test
    fun `regex short-circuits overlong input`() {
        val overlongInput = "a".repeat(TextRegexExpression.MAX_INPUT_LENGTH + 1)
        val prepared = preparedContext(value = overlongInput)
        val expression = TextRegexExpression(
            field = FieldId(value = "purpose"),
            pattern = Regex(pattern = "^(a+)+$")
        )

        assertEquals(expected = ConditionVerdict.FALSE, actual = expression.evaluate(context = prepared))
    }

    private fun preparedContext(value: String): PreparedRuleContext {
        return PreparedRuleContext.prepare(
            ctx = RuleContext.of("purpose" to value),
            schema = schema
        )
    }
}

