package ruleengine.evaluator.compiled

import ruleengine.core.domain.FieldId
import ruleengine.evaluator.context.PreparedRuleContext
import ruleengine.evaluator.context.dto.PreparedDecimal
import ruleengine.evaluator.context.dto.PreparedInteger
import ruleengine.evaluator.context.dto.PreparedText
import java.math.BigDecimal

class FieldAccessCompiledValueExpression(
    private val fieldPath: List<String>
) : CompiledValueExpression {
    override val cost: EvaluationCost = EvaluationCost.CHEAP

    override fun evaluate(context: PreparedRuleContext): ExpressionValue {
        if (fieldPath.size == 1) {
            val fieldId = FieldId(value = fieldPath[0])
            return when (val prepared = context.get(field = fieldId)) {
                is PreparedInteger -> NumberExpressionValue(value = BigDecimal(prepared.value))
                is PreparedDecimal -> NumberExpressionValue(value = prepared.value)
                is PreparedText -> TextExpressionValue(value = prepared.normalized)
                else -> resolveRaw(context = context)
            }
        }
        return resolveRaw(context = context)
    }

    private fun resolveRaw(context: PreparedRuleContext): ExpressionValue {
        val raw = context.rawContext.getRaw(fieldPath = fieldPath) ?: return MissingExpressionValue
        return rawToExpressionValue(raw = raw)
    }

    private fun rawToExpressionValue(raw: Any?): ExpressionValue {
        return when (raw) {
            null -> MissingExpressionValue
            is Number -> NumberExpressionValue(value = BigDecimal(raw.toString()))
            is String -> TextExpressionValue(value = raw)
            is Collection<*> -> {
                val elements = raw.mapNotNull { element ->
                    val v = rawToExpressionValue(raw = element)
                    if (v is MissingExpressionValue) null else v
                }
                ArrayExpressionValue(values = elements)
            }
            is Map<*, *> -> ObjectExpressionValue
            else -> MissingExpressionValue
        }
    }
}
