package ui.tester

/**
 * Result returned by [RuleSimulationService.simulate].
 *
 * @param outcome Complete outcome of the run, including the per-rule results and their traces.
 */
data class SimulationResult(
    val outcome: SimulationOutcome,
)
