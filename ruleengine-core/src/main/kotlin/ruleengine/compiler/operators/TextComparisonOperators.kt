package ruleengine.compiler.operators

import ruleengine.core.domain.FieldDefinition
import ruleengine.core.domain.FieldId
import ruleengine.core.errors.CompilationException
import ruleengine.core.normalizer.NormalizerRegistry
import ruleengine.dsl.ast.ConditionAst
import ruleengine.dsl.ast.StringLiteral
import ruleengine.evaluator.compiled.CompiledExpression
import ruleengine.evaluator.compiled.TextContainsExpression
import ruleengine.evaluator.compiled.TextEndsWithExpression
import ruleengine.evaluator.compiled.TextEqualsExpression
import ruleengine.evaluator.compiled.TextStartsWithExpression

object TextComparisonOperators {
    @Suppress("LongParameterList")
    fun compile(
        ruleId: String?,
        op: String,
        cond: ConditionAst,
        fieldId: FieldId,
        def: FieldDefinition,
        registry: NormalizerRegistry
    ): CompiledExpression {
        val literal = cond.value as? StringLiteral ?: throw CompilationException(
            ruleId = ruleId,
            details = "Expected string literal for text field '${cond.field}'"
        )
        var stringLiteral = literal.value
        for (normalizer in def.normalizers) {
            stringLiteral = registry.get(id = normalizer).normalize(value = stringLiteral)
        }
        val expected = stringLiteral

        return when (op) {
            "equals" -> TextEqualsExpression(
                field = fieldId,
                expectedNormalized = expected,
                ignoreCase = cond.ignoreCase
            )

            "contains" -> TextContainsExpression(
                field = fieldId,
                expectedNormalized = expected,
                ignoreCase = cond.ignoreCase
            )

            "startsWith" -> TextStartsWithExpression(
                field = fieldId,
                expectedNormalized = expected,
                ignoreCase = cond.ignoreCase
            )

            "endsWith" -> TextEndsWithExpression(
                field = fieldId,
                expectedNormalized = expected,
                ignoreCase = cond.ignoreCase
            )

            else -> throw CompilationException(ruleId = ruleId, details = "Unsupported operator '$op' for text field")
        }
    }
}


