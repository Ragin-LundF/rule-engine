package ui.editor.rules.model

/** The editor views the centre panel can show for a rule. */
enum class ViewMode {
    BUILDER,

    /** The board canvas. Has no tab in `ViewModeToggle` — see `RuleMode.BOARD`. */
    BOARD,
    CODE,
    DIAGRAM,
    TEST,
    TABLE,
}
