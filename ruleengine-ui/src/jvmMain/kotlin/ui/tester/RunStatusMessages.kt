package ui.tester

import ui.editor.rules.model.StatusKind
import ui.tester.model.SimulationOutcome

/** Status-bar text for a finished run, so the verdict is visible even with the panel scrolled away. */
internal fun runStatusMessage(outcome: SimulationOutcome): String {
    return when (outcome) {
        is SimulationOutcome.Completed ->
            "${outcome.matchedCount} of ${outcome.ruleResults.size} rules matched — " +
                "${outcome.actionCount} action(s)"

        is SimulationOutcome.ValidationFailed -> "Test not run: ${outcome.reason}"
        is SimulationOutcome.InvalidJson -> "Test not run: invalid JSON — ${outcome.reason}"
        is SimulationOutcome.Idle -> "Ready"
    }
}

internal fun runStatusKind(outcome: SimulationOutcome): StatusKind {
    return when (outcome) {
        is SimulationOutcome.Completed -> if (outcome.matchedCount > 0) StatusKind.SUCCESS else StatusKind.IDLE
        is SimulationOutcome.ValidationFailed, is SimulationOutcome.InvalidJson -> StatusKind.ERROR
        is SimulationOutcome.Idle -> StatusKind.IDLE
    }
}
