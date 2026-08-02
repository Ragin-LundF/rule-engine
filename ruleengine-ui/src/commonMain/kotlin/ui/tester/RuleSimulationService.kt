package ui.tester

import ui.tester.model.SimulationResult

/**
 * Platform-agnostic contract for running a rule simulation.
 *
 * Implementations live in platform source sets (jvmMain, etc.) and call the
 * full core evaluation pipeline without exposing JVM types to commonMain.
 */
interface RuleSimulationService {

    /**
     * Runs the rule identified by [ruleId] against [inputJson], or every rule when [ruleId] is blank.
     *
     * @param schemaText     Raw YAML field-schema text (may be blank).
     * @param actionsText    Raw YAML action-schema text (may be blank).
     * @param ruleText       Full DSL text containing one or more rules.
     * @param ruleId         Id of the rule to evaluate; blank runs all rules in [ruleText].
     * @param inputJson      JSON object string representing the fact context.
     * @param scope          Collection to evaluate once per member, or blank for the whole document.
     *   Mirrors the manifest entry's `scope`: without it the rules of a scoped entry are checked
     *   against the document and every member field reads as unknown.
     * @return               A [SimulationResult] that is always non-null and never throws.
     */
    fun simulate(
        schemaText: String,
        actionsText: String,
        ruleText: String,
        ruleId: String,
        inputJson: String,
        scope: String = "",
    ): SimulationResult
}
