package ruleengine.compiler.operators

import ruleengine.core.domain.dto.FieldDefinition
import ruleengine.core.domain.dto.FieldId
import ruleengine.core.errors.CompilationException
import ruleengine.core.normalizer.NormalizerRegistry
import ruleengine.dsl.ast.ConditionAst
import ruleengine.dsl.ast.ListLiteral
import ruleengine.dsl.ast.StringLiteral
import ruleengine.evaluator.compiled.CompiledExpression
import ruleengine.evaluator.compiled.TextInExpression

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
                val normalized = set.map { setLiterals ->
                    var listLiteral = setLiterals
                    for (normalizer in def.normalizers) {
                        listLiteral = registry.get(id = normalizer).normalize(value = listLiteral)
                    }
                    listLiteral
                }.toSet()
                TextInExpression(field = fieldId, expectedNormalized = normalized, ignoreCase = cond.ignoreCase)
            }

            is StringLiteral -> {
                var literalValue = lit.value
                for (normalizer in def.normalizers) {
                    literalValue = registry.get(id = normalizer).normalize(value = literalValue)
                }
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
