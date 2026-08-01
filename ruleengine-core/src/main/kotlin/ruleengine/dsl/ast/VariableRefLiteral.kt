package ruleengine.dsl.ast

/**
 * A variable read used as an action argument, written `$name` in the rule DSL — for example
 * `score $riskScore`.
 *
 * The value-expression counterpart is [VariableRefAst]; this node exists because action arguments
 * are literals rather than value expressions. Distinguished from [ExtractionRefLiteral] by the name
 * not being all digits.
 */
data class VariableRefLiteral(val name: String) : LiteralAst
