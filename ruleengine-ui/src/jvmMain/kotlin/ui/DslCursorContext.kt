package ui
/**
 * Describes the editing context at the cursor position inside the rule DSL.
 *
 * @property section          The DSL block the cursor is currently in.
 * @property precedingField   The field name immediately before the cursor (if in WHEN and no operator yet).
 * @property precedingOperator The operator after the preceding field (if no value consumed yet).
 * @property afterAction      The action name on the current THEN line (if no argument consumed yet).
 */
data class DslCursorContext(
    val section: DslSection,
    val precedingField: String? = null,
    val precedingOperator: String? = null,
    val afterAction: String? = null,
)
