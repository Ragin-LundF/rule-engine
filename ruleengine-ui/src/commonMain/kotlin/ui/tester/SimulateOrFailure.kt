package ui.tester

import ui.tester.model.SimulationOutcome
import ui.tester.model.SimulationResult

/**
 * Runs [RuleSimulationService.simulate] and turns a thrown failure into a
 * [SimulationOutcome.ValidationFailed] result instead of letting it escape.
 *
 * The service promises never to throw, but the UI must not depend on that promise: a throw inside the
 * coroutine that drives a run cancels it silently, which leaves the Run button stranded on "Running…"
 * and the reason nowhere the user can see it.
 */
fun RuleSimulationService.simulateOrFailure(
    schemaText: String,
    actionsText: String,
    ruleText: String,
    ruleId: String,
    inputJson: String,
): SimulationResult {
    return runCatching {
        simulate(
            schemaText = schemaText,
            actionsText = actionsText,
            ruleText = ruleText,
            ruleId = ruleId,
            inputJson = inputJson,
        )
    }.getOrElse { failure ->
        SimulationResult(
            outcome = SimulationOutcome.ValidationFailed(reason = failure.message ?: failure.toString()),
        )
    }
}
