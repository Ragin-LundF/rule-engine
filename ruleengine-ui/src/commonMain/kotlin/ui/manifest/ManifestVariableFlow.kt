package ui.manifest

import ui.builder.board.ribbon.VariableFlow
import ui.builder.board.ribbon.model.RibbonGroup

/** A `$variable` a rule file reads, and whether anything earlier in the run publishes it. */
data class VariableRead(val name: String, val resolved: Boolean)

/** What one rule file contributes to the run's variable flow. */
data class RuleFileFlow(
    val relativePath: String,
    val sets: List<String>,
    val reads: List<VariableRead>,
) {
    val hasUnresolvedRead: Boolean get() = reads.any { read -> !read.resolved }
}

/**
 * The `$variable` flow of an entry, per rule file.
 *
 * **The one thing about a manifest that no single-file view can see**, because it is a property of the
 * *order*: a variable is visible only to the files listed after the one that sets it, so the same two
 * files in the other order can turn a working entry into one where a read never resolves. The rule still
 * parses, still validates and can never fire — which is why this belongs beside the control that changes
 * the order.
 *
 * Built on [VariableFlow] and `RibbonModel.groups`, which the board already uses for exactly this
 * question. A second implementation would be a second answer, and the two would disagree the first time
 * either was changed.
 */
object ManifestVariableFlow {

    /** [groups] are the entry's files in manifest order, from `RibbonModel.groups`. */
    fun of(groups: List<RibbonGroup>): List<RuleFileFlow> {
        val cards = groups.flatMap { group -> group.cards }
        val everyVariable = cards.flatMap { card -> card.reads + card.sets }.distinct()

        // One flow per variable, computed once. Asking per read would recompute the same partition for
        // every card that mentions the name.
        val flows = everyVariable.associateWith { name -> VariableFlow.of(variable = name, groups = groups) }

        return groups.map { group ->
            RuleFileFlow(
                relativePath = group.relativePath,
                sets = group.cards.flatMap { card -> card.sets }.distinct(),
                reads = group.cards
                    .flatMap { card -> card.reads.map { name -> card.ordinal to name } }
                    .distinctBy { (_, name) -> name }
                    .map { (ordinal, name) ->
                        VariableRead(
                            name = name,
                            resolved = flows[name]?.orphanReaders?.contains(element = ordinal) != true,
                        )
                    },
            )
        }
    }

    /**
     * The reads that never resolve, as one sentence each.
     *
     * Phrased as the consequence rather than the condition: "resolves to null on every run" is what the
     * author will observe, and "no earlier producer" is only why.
     */
    fun unresolvedReads(groups: List<RibbonGroup>): List<Pair<String, String>> =
        of(groups = groups)
            .flatMap { flow ->
                flow.reads
                    .filterNot { read -> read.resolved }
                    .map { read ->
                        flow.relativePath to "reads \$${read.name} before any file publishes it, " +
                            "so it resolves to null on every run."
                    }
            }
}
