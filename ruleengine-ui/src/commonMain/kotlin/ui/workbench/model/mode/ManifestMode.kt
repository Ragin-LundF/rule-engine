package ui.workbench.model.mode
/**
 * Center-panel modes available inside the [AppArea.MANIFEST] area.
 */
enum class ManifestMode {
    BUILDER,
    YAML,
}

/**
 * What the mode is called in the tab strip — see [SchemaMode.displayName] for why these two words.
 *
 * [ManifestMode.BUILDER] is called *Visual*: the constant keeps its name, because renaming it would put
 * the churn in every file that mentions it for no gain, but the word on screen is the one the other
 * three areas use.
 */
val ManifestMode.displayName: String
    get() {
        return when (this) {
            ManifestMode.BUILDER -> "Visual"
            ManifestMode.YAML -> "Code"
        }
    }

/** The glyph the tab shows — see [SchemaMode.icon]. */
val ManifestMode.icon: String
    get() {
        return when (this) {
            ManifestMode.BUILDER -> "⊞"
            ManifestMode.YAML -> "{ }"
        }
    }
