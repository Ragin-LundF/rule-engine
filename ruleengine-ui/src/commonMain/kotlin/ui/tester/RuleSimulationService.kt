package ui.tester

/**
 * Platform-agnostic contract for running a rule simulation.
 *
 * Implementations live in platform source sets (jvmMain, etc.) and call the
 * full core evaluation pipeline without exposing JVM types to commonMain.
 */
interface RuleSimulationService {

    /**
     * Runs the rule identified by [ruleId] against [inputJson].
     *
     * @param schemaText     Raw YAML field-schema text (may be blank).
     * @param actionsText    Raw YAML action-schema text (may be blank).
     * @param ruleText       Full DSL text containing one or more rules.
     * @param ruleId         Id of the rule to evaluate (first rule when blank).
     * @param inputJson      JSON object string representing the fact context.
     * @return               A [SimulationResult] that is always non-null and never throws.
     */
    fun simulate(
        schemaText: String,
        actionsText: String,
        ruleText: String,
        ruleId: String,
        inputJson: String,
    ): SimulationResult
}

/**
 * Result returned by [RuleSimulationService.simulate].
 *
 * @param outcome   High-level outcome (matched / not matched / error).
 * @param traceRows Condition trace rows; empty when trace is unavailable.
 */
data class SimulationResult(
    val outcome: SimulationOutcome,
    val traceRows: List<TraceRow> = emptyList(),
)
