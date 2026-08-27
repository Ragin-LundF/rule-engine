package ruleengine.dsl.ast

/**
 * A comparison whose operands are value expressions.
 *
 * [ignoreCase] is the `ignoreCase` modifier, read by the parser after the right operand exactly as it
 * is for a named-operator condition. It matters most here: a variable and an aggregate *always* take
 * this path, so before it was honoured there was no way to write a case-insensitive comparison
 * against one at all — a normalizer declared on a field cannot reach a value a rule computed.
 */
data class ComparisonExpressionAst(
    val left: ValueExpressionAst,
    val operator: ComparisonOperatorAst,
    val right: ValueExpressionAst,
    val ignoreCase: Boolean = false
) : ExpressionAst
