package ruleengine.evaluator.compiled

import ruleengine.core.domain.FieldId
import ruleengine.evaluator.context.PreparedRuleContext
import ruleengine.evaluator.context.dto.PreparedBoolean
import ruleengine.evaluator.context.dto.PreparedDecimal
import ruleengine.evaluator.context.dto.PreparedInteger
import ruleengine.evaluator.context.dto.PreparedText
import java.math.BigDecimal

class FieldAccessCompiledValueExpression(
    private val segments: List<CompiledPathSegment>
) : CompiledValueExpression {
    override val cost: EvaluationCost = EvaluationCost.CHEAP

    override fun evaluate(context: PreparedRuleContext): ExpressionValue {
        if (segments.size == 1 && segments[0] is CompiledFieldSegment) {
            val fieldId = FieldId(value = (segments[0] as CompiledFieldSegment).name)
            return when (val prepared = context.get(field = fieldId)) {
                is PreparedInteger -> NumberExpressionValue(value = BigDecimal(prepared.value))
                is PreparedDecimal -> NumberExpressionValue(value = prepared.value)
                is PreparedText -> TextExpressionValue(value = prepared.normalized)
                is PreparedBoolean -> BooleanExpressionValue(value = prepared.value)
                else -> {
                    val raw = context.rawContext.getRaw(fieldPath = listOf(fieldId.value))
                    rawToExpressionValue(raw = raw)
                }
            }
        }
        val rootName = (segments[0] as? CompiledFieldSegment)?.name ?: return MissingExpressionValue
        val rootRaw = context.rawContext.getRaw(fieldPath = listOf(rootName)) ?: return MissingExpressionValue
        val rootList = when (rootRaw) {
            is Collection<*> -> rootRaw.toList()
            else -> listOf(rootRaw)
        }
        return resolveSegments(current = rootList, segments = segments, index = 1, context = context)
    }

    private fun resolveSegments(
        current: List<Any?>,
        segments: List<CompiledPathSegment>,
        index: Int,
        context: PreparedRuleContext
    ): ExpressionValue {
        if (index >= segments.size) {
            val values = current.mapNotNull {
                rawToExpressionValue(raw = it).takeIf { v -> v !is MissingExpressionValue }
            }
            return when {
                values.isEmpty() -> MissingExpressionValue
                values.size == 1 -> values[0]
                else -> ArrayExpressionValue(values = values)
            }
        }
        return when (val segment = segments[index]) {
            is CompiledFieldSegment -> {
                val projected = current.flatMap { element ->
                    when (element) {
                        is Map<*, *> -> {
                            val v = element[segment.name]
                            when (v) {
                                is Collection<*> -> v.toList()
                                null -> emptyList()
                                else -> listOf(v)
                            }
                        }

                        else -> emptyList()
                    }
                }
                resolveSegments(current = projected, segments = segments, index = index + 1, context = context)
            }

            is CompiledFilterSegment -> {
                val filtered = current.filter { element ->
                    if (element !is Map<*, *>) {
                        return@filter false
                    }
                    val childContext = context.child(element = element)
                    segment.expression.evaluate(context = childContext, trace = null)
                }
                resolveSegments(current = filtered, segments = segments, index = index + 1, context = context)
            }
        }
    }

    private fun rawToExpressionValue(raw: Any?): ExpressionValue {
        return when (raw) {
            null -> MissingExpressionValue
            is Number -> NumberExpressionValue(value = BigDecimal(raw.toString()))
            is String -> TextExpressionValue(value = raw)
            is Boolean -> BooleanExpressionValue(value = raw)
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
