package ui.workbench.model.mode
/**
 * Center-panel modes available inside the [AppArea.RULES] area.
 */
enum class RuleMode {
    BUILDER,

    /**
     * The board: the same rule as the Builder, plus every rule around it in manifest order.
     *
     * A mode rather than a flag inside [BUILDER] so that which canvas the author was last on survives
     * the same way every other mode choice does. It deliberately has no tab of its own: the two
     * canvases show one rule and share one Inspector, so switching between them is a *subordinate*
     * switch beside the tabs — `AreaHeader(subTabs = …)` — and not a row of tabs that change what the
     * centre panel *is*. While the board is showing, the strip still marks the Visual tab.
     */
    BOARD,
    CODE,
    DIAGRAM,
    TEST,
    TABLE,
}

/**
 * What the mode is called in the tab strip — see [SchemaMode.displayName] for why these two words.
 *
 * [RuleMode.BUILDER] is called *Visual* and [RuleMode.CODE] *Code*, which is the same pair the three
 * YAML areas now use. [RuleMode.BOARD] has no tab of its own, so it answers with the name of the tab it
 * is drawn under: whichever canvas is showing, the strip reports the centre panel as *Visual*.
 */
val RuleMode.displayName: String
    get() {
        return when (this) {
            RuleMode.BUILDER, RuleMode.BOARD -> "Visual"
            RuleMode.CODE -> "Code"
            RuleMode.DIAGRAM -> "Diagram"
            RuleMode.TEST -> "Test"
            RuleMode.TABLE -> "Table"
        }
    }

/** The glyph the tab shows — see [SchemaMode.icon]. */
val RuleMode.icon: String
    get() {
        return when (this) {
            RuleMode.BUILDER, RuleMode.BOARD -> "⊞"
            RuleMode.CODE -> "{ }"
            RuleMode.DIAGRAM -> "⬡"
            RuleMode.TEST -> "▷"
            RuleMode.TABLE -> "▦"
        }
    }
