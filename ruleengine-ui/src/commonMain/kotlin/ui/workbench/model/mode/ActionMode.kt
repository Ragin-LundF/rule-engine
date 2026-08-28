package ui.workbench.model.mode
/**
 * Center-panel modes available inside the [AppArea.ACTIONS] area.
 */
enum class ActionMode {
    VISUAL,
    YAML,
}

/** What the mode is called in the tab strip — see [SchemaMode.displayName] for why these two words. */
val ActionMode.displayName: String
    get() {
        return when (this) {
            ActionMode.VISUAL -> "Visual"
            ActionMode.YAML -> "Code"
        }
    }

/** The glyph the tab shows — see [SchemaMode.icon]. */
val ActionMode.icon: String
    get() {
        return when (this) {
            ActionMode.VISUAL -> "⊞"
            ActionMode.YAML -> "{ }"
        }
    }
