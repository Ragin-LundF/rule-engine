package ruleengine.export.dto

/**
 * Everything one manifest entry's rule set says, prepared for export.
 *
 * The single input to every renderer. Anything a document shows is resolved into this first, so a
 * new output format is a new renderer over the same facts rather than another walk of the AST.
 *
 * Deliberately carries no timestamp: it is a pure function of the rule files, which keeps a
 * regenerated wiki page byte-identical when nothing changed. A renderer that wants to date the
 * document takes that from its caller.
 */
data class RuleCatalog(
    val projectName: String?,
    val entryId: String?,
    val schemaPath: String?,
    val files: List<CatalogRuleFile>,
) {
    /** Every rule in the entry, in the order the engine evaluates them: file order, then declaration order. */
    val rules: List<CatalogRule>
        get() = files.flatMap { file -> file.rules }

    /**
     * Rules grouped by the outcome they produce, for the "what can this rule set decide" summary.
     *
     * Keyed by the whole outcome — action *and* argument — not by the argument alone. An action
     * schema usually declares more than one kind of output, and a summary that lists
     * `service:premium` next to `gold-customer-on-express-service` as if they were peers reads as
     * two competing decisions rather than one decision and its reason.
     *
     * Ordered by action, then by first appearance within that action, so every output of one kind
     * stays together while the rules underneath keep their evaluation order.
     *
     * Both branches count. An outcome a rule only produces in its `else` block is one the rule set can
     * still decide, and leaving it out would make the summary claim less than the rules do. A rule that
     * produces the same outcome in both branches is listed once.
     */
    fun rulesByOutcome(): Map<CatalogOutcome, List<CatalogRule>> {
        val grouped = linkedMapOf<CatalogOutcome, MutableList<CatalogRule>>()
        rules.forEach { rule ->
            (rule.outcomes + rule.elseOutcomes + rule.notExistsOutcomes).distinct().forEach { outcome ->
                grouped.getOrPut(key = outcome) { mutableListOf() }.add(element = rule)
            }
        }

        return grouped.entries
            .sortedBy { entry -> entry.key.action }
            .associate { entry -> entry.key to entry.value.toList() }
    }
}
