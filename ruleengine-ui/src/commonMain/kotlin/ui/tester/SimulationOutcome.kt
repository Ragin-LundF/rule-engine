package ui.tester

/**
 * Outcome of a simulation run.
 */
sealed class SimulationOutcome {

    /**
     * The run finished. [ruleResults] carries one entry per evaluated rule, in declaration order.
     *
     * There is deliberately no single "matched" verdict: a run over a rule set has as many answers as
     * it has rules, and collapsing them to one match is what hid every other rule's actions.
     */
    data class Completed(val ruleResults: List<RuleResult>) : SimulationOutcome() {
        val matchedCount: Int get() = ruleResults.count { it.matched }
        val actionCount: Int get() = ruleResults.sumOf { it.actions.size }
    }

    /** Rule or schema had validation errors — simulation was not attempted. */
    data class ValidationFailed(val reason: String) : SimulationOutcome()

    /** Input JSON could not be parsed. */
    data class InvalidJson(val reason: String) : SimulationOutcome()

    /** Idle — no run has been triggered yet. */
    data object Idle : SimulationOutcome()
}
