package ruleengine.evaluator.compiled.value.result

/**
 * A structure reached where a scalar was expected; it has no comparable value of its own.
 *
 * It carries [value] so a collection of objects survives a round trip through a function argument.
 * Slicing, `every` / `any` and keyed joins all need the element itself, and a projection has already
 * thrown away everything except the projected member.
 *
 * Deliberately **not** a `data class`: structural equality would deep-hash a whole input element
 * every time one lands in an array comparison. [ExpressionValues.equalsByValue] already answers
 * `false` for a structure, which is the only equality the DSL exposes.
 */
class ObjectExpressionValue(
    val value: Map<*, *>
) : ExpressionValue
