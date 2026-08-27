package ruleengine.evaluator.compiled

/**
 * A DSL function over the *shape* of a collection rather than over its values.
 *
 * Separate from [AggregateFunctionName] because these cannot be evaluated from an argument value:
 * `every(lineItems[quantity >= 1])` needs the predicate as something it can run per element, and by
 * the time an argument has been reduced to a value the predicate is gone. The compiler therefore
 * takes them apart at the AST, which is why they have no place in the value-call dispatch.
 */
enum class CollectionFunctionName(
    /** The spelling the DSL uses. */
    val dslName: String,
    /** How many arguments the function accepts. */
    val arity: IntRange,
    /** What a call evaluates to, so the validator can type a comparison against it. */
    val resultKind: FunctionResultKind
) {
    /** True when every element satisfies the predicate. Vacuously true for an empty collection. */
    EVERY(dslName = "every", arity = 1..1, resultKind = FunctionResultKind.BOOLEAN),

    /** True when at least one element satisfies the predicate. False for an empty collection. */
    ANY(dslName = "any", arity = 1..1, resultKind = FunctionResultKind.BOOLEAN),

    /**
     * One total per key, joining two or more collections on a shared member: a key literal followed
     * by the sources. Two sources is the smallest join worth writing, hence three arguments.
     */
    SUM_BY_KEY(dslName = "sumByKey", arity = 3..Int.MAX_VALUE, resultKind = FunctionResultKind.ARRAY),

    /**
     * Whether a collection arrived carrying no elements — **present but empty**, which is a different
     * answer from absent.
     *
     * The complement of [AggregateFunctionName.IS_AVAILABLE] rather than its negation. Between them a
     * rule can name all three states a collection can be in:
     *
     * | the record carries | `isAvailable` | `isEmpty` |
     * |---|---|---|
     * | nothing at all | `false` | `false` |
     * | an empty collection | `false` | **`true`** |
     * | one or more elements | `true` | `false` |
     *
     * It belongs here and not with the aggregates because it needs the argument's *shape*: by the
     * time a path has been reduced to a value, an empty collection and an absent one are already the
     * same [ruleengine.evaluator.compiled.value.result.MissingExpressionValue]. `count(x) == 0`
     * cannot stand in for it for exactly that reason — `count` answers `0` for both.
     *
     * Like `isAvailable` it returns a real boolean, never
     * [ruleengine.core.domain.dto.ConditionVerdict.UNKNOWN], so it can guard a rule whose other
     * conditions would be undecidable.
     */
    IS_EMPTY(dslName = "isEmpty", arity = 1..1, resultKind = FunctionResultKind.BOOLEAN);

    companion object {

        /** The function [name] denotes, case-insensitively, or null when it names none. */
        fun fromName(name: String): CollectionFunctionName? {
            return entries.firstOrNull { entry -> entry.dslName.equals(other = name, ignoreCase = true) }
        }

        /** Every function name in the spelling the DSL and diagnostics use. */
        fun dslNames(): List<String> {
            return entries.map { entry -> entry.dslName }
        }
    }
}
