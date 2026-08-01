package ruleengine.compiler

import ruleengine.core.domain.FieldPathResolver
import ruleengine.core.domain.dto.FieldSchema
import ruleengine.core.errors.CompilationException
import ruleengine.dsl.ast.ArithmeticValueAst
import ruleengine.dsl.ast.BooleanLiteral
import ruleengine.dsl.ast.ExpressionAst
import ruleengine.dsl.ast.FieldAccessAst
import ruleengine.dsl.ast.FieldSegmentAst
import ruleengine.dsl.ast.FilterSegmentAst
import ruleengine.dsl.ast.FunctionCallValueAst
import ruleengine.dsl.ast.LiteralValueAst
import ruleengine.dsl.ast.NumberLiteral
import ruleengine.dsl.ast.StringLiteral
import ruleengine.dsl.ast.ValueExpressionAst
import ruleengine.evaluator.compiled.AggregateFunctionName
import ruleengine.evaluator.compiled.ArithmeticCompiledValueExpression
import ruleengine.evaluator.compiled.BooleanExpressionValue
import ruleengine.evaluator.compiled.CompiledExpression
import ruleengine.evaluator.compiled.CompiledFieldSegment
import ruleengine.evaluator.compiled.CompiledFilterSegment
import ruleengine.evaluator.compiled.CompiledPathSegment
import ruleengine.evaluator.compiled.CompiledValueExpression
import ruleengine.evaluator.compiled.FieldAccessCompiledValueExpression
import ruleengine.evaluator.compiled.FunctionCallCompiledValueExpression
import ruleengine.evaluator.compiled.LiteralCompiledValueExpression
import ruleengine.evaluator.compiled.NumberExpressionValue
import ruleengine.evaluator.compiled.TextExpressionValue
import java.math.BigDecimal

internal object ValueExpressionCompiler {
    fun compile(
        expr: ValueExpressionAst,
        schema: FieldSchema,
        ruleId: String? = null,
        filterCompiler: ((ExpressionAst, FieldSchema) -> CompiledExpression)? = null
    ): CompiledValueExpression {
        return when (expr) {
            is LiteralValueAst -> compileLiteral(literal = expr, ruleId = ruleId)
            is FieldAccessAst -> compileFieldAccess(
                expr = expr,
                schema = schema,
                ruleId = ruleId,
                filterCompiler = filterCompiler
            )

            is ArithmeticValueAst -> compileArithmetic(
                expr = expr,
                schema = schema,
                ruleId = ruleId,
                filterCompiler = filterCompiler
            )

            is FunctionCallValueAst -> compileFunctionCall(
                expr = expr,
                schema = schema,
                ruleId = ruleId,
                filterCompiler = filterCompiler
            )
        }
    }

    private fun compileLiteral(literal: LiteralValueAst, ruleId: String?): CompiledValueExpression {
        val value = when (val lit = literal.literal) {
            is NumberLiteral -> NumberExpressionValue(value = BigDecimal(lit.value))
            is StringLiteral -> TextExpressionValue(value = lit.value)
            is BooleanLiteral -> BooleanExpressionValue(value = lit.value)
            else -> throw CompilationException(
                ruleId = ruleId,
                details = "Unsupported literal type: ${literal.literal::class.simpleName}"
            )
        }
        return LiteralCompiledValueExpression(value = value)
    }

    private fun compileFieldAccess(
        expr: FieldAccessAst,
        schema: FieldSchema,
        ruleId: String?,
        filterCompiler: ((ExpressionAst, FieldSchema) -> CompiledExpression)?
    ): CompiledValueExpression {
        val compiledSegments = mutableListOf<CompiledPathSegment>()
        for ((index, segment) in expr.path.withIndex()) {
            when (segment) {
                is FieldSegmentAst -> {
                    val name = if (index == 0) {
                        FieldPathResolver.resolveName(identifier = segment.name, fields = schema.fields)
                    } else {
                        segment.name
                    }
                    compiledSegments += CompiledFieldSegment(name = name)
                }

                is FilterSegmentAst -> {
                    val compiler = filterCompiler ?: throw CompilationException(
                        ruleId = ruleId,
                        details = "Filter segments in field paths are not supported in this context"
                    )
                    val compiledFilter = compiler(segment.expression, schema)
                    compiledSegments += CompiledFilterSegment(expression = compiledFilter)
                }
            }
        }
        return FieldAccessCompiledValueExpression(segments = compiledSegments)
    }

    private fun compileFunctionCall(
        expr: FunctionCallValueAst,
        schema: FieldSchema,
        ruleId: String?,
        filterCompiler: ((ExpressionAst, FieldSchema) -> CompiledExpression)?
    ): CompiledValueExpression {
        val functionName = AggregateFunctionName.fromName(name = expr.name) ?: throw CompilationException(
            ruleId = ruleId,
            details = "Unknown function '${expr.name}'"
        )
        if (expr.arguments.size != 1) {
            throw CompilationException(
                ruleId = ruleId,
                details = "Function '${expr.name}' requires exactly one argument"
            )
        }
        val compiledArg = compile(
            expr = expr.arguments[0],
            schema = schema,
            ruleId = ruleId,
            filterCompiler = filterCompiler
        )
        return FunctionCallCompiledValueExpression(function = functionName, argument = compiledArg)
    }

    private fun compileArithmetic(
        expr: ArithmeticValueAst,
        schema: FieldSchema,
        ruleId: String?,
        filterCompiler: ((ExpressionAst, FieldSchema) -> CompiledExpression)?
    ): CompiledValueExpression {
        val left = compile(expr = expr.left, schema = schema, ruleId = ruleId, filterCompiler = filterCompiler)
        val right = compile(expr = expr.right, schema = schema, ruleId = ruleId, filterCompiler = filterCompiler)
        val cost = maxOf(left.cost, right.cost)
        return ArithmeticCompiledValueExpression(
            left = left,
            operator = expr.operator,
            right = right,
            cost = cost
        )
    }

}
