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
     * the same way every other mode choice does. It deliberately has no tab of its own in
     * `ViewModeToggle`: the two canvases show one rule and share one Inspector, so switching between
     * them belongs on the canvas, beside the thing being switched, not in a row of tabs that change
     * what the centre panel *is*.
     */
    BOARD,
    CODE,
    DIAGRAM,
    TEST,
    TABLE,
}
