package ui.workbench.model.mode
/**
 * Center-panel modes available inside the [AppArea.SCHEMA] area.
 */
enum class SchemaMode {
    VISUAL,
    YAML,
}

/**
 * What the mode is called in the tab strip.
 *
 * Here rather than beside the tabs so that all four areas name the same two ideas with the same two
 * words: the model-editing surface is **Visual** and the file's text is **Code**. The name used to live
 * next to each area's own tab composable, which is how the app ended up offering Builder / Code,
 * Visual / YAML and Builder / YAML for what is, in every area, one pair.
 *
 * "Code" rather than "YAML" although this file is YAML: the Rules area's text is the rule DSL, and a
 * vocabulary that only works for three of the four areas is the problem this replaces.
 */
val SchemaMode.displayName: String
    get() {
        return when (this) {
            SchemaMode.VISUAL -> "Visual"
            SchemaMode.YAML -> "Code"
        }
    }

/**
 * The glyph the tab shows, and all it shows when the header runs out of room.
 *
 * The same two glyphs in every area, for the same reason the names are the same two words.
 */
val SchemaMode.icon: String
    get() {
        return when (this) {
            SchemaMode.VISUAL -> "⊞"
            SchemaMode.YAML -> "{ }"
        }
    }
