package ruleengine.evaluator.compiled

/**
 * What a DSL function evaluates to.
 *
 * Declared on the function rather than inferred, because the two stages that need it cannot work it
 * out for themselves: the validator has to know whether `every(...)` may stand where a boolean is
 * expected, and the UI has to catalogue a rule output variable without evaluating anything.
 */
enum class FunctionResultKind {
    NUMERIC,
    BOOLEAN,
    ARRAY
}
