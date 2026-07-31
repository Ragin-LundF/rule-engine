package ui.tester

/**
 * A single condition trace row produced by the evaluator.
 *
 * @param label   Human-readable description of the condition (e.g. "purpose contains rent").
 * @param result  Whether the condition evaluated to true.
 * @param actual  The value actually found, when the evaluator recorded one. Null for the condition
 *   types that do not yet report it, in which case the row renders exactly as it always has.
 */
data class TraceRow(
    val label: String,
    val result: Boolean,
    val actual: String? = null,
)
