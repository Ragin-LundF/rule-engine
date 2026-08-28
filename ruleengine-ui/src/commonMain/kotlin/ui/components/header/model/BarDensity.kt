package ui.components.header.model

/**
 * How much room a bar has, and therefore how much of itself it can show.
 *
 * Three steps rather than a continuous width because the decisions are discrete: a label is either
 * drawn or it is not. The thresholds live in `densityFor`, and they are measured against the *panel*
 * the bar sits in, not the window — the centre panel loses width to the icon rail and the Inspector,
 * so a window-wide measure would keep promising room the header does not have.
 */
enum class BarDensity {
    /** Everything, with labels. */
    FULL,

    /** Secondary actions drop to their icons; incidental text is dropped. */
    COMPACT,

    /** Tabs drop to their icons too, and the binding chip keeps only its value. */
    MINIMAL,
}
