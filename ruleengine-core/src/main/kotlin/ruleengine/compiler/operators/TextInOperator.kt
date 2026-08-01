package ruleengine.compiler.operators

import ruleengine.core.domain.dto.FieldDefinition
import ruleengine.core.domain.dto.FieldId
import ruleengine.core.errors.CompilationException
import ruleengine.core.normalizer.NormalizerRegistry
import ruleengine.dsl.ast.ConditionAst
import ruleengine.dsl.ast.ListLiteral
import ruleengine.dsl.ast.StringLiteral
import ruleengine.evaluator.compiled.CompiledExpression
import ruleengine.evaluator.compiled.text.TextInExpression

object TextInOperator {
    fun compile(
        ruleId: String?,
        cond: ConditionAst,
        fieldId: FieldId,
        def: FieldDefinition,
        registry: NormalizerRegistry
    ): CompiledExpression {
        return when (val lit = cond.value) {
            is ListLiteral -> {
                val set = lit.items.map {
                    (it as? StringLiteral)?.value ?: throw CompilationException(
                        ruleId,
                        "Expected string items in list"
                    )
                }.toSet()
                val normalized = set.map { item ->
                    registry.applyAll(value = item, normalizers = def.normalizers)
                }.toSet()
                TextInExpression(field = fieldId, expectedNormalized = normalized, ignoreCase = cond.ignoreCase)
            }

            is StringLiteral -> {
                val literalValue = registry.applyAll(value = lit.value, normalizers = def.normalizers)
                TextInExpression(
                    field = fieldId,
                    expectedNormalized = setOf(literalValue),
                    ignoreCase = cond.ignoreCase
                )
            }

            else -> throw CompilationException(
                ruleId = ruleId,
                details = "Operator 'in' expects list or string literal for text field"
            )
        }
    }
}
