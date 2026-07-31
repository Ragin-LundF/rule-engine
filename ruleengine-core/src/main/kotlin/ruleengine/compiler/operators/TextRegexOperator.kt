package ruleengine.compiler.operators

import ruleengine.core.domain.dto.FieldId
import ruleengine.core.errors.CompilationException
import ruleengine.dsl.ast.ConditionAst
import ruleengine.dsl.ast.StringLiteral
import ruleengine.evaluator.compiled.CompiledExpression
import ruleengine.evaluator.compiled.TextRegexExpression

object TextRegexOperator {
    fun compile(ruleId: String?, cond: ConditionAst, fieldId: FieldId): CompiledExpression {
        val literal = cond.value as? StringLiteral ?: throw CompilationException(
            ruleId = ruleId,
            details = "Operator 'regex' expects string literal pattern for field '${cond.field}'"
        )
        val options = if (cond.ignoreCase) setOf(RegexOption.IGNORE_CASE) else emptySet()
        val pattern = runCatching { Regex(pattern = literal.value, options = options) }.getOrElse { ex ->
            throw CompilationException(
                ruleId = ruleId,
                details = "Invalid regex pattern '${literal.value}': ${ex.message}"
            )
        }

        return TextRegexExpression(field = fieldId, pattern = pattern)
    }
}
