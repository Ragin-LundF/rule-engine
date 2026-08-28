package ui.builder.formula.model

import ui.builder.model.BuilderConditionNode

/**
 * What came of trying to read a typed expression.
 *
 * A sealed result rather than a nullable node plus a message, so that a caller cannot apply a parse that
 * failed. The formula bar's whole safety property is that a rejected expression leaves the rule exactly
 * as it was — the Builder rewrites the entire rule text on every edit, so an expression applied
 * half-understood would be written to the file.
 */
sealed interface FormulaResult {

    /** The expression read cleanly and became [node]. */
    data class Parsed(val node: BuilderConditionNode) : FormulaResult

    /**
     * The expression could not be read, or could be read but not represented visually.
     *
     * [message] is shown as typed-in feedback, so it is phrased for the author rather than copied
     * verbatim from the parser where a better phrasing exists.
     */
    data class Failed(val message: String) : FormulaResult
}
