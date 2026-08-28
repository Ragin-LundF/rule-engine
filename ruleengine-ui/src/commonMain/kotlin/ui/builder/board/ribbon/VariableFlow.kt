package ui.builder.board.ribbon

import ui.builder.board.ribbon.model.RibbonCard
import ui.builder.board.ribbon.model.RibbonGroup

/**
 * Who publishes a `$variable` and who reads it — the question the board exists to answer.
 *
 * A variable is the one way one rule reaches another, and it is invisible in the text: `$tier` in a
 * condition says nothing about which rule set it, or whether any rule set it *before this one runs*.
 * Reading a variable no earlier rule assigns is not a syntax error, so nothing tells the author. It
 * evaluates as absent, and the rule quietly does not fire.
 *
 * So the scope rule below is the load-bearing part: **only a rule earlier in the run publishes to a
 * later one.** That is the engine's own rule, and it is why a producer here is always the *last*
 * assignment before the reader rather than just any rule that sets the name.
 */
object VariableFlow {

    /**
     * Which cards take part in [variable]'s flow.
     *
     * [producers] holds every card that assigns it, and [readers] every card that reads it *and could
     * actually see it* — a reader with no earlier producer is in [orphanReaders] instead. Splitting the
     * two is the whole value: a highlight that lit up a reader and a producer that runs after it would
     * draw an arrow backwards through the run and assert a connection that does not exist.
     */
    data class Flow(
        val variable: String,
        val producers: List<Int>,
        val readers: List<Int>,
        val orphanReaders: List<Int>,
    ) {
        /** True when [ordinal]'s card should be lit at all. */
        fun touches(ordinal: Int): Boolean {
            return ordinal in producers || ordinal in readers || ordinal in orphanReaders
        }
    }

    /** The flow of [variable] across [groups], which are already in manifest order. */
    fun of(variable: String, groups: List<RibbonGroup>): Flow {
        val cards = groups.flatMap { group -> group.cards }
        val producers = cards.filter { card -> variable in card.sets }.map { card -> card.ordinal }
        val reading = cards.filter { card -> variable in card.reads }

        // A reader sees the variable only if something assigned it strictly earlier in the run. A rule
        // that both reads and sets the same name reads the *previous* value, so its own assignment does
        // not count as its producer.
        val (visible, orphans) = reading.partition { card ->
            producers.any { producer -> producer < card.ordinal }
        }

        return Flow(
            variable = variable,
            producers = producers,
            readers = visible.map { card -> card.ordinal },
            orphanReaders = orphans.map { card -> card.ordinal },
        )
    }

    /**
     * Every variable a rule reads that nothing before it assigns.
     *
     * This is the board's one genuine warning, and the reason the ribbon is worth the space: the rule
     * parses, validates and runs, and silently never fires. Nothing else in the tool can say it,
     * because nothing else looks at more than one rule at a time.
     */
    fun unresolvedReads(card: RibbonCard, groups: List<RibbonGroup>): List<String> {
        return card.reads.filter { variable ->
            of(variable = variable, groups = groups).orphanReaders.contains(element = card.ordinal)
        }
    }
}
