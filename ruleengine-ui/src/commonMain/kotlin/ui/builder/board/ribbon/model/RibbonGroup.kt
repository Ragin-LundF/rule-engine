package ui.builder.board.ribbon.model

/**
 * One rule file's cards, in the order the file declares them.
 *
 * The grouping is by file rather than one flat run because a file is the unit an author edits and
 * reasons about, and the manifest's own order of files is what decides evaluation order between them.
 *
 * [cardCount] is here rather than left to `cards.size` at the call site because the renderer needs it
 * to *state* the group's width instead of measuring it — see the note on the board's ribbon. Keeping
 * the count on the model makes that a data question rather than a layout one.
 */
data class RibbonGroup(
    val relativePath: String,
    val cards: List<RibbonCard>,
) {
    val cardCount: Int get() = cards.size
}
