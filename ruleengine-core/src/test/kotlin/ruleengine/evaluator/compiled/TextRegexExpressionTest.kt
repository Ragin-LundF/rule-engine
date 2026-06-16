package ruleengine.evaluator.compiled

import ruleengine.core.domain.FieldDefinition
import ruleengine.core.domain.FieldId
import ruleengine.core.domain.FieldSchema
import ruleengine.core.domain.FieldType
import ruleengine.core.domain.OperatorId
import ruleengine.evaluator.context.PreparedRuleContext
import ruleengine.evaluator.context.RuleContext
import kotlin.test.Test
import kotlin.test.assertFalse
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
    fun `regex matches normal input within limit`() {
        val prepared = preparedContext(value = "TXN-12345")
        val expression = TextRegexExpression(
            field = FieldId(value = "purpose"),
            pattern = Regex(pattern = "TXN-[0-9]+")
        )

        assertTrue(actual = expression.evaluate(context = prepared))
    }

    @Test
    fun `regex short-circuits overlong input`() {
        val overlongInput = "a".repeat(TextRegexExpression.MAX_INPUT_LENGTH + 1)
        val prepared = preparedContext(value = overlongInput)
        val expression = TextRegexExpression(
            field = FieldId(value = "purpose"),
            pattern = Regex(pattern = "^(a+)+$")
        )

        assertFalse(actual = expression.evaluate(context = prepared))
    }

    private fun preparedContext(value: String): PreparedRuleContext {
        return PreparedRuleContext.prepare(
            ctx = RuleContext.of("purpose" to value),
            schema = schema
        )
    }
}

