package ui.schema

import ruleengine.core.domain.dto.field.isNormalizable
import ruleengine.core.domain.dto.field.isStructure
import ui.schema.model.EditableField
import ui.schema.model.SchemaEditorState

/** How badly a declaration is wrong, which decides how the row and the dock colour it. */
enum class IssueLevel { ERROR, WARNING, NOTE }

/** One thing wrong with one declaration, and the path or name it is about. */
data class SchemaIssue(val level: IssueLevel, val path: String, val message: String)

/**
 * What is wrong with a schema, per field.
 *
 * One source for two readers: the canvas prints a field's first issue under its row, and the dock's
 * Checks tab lists all of them with the row each belongs to. Two implementations would be two answers
 * to the same question, and the row is the one people would trust.
 *
 * These are not the core validator's diagnostics — those carry a file and a line and are reported
 * against the file. These are the mistakes the editor has complete information about on its own, which
 * are also the ones worth catching inline, because they are what a declaration looks like *while it is
 * being written*.
 */
object SchemaIssues {

    /** Everything wrong with [state], in declaration order, parents before members. */
    fun of(state: SchemaEditorState): List<SchemaIssue> = buildList {
        if (state.schemaName.isBlank()) {
            add(
                element = SchemaIssue(
                    level = IssueLevel.WARNING,
                    path = "",
                    message = "The schema has no name, so `schema:` is left out of the file.",
                ),
            )
        }

        val flattened = state.fields.flattenPaths()
        val duplicates = flattened
            .map { (path, _) -> path }
            .groupingBy { path -> path }
            .eachCount()
            .filterValues { count -> count > 1 }
            .keys

        flattened.forEach { (path, field) ->
            addAll(elements = ofField(path = path, field = field))
            if (path in duplicates) {
                add(
                    element = SchemaIssue(
                        level = IssueLevel.ERROR,
                        path = path,
                        message = "Declared more than once — the engine refuses a duplicate path.",
                    ),
                )
            }
        }
    }

    /**
     * What is wrong with one field, most serious first.
     *
     * [readBy] is how many loaded rules read it, when that is known. A field nothing reads is a note
     * rather than a warning: the schema describes the document, not the rules, so it is allowed — it is
     * just usually a rename that happened on one side only.
     */
    fun ofField(path: String, field: EditableField, readBy: Int? = null): List<SchemaIssue> = buildList {
        val type = field.type

        if (field.path.isBlank()) {
            add(
                element = SchemaIssue(
                    level = IssueLevel.ERROR,
                    path = path,
                    message = "No path — the writer drops this field, so it never reaches the file.",
                ),
            )
        }

        strayOperators(field = field).forEach { operator ->
            add(
                element = SchemaIssue(
                    level = IssueLevel.WARNING,
                    path = path,
                    message = "`$operator` is not allowed on ${type.yamlValue} — a rule using it will not compile.",
                ),
            )
        }

        if (!type.isStructure && field.operators.isEmpty()) {
            add(
                element = SchemaIssue(
                    level = IssueLevel.WARNING,
                    path = path,
                    message = "No operators declared, so no rule can compare this field.",
                ),
            )
        }

        if (type.isStructure && field.fields.isEmpty()) {
            add(
                element = SchemaIssue(
                    level = IssueLevel.WARNING,
                    path = path,
                    message = "A ${type.yamlValue} with no members — there is nothing to navigate into.",
                ),
            )
        }

        if (!type.isNormalizable && field.normalizers.isNotEmpty()) {
            add(
                element = SchemaIssue(
                    level = IssueLevel.WARNING,
                    path = path,
                    message = "Normalizers apply to text values only; these are written and ignored.",
                ),
            )
        }

        if (readBy == 0 && !type.isStructure) {
            add(
                element = SchemaIssue(
                    level = IssueLevel.NOTE,
                    path = path,
                    message = "No loaded rule reads this field.",
                ),
            )
        }
    }

    /** The operators [field] declares that its type does not allow. */
    fun strayOperators(field: EditableField): List<String> {
        val allowed = operatorsFor(type = field.type)
        return field.operators.filterNot { operator -> operator in allowed }
    }
}
