package ruleengine.export.dto

/**
 * The child must not hold — the DSL's `not`.
 *
 * Kept as its own node rather than folded into the child's wording (turning "is" into "is not")
 * because `not` may wrap a whole group, and inverting every leaf inside one changes the meaning:
 * `not (a and b)` is not `not a and not b`.
 */
data class PlainNot(val child: PlainCondition) : PlainCondition
