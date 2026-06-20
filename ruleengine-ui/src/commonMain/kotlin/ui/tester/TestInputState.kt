package ui.tester

/**
 * Outcome of a single rule simulation run.
 */
sealed class SimulationOutcome {
    /** Rule matched — contains the fired actions as display strings. */
    data class Matched(val actions: List<String>) : SimulationOutcome()

    /** Rule did not match. */
    data object NotMatched : SimulationOutcome()

    /** Rule or schema had validation errors — simulation was not attempted. */
    data class ValidationFailed(val reason: String) : SimulationOutcome()

    /** Input JSON could not be parsed. */
    data class InvalidJson(val reason: String) : SimulationOutcome()

    /** Idle — no run has been triggered yet. */
    data object Idle : SimulationOutcome()
}

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

/**
 * Immutable snapshot of the test-panel UI state.
 *
 * @param inputJson   Raw JSON text typed by the user.
 * @param isRunning   True while simulation is in progress.
 * @param outcome     Latest simulation result.
 * @param traceRows   Condition trace rows from the last run (empty when trace unavailable).
 */
data class TestInputState(
    val inputJson: String = "",
    val selectedRuleId: String = "",
    val isRunning: Boolean = false,
    val outcome: SimulationOutcome = SimulationOutcome.Idle,
    val traceRows: List<TraceRow> = emptyList(),
) {
    companion object {
        val Empty = TestInputState()
    }
}
