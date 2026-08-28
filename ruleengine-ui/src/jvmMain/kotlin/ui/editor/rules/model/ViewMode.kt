package ui.editor.rules.model

/** The editor views the centre panel can show for a rule. */
enum class ViewMode {
    BUILDER,

    /** The board canvas. Has no tab of its own; it is the Visual tab drawn the other way — see `RuleMode.BOARD`. */
    BOARD,
    CODE,
    DIAGRAM,
    TEST,
    TABLE,
}
