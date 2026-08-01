package ruleengine.compiler.operators

import ruleengine.core.domain.OperatorNames
import ruleengine.core.domain.dto.field.FieldDefinition
import ruleengine.core.domain.dto.field.FieldId
import ruleengine.core.errors.CompilationException
import ruleengine.core.normalizer.NormalizerRegistry
import ruleengine.dsl.ast.ConditionAst
import ruleengine.dsl.ast.StringLiteral
import ruleengine.evaluator.compiled.CompiledExpression
import ruleengine.evaluator.compiled.text.TextContainsExpression
import ruleengine.evaluator.compiled.text.TextEndsWithExpression
import ruleengine.evaluator.compiled.text.TextEqualsExpression
import ruleengine.evaluator.compiled.text.TextStartsWithExpression

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
        val expected = registry.applyAll(value = literal.value, normalizers = def.normalizers)

        return when (op) {
            OperatorNames.EQUALS -> TextEqualsExpression(
                field = fieldId,
                expectedNormalized = expected,
                ignoreCase = cond.ignoreCase
            )

            OperatorNames.CONTAINS -> TextContainsExpression(
                field = fieldId,
                expectedNormalized = expected,
                ignoreCase = cond.ignoreCase
            )

            OperatorNames.STARTS_WITH -> TextStartsWithExpression(
                field = fieldId,
                expectedNormalized = expected,
                ignoreCase = cond.ignoreCase
            )

            OperatorNames.ENDS_WITH -> TextEndsWithExpression(
                field = fieldId,
                expectedNormalized = expected,
                ignoreCase = cond.ignoreCase
            )

            else -> throw CompilationException(ruleId = ruleId, details = "Unsupported operator '$op' for text field")
        }
    }
}

