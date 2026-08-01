package ui.tester.model

/**
 * Immutable snapshot of the test-panel UI state.
 *
 * The trace is not held here: it belongs to the rule that produced it and travels inside
 * [RuleResult], because a flat trace spanning every rule cannot say which rule a row came from.
 *
 * @param inputJson       Raw JSON text typed or loaded by the user.
 * @param selectedRuleId  Id of the rule to run; blank means all rules.
 * @param isRunning       True while simulation is in progress.
 * @param outcome         Latest simulation result.
 */
data class TestInputState(
    val inputJson: String = "",
    val selectedRuleId: String = "",
    val isRunning: Boolean = false,
    val outcome: SimulationOutcome = SimulationOutcome.Idle,
) {
    companion object {
        val Empty = TestInputState()
    }
}
