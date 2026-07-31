package ui.builder

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

/**
 * One path segment plus the filters attached to it — mirrors an engine `FieldSegmentAst` followed by
 * zero or more `FilterSegmentAst`.
 *
 * Multiple [filters] on one step are joined with `and` in the generated DSL.
 */
data class BuilderPathStep(
    val name: String,
    val filters: List<BuilderFilter> = emptyList(),
)

/** A single `[field op value]` filter applied to the step it belongs to. */
data class BuilderFilter(
    val field: String,
    val operator: String,
    val value: String,
)

/** One term of a [BuilderOperand.Calc]; [operator] is empty for the first term. */
data class BuilderTerm(
    val operator: String,
    val operand: BuilderOperand,
)

/** True when this operand always yields a number, and so requires a numeric comparison. */
val BuilderOperand.isComputed: Boolean
    get() = this is BuilderOperand.Aggregate || this is BuilderOperand.Calc

/** Builds a single-segment, unfiltered field reference. */
fun fieldOperand(name: String): BuilderOperand.FieldRef =
    BuilderOperand.FieldRef(path = listOf(BuilderPathStep(name = name)))

/** Builds a path operand from dotted names, e.g. `orders.items.price`. */
fun pathOperand(dotted: String): BuilderOperand.FieldRef =
    BuilderOperand.FieldRef(path = dotted.split(".").map { BuilderPathStep(name = it) })

/** Dotted names of a path, ignoring filters — the form used to look fields up in the catalog. */
val List<BuilderPathStep>.names: List<String>
    get() = map { it.name }
