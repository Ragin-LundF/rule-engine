package ruleengine.dsl.ast

/**
 * The operators a [ComparisonExpressionAst] can carry.
 *
 * [CONTAINS] is the odd one out: neither an ordering nor an equality, and the only member written as
 * a word rather than a symbol. It lives here anyway because [ExpressionAst] is sealed — a separate
 * membership node would force a new arm in the validator, the compiler, both renderers, the catalog
 * builder and every walker in the UI module, whereas an enum constant costs four exhaustive `when`
 * blocks, all inside this module.
 */
enum class ComparisonOperatorAst {
    EQ,
    NEQ,
    GT,
    GTE,
    LT,
    LTE,

    /**
     * Membership in a list variable, or a substring of a text value — which one is decided by the
     * left operand's runtime type in
     * [ruleengine.evaluator.compiled.value.ComparisonCompiledExpression].
     *
     * A plain `field contains "literal"` does **not** produce this: the parser keeps that on the
     * legacy [ConditionAst] path, which enforces the field's declared `operators:` list and
     * normalizes the literal.
     */
    CONTAINS,
}
