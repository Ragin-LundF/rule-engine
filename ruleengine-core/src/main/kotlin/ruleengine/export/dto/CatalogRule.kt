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
 * @param outcomes The actions the rule produces when [condition] holds, in declaration order.
 * @param publishes Names of the variables the rule publishes with `set`, in declaration order. A
 *   variable is not an outcome — it is read by the rules that follow — so it is listed separately.
 * @param elseOutcomes The actions the rule's `else` block produces when [condition] does not hold.
 *   Empty for a rule that declares no `else`, which is what a renderer checks before writing an
 *   "otherwise" section at all.
 * @param elsePublishes Names of the variables the `else` block publishes with `set`.
 * @param stopsOnThen True when a match ends the run: the rules listed after this one are not evaluated.
 *   A reader of the overview has to know that, or the rules below look like they still apply.
 * @param stopsOnElse True when the `else` block ends the run.
 */
data class CatalogRule(
    val id: String,
    val description: String?,
    val condition: PlainCondition,
    val technicalCondition: String,
    val outcomes: List<CatalogOutcome>,
    val publishes: List<String> = emptyList(),
    val elseOutcomes: List<CatalogOutcome> = emptyList(),
    val elsePublishes: List<String> = emptyList(),
    val stopsOnThen: Boolean = false,
    val stopsOnElse: Boolean = false,
)
