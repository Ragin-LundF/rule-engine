package ui.actions

import ui.actions.model.ActionEditorState
import ui.actions.model.EditableAction
import ui.schema.IssueLevel
import ui.schema.SchemaIssue

/**
 * What is wrong with an action schema, per action.
 *
 * Reuses [SchemaIssue] rather than declaring a parallel type: the canvas row and the dock's Checks tab
 * treat both files the same way, and a second identical record would only make them harder to keep so.
 */
object ActionIssues {

    fun of(state: ActionEditorState): List<SchemaIssue> = buildList {
        val duplicates = state.actions
            .map { action -> action.name.trim() }
            .filter { name -> name.isNotBlank() }
            .groupingBy { name -> name }
            .eachCount()
            .filterValues { count -> count > 1 }
            .keys

        state.actions.forEach { action ->
            addAll(elements = ofAction(action = action))
            if (action.name.trim() in duplicates) {
                add(
                    element = SchemaIssue(
                        level = IssueLevel.ERROR,
                        path = action.name,
                        message = "Declared more than once — a later declaration silently replaces the first.",
                    ),
                )
            }
        }
    }

    /**
     * What is wrong with one action.
     *
     * [emittedBy] is how many loaded rules emit it, when known. An action nothing emits is a note: the
     * schema is the vocabulary, and declaring a word nobody has used yet is not an error.
     */
    fun ofAction(action: EditableAction, emittedBy: Int? = null): List<SchemaIssue> = buildList {
        if (action.name.isBlank()) {
            add(
                element = SchemaIssue(
                    level = IssueLevel.ERROR,
                    path = "",
                    message = "No name, so no rule can emit it.",
                ),
            )
        }

        val unknown = action.argTypes.filterNot { type -> type in KnownActionArgTypes }
        unknown.forEach { type ->
            add(
                element = SchemaIssue(
                    level = IssueLevel.ERROR,
                    path = action.name,
                    message = "`$type` is not an argument type the engine knows, so the schema will not load.",
                ),
            )
        }

        // Not a defect in the schema — the engine is happy — but the Builder can only fill in one
        // argument, so saying so where the declaration is made saves the discovery later.
        if (action.argTypes.size > 1) {
            add(
                element = SchemaIssue(
                    level = IssueLevel.NOTE,
                    path = action.name,
                    message = "${action.argTypes.size} arguments — the Builder fills in one, so a rule " +
                        "emitting this has to be written in the Code view.",
                ),
            )
        }

        if (emittedBy == 0) {
            add(
                element = SchemaIssue(
                    level = IssueLevel.NOTE,
                    path = action.name,
                    message = "No loaded rule emits this action.",
                ),
            )
        }
    }
}
