package ruleengine.evaluator.compiled

/**
 * A DSL function that reduces or transforms already-evaluated values.
 *
 * Every member here is something `FunctionCallCompiledValueExpression` can evaluate from its
 * arguments alone. A function that needs the *shape* of its argument — a predicate to run per
 * element, or a key and a value read off the same element — does not belong here: by the time an
 * argument has become a value, that shape is gone.
 *
 * The type name is historical; the enum started as the aggregate list and is published API.
 * [isAggregate] separates the true reductions, which is what the visual editor offers in its
 * aggregate picker, from the rest.
 */
enum class AggregateFunctionName(
    /** The spelling the DSL uses, which is not always the enum name lowercased. */
    val dslName: String,
    /** How many arguments the function accepts. */
    val arity: IntRange,
    /** True for a reduction over a collection, i.e. what the Builder's aggregate picker offers. */
    val isAggregate: Boolean = true,
    /** What a call evaluates to, so the validator can type a comparison against it. */
    val resultKind: FunctionResultKind = FunctionResultKind.NUMERIC
) {
    COUNT(dslName = "count", arity = 1..1),
    SUM(dslName = "sum", arity = 1..1),
    SUBTRACT(dslName = "subtract", arity = 1..1),
    AVG(dslName = "avg", arity = 1..1),
    MEDIAN(dslName = "median", arity = 1..1),
    MAX(dslName = "max", arity = 1..1),
    MIN(dslName = "min", arity = 1..1),

    /**
     * Magnitude of a single number. Not an aggregate: over a projected collection it would have to
     * pick one of many values, so it is offered as a calculation rather than in the aggregate picker.
     */
    ABS(dslName = "abs", arity = 1..1, isAggregate = false),

    /** Signed calendar days from the first argument to the second. */
    DAYS_BETWEEN(dslName = "daysBetween", arity = 2..2, isAggregate = false),

    /**
     * Whether its argument has a value at all — the explicit form of the check a `not_exists` branch
     * answers implicitly.
     *
     * Not an aggregate, and the one function whose answer is *about* the data arriving rather than
     * about what it says. That is what makes it usable as a guard: it returns a real boolean, never
     * [ruleengine.core.domain.dto.ConditionVerdict.UNKNOWN], so a rule can test availability without
     * the test itself becoming undecidable.
     *
     * It accepts any argument, including a bare `collection` or `object` path, which no other function
     * does. A path that yields no value is not available, and an absent field, a `null` and an **empty**
     * collection are alike here: the question is whether the record carries the value at all, and an
     * empty list is no more an answer to it than an absent one.
     */
    IS_AVAILABLE(
        dslName = "isAvailable",
        arity = 1..1,
        isAggregate = false,
        resultKind = FunctionResultKind.BOOLEAN,
    );

    companion object {

        /**
         * The function [name] denotes, case-insensitively, or null when it names none.
         *
         * The DSL accepts any casing — `count`, `COUNT` and `Count` are the same function — so every
         * stage that reads a function name off the AST resolves it through here rather than matching
         * on its own lowercased copy of the entry list.
         */
        fun fromName(name: String): AggregateFunctionName? {
            return entries.firstOrNull { entry -> entry.dslName.equals(other = name, ignoreCase = true) }
        }

        /** Every function name in the spelling the DSL and diagnostics use. */
        fun dslNames(): List<String> {
            return entries.map { entry -> entry.dslName }
        }
    }
}
