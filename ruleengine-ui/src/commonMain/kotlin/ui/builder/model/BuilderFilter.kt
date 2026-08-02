package ui.builder.model


/**
 * A single `[left op right]` restriction applied to the step it belongs to.
 *
 * Both sides are [BuilderOperand]s, the same shape a comparison row uses one level up. A filter *is*
 * a comparison — the engine compiles it to the same `ComparisonCompiledExpression` — so holding its
 * left side as a plain field name is what used to lock `orders[count(items) > 2]` out of the Builder.
 *
 * The three shapes a right-hand side can take fall out of the operand kinds rather than needing
 * fields of their own: a plain literal is a [BuilderOperand.Literal], a written-out list is a
 * [BuilderOperand.ListLiteral], and the name of another field or a variable —
 * `[customerId in priorityCustomerIds]`, which must be emitted unquoted or it becomes a text
 * comparison that never matches — is a [BuilderOperand.FieldRef].
 */
data class BuilderFilter(
    val left: BuilderOperand,
    val operator: String,
    val right: BuilderOperand,
)

/**
 * The common `field op literal` restriction, which is what most call sites build.
 *
 * [value] is treated as numeric when it reads as a number, matching how a comparison row's literal
 * side decides — a filter on a price must not quote its bound.
 */
fun filter(field: String, operator: String, value: String): BuilderFilter = BuilderFilter(
    left = pathOperand(dotted = field),
    operator = operator,
    right = BuilderOperand.Literal(text = value, numeric = value.trim().toDoubleOrNull() != null),
)

/**
 * True once the left side names something, i.e. the restriction is worth generating.
 *
 * A half-built row must not turn into `[ == "x"]`, which does not parse — so it stays visible in the
 * drawer and contributes nothing to the DSL until the author names a member. Anything other than a
 * bare path — an aggregate, arithmetic, a call — always counts.
 */
val BuilderFilter.namesAField: Boolean
    get() = (left as? BuilderOperand.FieldRef)?.path?.any { step -> step.name.isNotBlank() } ?: true
