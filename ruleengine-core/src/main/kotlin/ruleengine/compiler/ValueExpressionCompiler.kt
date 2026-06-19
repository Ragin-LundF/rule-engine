package ruleengine.compiler

import ruleengine.core.domain.FieldId
import ruleengine.core.domain.FieldSchema
import ruleengine.core.errors.CompilationException
import ruleengine.dsl.ast.ArithmeticValueAst
import ruleengine.dsl.ast.FieldAccessAst
import ruleengine.dsl.ast.FieldSegmentAst
import ruleengine.dsl.ast.FilterSegmentAst
import ruleengine.dsl.ast.FunctionCallValueAst
import ruleengine.dsl.ast.LiteralValueAst
import ruleengine.dsl.ast.NumberLiteral
import ruleengine.dsl.ast.StringLiteral
import ruleengine.dsl.ast.ValueExpressionAst
import ruleengine.evaluator.compiled.ArithmeticCompiledValueExpression
import ruleengine.evaluator.compiled.CompiledValueExpression
import ruleengine.evaluator.compiled.EvaluationCost
import ruleengine.evaluator.compiled.FieldAccessCompiledValueExpression
import ruleengine.evaluator.compiled.LiteralCompiledValueExpression
import ruleengine.evaluator.compiled.NumberExpressionValue
import ruleengine.evaluator.compiled.TextExpressionValue
import java.math.BigDecimal

internal object ValueExpressionCompiler {
    fun compile(expr: ValueExpressionAst, schema: FieldSchema): CompiledValueExpression {
        return when (expr) {
            is LiteralValueAst -> compileLiteral(literal = expr)
            is FieldAccessAst -> compileFieldAccess(expr = expr, schema = schema)
            is ArithmeticValueAst -> compileArithmetic(expr = expr, schema = schema)
            is FunctionCallValueAst -> throw CompilationException(
                ruleId = null,
                details = "Function calls in value expressions are not yet supported in this iteration"
            )
        }
    }

    private fun compileLiteral(literal: LiteralValueAst): CompiledValueExpression {
        val value = when (val lit = literal.literal) {
            is NumberLiteral -> NumberExpressionValue(value = BigDecimal(lit.value))
            is StringLiteral -> TextExpressionValue(value = lit.value)
            else -> throw CompilationException(
                ruleId = null,
                details = "Unsupported literal type: ${literal.literal::class.simpleName}"
            )
        }
        return LiteralCompiledValueExpression(value = value)
    }

    private fun compileFieldAccess(expr: FieldAccessAst, schema: FieldSchema): CompiledValueExpression {
        if (expr.path.any { it is FilterSegmentAst }) {
            throw CompilationException(
                ruleId = null,
                details = "Filter segments in field paths are not yet supported in this iteration"
            )
        }
        if (expr.path.size != 1 || expr.path[0] !is FieldSegmentAst) {
            throw CompilationException(
                ruleId = null,
                details = "Multi-segment field paths are not yet supported in this iteration"
            )
        }
        val name = (expr.path[0] as FieldSegmentAst).name
        val resolvedId = resolveIdentifier(identifier = name, schema = schema)
        val fieldId = FieldId(value = resolvedId)
        if (schema.fields[fieldId] == null) {
            throw CompilationException(
                ruleId = null,
                details = "Unknown field '$name'"
            )
        }
        return FieldAccessCompiledValueExpression(fieldId = fieldId)
    }

    private fun compileArithmetic(expr: ArithmeticValueAst, schema: FieldSchema): CompiledValueExpression {
        val left = compile(expr = expr.left, schema = schema)
        val right = compile(expr = expr.right, schema = schema)
        val cost = maxOf(left.cost, right.cost)
        return ArithmeticCompiledValueExpression(
            left = left,
            operator = expr.operator,
            right = right,
            cost = cost
        )
    }

    private fun resolveIdentifier(identifier: String, schema: FieldSchema): String {
        val fieldId = FieldId(value = identifier)
        if (schema.fields.containsKey(fieldId)) {
            return identifier
        }
        for ((id, definition) in schema.fields) {
            if (definition.alias == identifier) {
                return id.value
            }
        }
        return identifier
    }
}
