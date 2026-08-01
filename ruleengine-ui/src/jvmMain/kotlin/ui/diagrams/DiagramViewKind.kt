package ui.diagrams


/**
 * Which diagram the Diagram mode is currently drawing.
 *
 * These are views over the same rules, not separate editor modes, which is why they live in one
 * toolbar selector instead of in `RuleMode`: a new `RuleMode` has to be threaded through two enums,
 * a hand-written tab strip, five exhaustive `when`s and the file picker, and none of that buys the
 * reader anything here.
 *
 * How many rules each view sees is the existing scope control — the `☰` file picker's "All files"
 * option and the `showAllRules` flag behind it — not a second selector that could disagree with it.
 */
enum class DiagramViewKind {
    /** The original per-rule condition tree. */
    TREE,

    /** The manifest entry as one connected, ordered unit. Always entry-wide. */
    RUN,

    /** Rules grouped by the output they produce. */
    OUTCOMES,

    /** Schema field to rule to outcome. */
    FIELDS,
}
