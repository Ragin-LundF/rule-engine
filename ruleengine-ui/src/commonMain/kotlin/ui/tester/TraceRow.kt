package ui.tester

/**
 * A single condition trace row produced by the evaluator.
 *
 * @param label   Human-readable description of the condition (e.g. "purpose contains rent").
 * @param result  Whether the condition evaluated to true.
 */
data class TraceRow(
    val label: String,
    val result: Boolean,
)
