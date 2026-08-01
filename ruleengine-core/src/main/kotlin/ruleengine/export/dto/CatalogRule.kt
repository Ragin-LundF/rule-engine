package ruleengine.export.dto

/**
 * One rule, prepared for an exported overview.
 *
 * Everything a renderer needs is resolved here, so Markdown and Word both read the same values and
 * cannot drift apart in what they claim a rule does.
 *
 * @param id The rule's identifier — the handle a reviewer quotes when asking for a change.
 * @param description The author's `description` clause, or null when the rule has none. The
 *   validator warns about that; the export shows [condition] instead rather than leaving a gap.
 * @param condition The condition restated as sentences.
 * @param technicalCondition The condition in DSL syntax, for a technical reviewer.
 * @param outcomes The actions the rule produces, in declaration order.
 * @param fields The field paths the rule reads, sorted, so a reader can see what data it depends on.
 */
data class CatalogRule(
    val id: String,
    val description: String?,
    val condition: PlainCondition,
    val technicalCondition: String,
    val outcomes: List<CatalogOutcome>,
    val fields: List<String>,
)
