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

    /**
     * Membership of the left operand in the collection, string set or list variable on the right.
     *
     * The mirror image of [CONTAINS], and it exists separately because the source is named rather
     * than written out: `customerId in priorityCustomerIds` asks a question about a set the document
     * carries, which `contains` cannot express with the operands in that order.
     *
     * A literal list — `country in ["de", "at"]` — does **not** produce this. That spelling stays on
     * the legacy [ConditionAst] path, the only one that enforces the field's declared `operators:`
     * list and normalizes each item of the list.
     */
    IN,
}
