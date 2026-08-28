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
     * A written-out list, e.g. `["fragile", "liquid"]`.
     *
     * Only ever the right side of a membership test — `in`, `containsAny`, `containsAll` — since
     * every ordering or equality against a whole list evaluates to false. Each item keeps its text
     * and is quoted on the way out unless it reads as a number, the same rule a single [Literal]
     * follows.
     */
    data class ListLiteral(val items: List<String>) : BuilderOperand

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

    /**
     * Any other function call, e.g. `daysBetween(registeredAt, reviewDate)` or
     * `sumByKey("month", sales.amount, refunds.amount)`.
     *
     * Separate from [Aggregate] rather than replacing it: an aggregate is one function over one
     * path, which is what the aggregate picker and its breadcrumb are built around, and keeping it
     * means every rule written before this form renders byte-identically.
     *
     * [args] are operands in their own right, so an argument may itself be a path, a literal, an
     * aggregate, a calculation or another call — `abs(sum(a) - sum(b))` is a call around a
     * calculation around two aggregates.
     */
    data class Call(
        val function: String,
        val args: List<BuilderOperand>,
    ) : BuilderOperand
}

/**
 * The path of an operand that has one, or null.
 *
 * [BuilderOperand.FieldRef] and [BuilderOperand.Aggregate] are the two kinds built around a path, and
 * both use the same [BuilderPathStep] list. Reading them through one accessor is what lets the
 * selection walk address a path segment without caring which of the two it sits under.
 */
val BuilderOperand.pathOrNull: List<BuilderPathStep>?
    get() {
        return when (this) {
            is BuilderOperand.FieldRef -> path
            is BuilderOperand.Aggregate -> path
            else -> null
        }
    }

/**
 * The first path found anywhere in an operand tree, or null.
 *
 * Deeper than [pathOrNull], which looks only at the operand itself. This is what makes switching a
 * side's kind reversible: `abs(sum(invoices.amount) - sum(payments.amount))` switched to Field must
 * come back as `invoices.amount`, not as whatever the schema happens to declare first. Without it one
 * mis-click silently replaced the author's field.
 *
 * Depth-first and left-to-right, so it returns the path the author would read first.
 */
val BuilderOperand.firstPath: List<BuilderPathStep>?
    get() {
        pathOrNull?.let { path -> return path }
        return when (this) {
            is BuilderOperand.Call -> args.firstNotNullOfOrNull { arg -> arg.firstPath }
            is BuilderOperand.Calc -> terms.firstNotNullOfOrNull { term -> term.operand.firstPath }
            else -> null
        }
    }

/** Replaces the path of an operand that has one; returns the operand unchanged when it has none. */
fun BuilderOperand.withPath(path: List<BuilderPathStep>): BuilderOperand {
    return when (this) {
        is BuilderOperand.FieldRef -> copy(path = path)
        is BuilderOperand.Aggregate -> copy(path = path)
        else -> this
    }
}

/** Builds a single-segment, unfiltered field reference. */
fun fieldOperand(name: String): BuilderOperand.FieldRef =
    BuilderOperand.FieldRef(path = listOf(BuilderPathStep(name = name)))

/** Builds a path operand from dotted names, e.g. `orders.items.price`. */
fun pathOperand(dotted: String): BuilderOperand.FieldRef =
    BuilderOperand.FieldRef(path = dotted.split(".").map { BuilderPathStep(name = it) })
