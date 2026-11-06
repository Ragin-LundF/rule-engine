package ruleengine.dsl.parser

import ruleengine.dsl.ast.literal.BooleanLiteral
import ruleengine.dsl.ast.value.FunctionCallValueAst
import ruleengine.dsl.ast.value.LiteralValueAst
import ruleengine.dsl.ast.value.ValueExpressionAst
import ruleengine.evaluator.compiled.AggregateFunctionName
import ruleengine.evaluator.compiled.DslFunctions
import ruleengine.evaluator.compiled.FunctionResultKind

/** Which calls answer true or false, so [Parser] may read one on its own as a condition. */
internal object BooleanCalls {

    /**
     * A call that already answers true or false, so it may stand where a condition is expected.
     *
     * Restricted to functions declared to return a boolean. Accepting any call would turn a
     * misplaced `count(orders)` into `count(orders) == true`, reported as a type mismatch rather
     * than as the missing comparison it is.
     */
    fun isBooleanCall(expr: ValueExpressionAst): Boolean {
        if (expr !is FunctionCallValueAst) {
            return false
        }
        // `default(value, fallback)` has the kind of its value, and the fallback must match.
        if (AggregateFunctionName.fromName(name = expr.name) == AggregateFunctionName.DEFAULT) {
            return expr.arguments.any { argument ->
                (argument as? LiteralValueAst)?.literal is BooleanLiteral || isBooleanCall(expr = argument)
            }
        }
        return DslFunctions.resultKindOf(name = expr.name) == FunctionResultKind.BOOLEAN
    }
}
