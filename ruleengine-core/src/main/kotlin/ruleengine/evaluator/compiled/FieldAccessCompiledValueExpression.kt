package ruleengine.evaluator.compiled

import ruleengine.core.domain.FieldId
import ruleengine.evaluator.context.PreparedRuleContext
import ruleengine.evaluator.context.dto.PreparedDecimal
import ruleengine.evaluator.context.dto.PreparedInteger
import ruleengine.evaluator.context.dto.PreparedText
import java.math.BigDecimal

class FieldAccessCompiledValueExpression(
    private val fieldId: FieldId
) : CompiledValueExpression {
    override val cost: EvaluationCost = EvaluationCost.CHEAP

    override fun evaluate(context: PreparedRuleContext): ExpressionValue {
        return when (val prepared = context.get(field = fieldId)) {
            is PreparedInteger -> NumberExpressionValue(value = BigDecimal(prepared.value))
            is PreparedDecimal -> NumberExpressionValue(value = prepared.value)
            is PreparedText -> TextExpressionValue(value = prepared.normalized)
            else -> MissingExpressionValue
        }
    }
}
