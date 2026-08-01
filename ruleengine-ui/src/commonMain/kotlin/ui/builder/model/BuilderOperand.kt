package ui.builder.model


/**
 * One side of a comparison in Builder mode — the visual counterpart of the engine's
 * `ValueExpressionAst`.
 *
 * Operand trees are treated as immutable values: an edit replaces the whole operand rather than
 * mutating a node in place. That keeps Compose state to a single `mutableStateOf` per side instead of
 * one observable per nested field.
 *
 * Everything here is string-based because `commonMain` cannot see the JVM-only engine AST; the
 * conversion lives in `RuleAstToBuilderMapper` (AST → operand) and [BuilderToRuleDsl] (operand → text).
 */
sealed interface BuilderOperand {

    /**
     * A field path, e.g. `amount`, `customer.country`, or `orders[status == "paid"].total`.
     *
     * Uses the same [BuilderPathStep] list as [Aggregate] so plain and filtered paths render through
     * one code path at any depth. Use [field] for the common single-segment case.
     */
    data class FieldRef(val path: List<BuilderPathStep>) : BuilderOperand

    /** A literal value. [numeric] decides whether the DSL renders it quoted. */
    data class Literal(val text: String, val numeric: Boolean = false) : BuilderOperand

    /**
     * An aggregate function over a path, e.g. `sum(orders[status == "paid"].items.price)`.
     *
     * [path] holds one entry per path segment, so depth is unbounded — a two-segment and a
     * six-segment path use the same shape, as in [FieldRef].
     */
    data class Aggregate(
        val function: String,
        val path: List<BuilderPathStep>,
    ) : BuilderOperand

    /**
     * An arithmetic chain, e.g. `sum(...) * 0.03`.
     *
     * Kept as a flat term list rather than a tree: the first term's [BuilderTerm.operator] is empty
     * and every later term carries the operator that joins it to the running result. Nested
     * parenthesised sub-expressions are represented by a [Calc] term with [parenthesized] set.
     */
    data class Calc(
        val terms: List<BuilderTerm>,
        val parenthesized: Boolean = false,
    ) : BuilderOperand
}

/** Builds a single-segment, unfiltered field reference. */
fun fieldOperand(name: String): BuilderOperand.FieldRef =
    BuilderOperand.FieldRef(path = listOf(BuilderPathStep(name = name)))

/** Builds a path operand from dotted names, e.g. `orders.items.price`. */
fun pathOperand(dotted: String): BuilderOperand.FieldRef =
    BuilderOperand.FieldRef(path = dotted.split(".").map { BuilderPathStep(name = it) })
