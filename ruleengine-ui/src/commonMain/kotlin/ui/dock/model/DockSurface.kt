package ui.dock.model

/**
 * The places a dock appears, and the only thing that differs between them on a first launch.
 *
 * Five surfaces, four entries. The Builder's outline and board canvases are one rule file with one
 * selection, and whether the dock starts open is a property of the area rather than of the canvas — so
 * switching canvas must not change the dock, and giving them separate identities is the way to get that
 * wrong.
 *
 * [openByDefault] lives here rather than as four literals at the call sites because it is one decision:
 * the Builder's dock is open because seeing the DSL a row generates is how the Builder teaches the
 * language, and the other three are closed because their YAML is reference material that would
 * otherwise take height from the canvas on every launch.
 */
enum class DockSurface(val openByDefault: Boolean) {
    RULES(openByDefault = true),
    SCHEMA(openByDefault = false),
    ACTIONS(openByDefault = false),
    MANIFEST(openByDefault = false),
    ;

    companion object {
        /** The generated-file tab, which every surface has and every surface opens on. */
        const val FILE_TAB_ID: String = "file"
    }
}
