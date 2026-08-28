package ui.builder.board.ribbon.model

/**
 * One rule as the ribbon shows it: where it sits in the run, and what it exchanges with its neighbours.
 *
 * Every field is filled for every card, including the empty cases, and that is the point. The demo of
 * this ribbon rendered a `↓reads` line only when a rule had reads, so cards had different heights and
 * different rows in the same place — and the first thing anyone asked was why some rules showed their
 * output and others did not. An absent line reads as "unknown"; a line with a dash in it reads as
 * "none", which is what it means. So the renderer draws four rows always, and [reads] / [sets] being
 * empty is a fact the card states rather than a row it omits.
 */
data class RibbonCard(
    /** 1-based position in the whole run, across files — the order the engine evaluates rules in. */
    val ordinal: Int,
    val ruleId: String,
    /** Variables this rule's conditions read, in first-appearance order, without the `$`. */
    val reads: List<String>,
    /** Variables this rule assigns, in declaration order, without the `$`. */
    val sets: List<String>,
    /** True when any branch of this rule ends the run, so nothing after it is evaluated. */
    val halts: Boolean,
    /** True when the Builder cannot render this rule and the board can only name it. */
    val locked: Boolean,
)
