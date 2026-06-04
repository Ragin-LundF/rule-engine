package ruleengine.compiler.operators

import ruleengine.core.domain.FieldDefinition
import ruleengine.core.domain.FieldId
import ruleengine.core.errors.CompilationException
import ruleengine.core.normalizer.NormalizerRegistry
import ruleengine.dsl.ast.ConditionAst
import ruleengine.dsl.ast.ListLiteral
import ruleengine.dsl.ast.StringLiteral
import ruleengine.evaluator.compiled.CompiledExpression
import ruleengine.evaluator.compiled.TextContainsExpression
import ruleengine.evaluator.compiled.TextEndsWithExpression
import ruleengine.evaluator.compiled.TextEqualsExpression
import ruleengine.evaluator.compiled.TextInExpression
import ruleengine.evaluator.compiled.TextRegexExpression
import ruleengine.evaluator.compiled.TextStartsWithExpression

object TextRegexOperator {
    fun compile(ruleId: String?, cond: ConditionAst, fieldId: FieldId): CompiledExpression {
        val literal = cond.value as? StringLiteral ?: throw CompilationException(
            ruleId,
            "Operator 'regex' expects string literal pattern for field '${cond.field}'"
        )
        val options = if (cond.ignoreCase) setOf(RegexOption.IGNORE_CASE) else emptySet()
        val pattern = runCatching { Regex(literal.value, options) }.getOrElse { ex ->
            throw CompilationException(ruleId, "Invalid regex pattern '${literal.value}': ${ex.message}")
        }

        return TextRegexExpression(field = fieldId, pattern = pattern)
    }
}

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
                val normalized = set.map { s ->
                    var v = s
                    for (n in def.normalizers) {
                        v = registry.get(n).normalize(v)
                    }
                    v
                }.toSet()
                TextInExpression(field = fieldId, expectedNormalized = normalized, ignoreCase = cond.ignoreCase)
            }

            is StringLiteral -> {
                var v = lit.value
                for (n in def.normalizers) v = registry.get(n).normalize(v)
                TextInExpression(field = fieldId, expectedNormalized = setOf(v), ignoreCase = cond.ignoreCase)
            }

            else -> throw CompilationException(ruleId, "Operator 'in' expects list or string literal for text field")
        }
    }
}

object TextComparisonOperators {
    fun compile(
        ruleId: String?,
        op: String,
        cond: ConditionAst,
        fieldId: FieldId,
        def: FieldDefinition,
        registry: NormalizerRegistry
    ): CompiledExpression {
        val literal = cond.value as? StringLiteral ?: throw CompilationException(
            ruleId,
            "Expected string literal for text field '${cond.field}'"
        )
        var v = literal.value
        for (n in def.normalizers) v = registry.get(n).normalize(v)
        val expected = v

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

            else -> throw CompilationException(ruleId, "Unsupported operator '$op' for text field")
        }
    }
}


