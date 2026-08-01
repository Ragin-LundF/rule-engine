package ui.diagrams.model

import ruleengine.core.domain.dto.FieldSchema
import ruleengine.dsl.ast.RuleAst

/**
 * Everything the diagram views read, gathered once by the host.
 *
 * A parameter object rather than eight parameters on every view: the views need overlapping but
 * different slices of it, and threading each slice separately turned the host into a wall of
 * argument passing.
 *
 * @param rules The rules currently in scope — the open file, or the whole entry when "All files" is
 *   selected. What the tree, outcome and field views draw.
 * @param sources The entry's rule files parsed one by one. Only the run view needs the per-file
 *   split; empty until the entry has been loaded as a whole.
 * @param entryId Id of the selected manifest entry, or null when a loose rule file is open.
 * @param schemaPath Entry-relative path of the field schema, shown as provenance on the run view.
 * @param actionsPath Entry-relative path of the action schema, if the entry declares one.
 * @param schema The loaded field schema. The field view needs it to name the fields no rule reads.
 * @param entryWide Whether [rules] covers the whole entry or only the open file. The field view must
 *   know: "no rule reads this field" is a claim about the entry, and making it from one file's rules
 *   would report every field the other files read as dead.
 */
data class DiagramData(
    val rules: List<RuleAst>,
    val sources: List<RuleSource> = emptyList(),
    val entryId: String? = null,
    val schemaPath: String? = null,
    val actionsPath: String? = null,
    val schema: FieldSchema? = null,
    val entryWide: Boolean = false,
)
