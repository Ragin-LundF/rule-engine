package ruleengine.export.markdown

import ruleengine.export.CatalogText
import ruleengine.export.dto.CatalogOutcome
import ruleengine.export.dto.CatalogRule
import ruleengine.export.dto.CatalogRuleFile
import ruleengine.export.dto.RuleCatalog

/**
 * Renders a [RuleCatalog] as a Markdown document for a wiki.
 *
 * Plain CommonMark only — headings, tables, lists, inline code. No raw HTML, no footnotes, no
 * definition lists: the target is whatever wiki the customer already runs, and Confluence, GitHub
 * and Azure DevOps agree on little beyond that core.
 *
 * The output is a pure function of the catalog, so regenerating an unchanged rule set produces a
 * byte-identical page and the wiki shows no edit.
 */
object MarkdownCatalogRenderer {

    private const val INDENT = "  "

    fun render(catalog: RuleCatalog): String {
        val out = StringBuilder()

        appendHeader(out = out, catalog = catalog)
        appendIndex(out = out, catalog = catalog)
        appendOutcomes(out = out, catalog = catalog)
        catalog.files.forEach { file -> appendFile(out = out, file = file) }

        return out.toString()
    }

    // ── header ────────────────────────────────────────────────────────────────

    private fun appendHeader(out: StringBuilder, catalog: RuleCatalog) {
        out.append("# Rule overview — ").append(catalog.projectName ?: catalog.entryId ?: "rules")
            .append("\n\n")

        val facts = buildList {
            catalog.entryId?.let { entry -> add("Entry `$entry`") }
            add(CatalogText.count(n = catalog.rules.size, singular = "rule"))
            add(CatalogText.count(n = catalog.files.size, singular = "rule file"))
            catalog.schemaPath?.let { path -> add("Input contract `$path`") }
        }
        out.append("_").append(facts.joinToString(separator = " · ")).append("_\n\n")

        // Stated once, at the top: without it a reader assumes the first matching rule wins, which is
        // how most rule engines they have met behave, and every "but then why does rule 7 fire too?"
        // question follows from that assumption. The order sentence is not decoration either — it is
        // what makes the `set` and `stop` notes below mean anything.
        out.append(
            "Every rule is checked against every record, and each one that matches contributes its own " +
                "outcome — a later rule never overrides an earlier one. Rules are evaluated **in the " +
                "order they are listed below**, which is the order the engine uses: rule-file order, " +
                "then the order the rules appear inside each file.\n\n"
        )

        // Same reasoning as the variables note below: state the caveat only for a rule set it applies to.
        if (catalog.rules.any { rule -> rule.elseOutcomes.isNotEmpty() }) {
            out.append(
                "Some rules also say what happens when they do *not* match. Those rules contribute an " +
                    "outcome either way — the one listed under \"Then\" when the rule matches, the one " +
                    "under \"Otherwise\" when it does not.\n\n"
            )
        }

        // Only when the rule set actually uses them: a reader of a rule set without variables should
        // not have to hold a caveat that never applies to what they are reading.
        if (catalog.rules.any { rule -> rule.publishes.isNotEmpty() || rule.elsePublishes.isNotEmpty() }) {
            out.append(
                "Some rules publish a named value that the rules after them read. Those rules are " +
                    "order-dependent: the value only reaches a rule listed later, and only if the " +
                    "rule that publishes it matched.\n\n"
            )
        }

        // Placed last of the three notes because it is the strongest claim on the reader: it changes
        // whether the rules further down apply at all.
        if (catalog.rules.any { rule -> rule.stopsOnThen || rule.stopsOnElse }) {
            out.append(
                "Some rules **end the run**. Where a rule says so, the rules listed after it are not " +
                    "evaluated at all for that record — so a rule further down does not apply, whether " +
                    "or not it would have matched.\n\n"
            )
        }

        out.append("Each rule below has a permanent identifier. Quote it when asking for a change, ")
        out.append("so it is unambiguous which rule is meant.\n\n")
    }

    // ── index ─────────────────────────────────────────────────────────────────

    private fun appendIndex(out: StringBuilder, catalog: RuleCatalog) {
        if (catalog.rules.isEmpty()) {
            out.append("No rules are defined in this entry.\n")
            return
        }

        out.append("## At a glance\n\n")
        out.append("| Rule | What it does | Outcome |\n")
        out.append("|---|---|---|\n")

        catalog.rules.forEach { rule ->
            val link = "[`${rule.id}`](#${anchor(text = rule.id)})"
            out.append("| ").append(link)
                .append(" | ").append(cell(text = summaryOf(rule = rule)))
                .append(" | ").append(cell(text = outcomeSummary(rule = rule)))
                .append(" |\n")
        }
        out.append("\n")
    }

    /** The description when the author wrote one; otherwise the condition, so no row is ever blank. */
    private fun summaryOf(rule: CatalogRule): String {
        return rule.description ?: CatalogText.flatten(condition = rule.condition)
    }

    /**
     * The index cell for a rule's outputs.
     *
     * An `else` outcome is prefixed rather than listed as a peer: in one glance cell, `label:low` next
     * to `label:high` would read as two outcomes the rule produces together instead of one or the other.
     */
    private fun outcomeSummary(rule: CatalogRule): String {
        if (rule.outcomes.isEmpty() && rule.elseOutcomes.isEmpty()) {
            return "—"
        }

        val then = rule.outcomes.joinToString(separator = ", ") { outcome ->
            "`${CatalogText.label(outcome = outcome)}`"
        }
        if (rule.elseOutcomes.isEmpty()) {
            return then
        }

        val otherwise = rule.elseOutcomes.joinToString(separator = ", ") { outcome ->
            "`${CatalogText.label(outcome = outcome)}`"
        }
        return listOf(then, "otherwise $otherwise").filter { part -> part.isNotEmpty() }
            .joinToString(separator = "; ")
    }

    // ── outcome summary ───────────────────────────────────────────────────────

    private fun appendOutcomes(out: StringBuilder, catalog: RuleCatalog) {
        val byOutcome = catalog.rulesByOutcome()
        if (byOutcome.isEmpty()) {
            return
        }

        out.append("## Outcomes this rule set can produce\n\n")
        out.append(
            "One record can receive several outcomes at once — one from every rule that matches it.\n\n"
        )
        // The action is its own column rather than folded into the value: it is what distinguishes a
        // decision from the reason given for it, and a reader scanning the table needs that split.
        out.append("| Output | Value | Produced by |\n")
        out.append("|---|---|---|\n")

        byOutcome.forEach { (outcome, rules) ->
            val producers = rules.joinToString(separator = ", ") { rule ->
                "[`${rule.id}`](#${anchor(text = rule.id)})"
            }
            val value = outcome.arguments.joinToString(separator = ", ").ifEmpty { "—" }
            out.append("| `").append(cell(text = outcome.action)).append("`")
                .append(" | `").append(cell(text = value)).append("`")
                .append(" | ").append(producers).append(" |\n")
        }
        out.append("\n")
    }

    // ── one rule file ─────────────────────────────────────────────────────────

    private fun appendFile(out: StringBuilder, file: CatalogRuleFile) {
        out.append("## ").append(file.relativePath).append("\n\n")

        if (file.rules.isEmpty()) {
            out.append("_This file defines no rules._\n\n")
            return
        }

        file.rules.forEach { rule -> appendRule(out = out, rule = rule) }
    }

    private fun appendRule(out: StringBuilder, rule: CatalogRule) {
        // The id is the heading, because it is also the anchor the index links to and the handle a
        // reviewer quotes. A prose title would read better but could not do either.
        out.append("### ").append(rule.id).append("\n\n")

        rule.description?.let { description -> out.append(description).append("\n\n") }

        out.append("**").append(CatalogText.intro(condition = rule.condition)).append(":**\n\n")
        CatalogText.walk(condition = rule.condition, depth = 0, unwrapRoot = true) { text, depth ->
            out.append(INDENT.repeat(n = depth)).append("- ").append(text).append("\n")
        }
        out.append("\n")

        appendBranch(
            out = out,
            label = "Then",
            publishesLabel = "Publishes for later rules",
            outcomes = rule.outcomes,
            publishes = rule.publishes,
            stops = rule.stopsOnThen,
        )
        // Written only when the rule declares an `else` block, so a reader is never told what happens
        // "otherwise" for a rule where the answer is nothing.
        appendBranch(
            out = out,
            label = "Otherwise",
            publishesLabel = "Publishes for later rules otherwise",
            outcomes = rule.elseOutcomes,
            publishes = rule.elsePublishes,
            stops = rule.stopsOnElse,
        )

        out.append("In the rule language: `").append(rule.technicalCondition).append("`\n\n")
    }

    @Suppress("LongParameterList")
    private fun appendBranch(
        out: StringBuilder,
        label: String,
        publishesLabel: String,
        outcomes: List<CatalogOutcome>,
        publishes: List<String>,
        stops: Boolean,
    ) {
        if (publishes.isNotEmpty()) {
            val names = publishes.joinToString(separator = ", ") { name -> "`$name`" }
            out.append("**").append(publishesLabel).append(":** ").append(names).append("\n\n")
        }

        if (outcomes.isNotEmpty()) {
            val text = outcomes.joinToString(separator = ", ") { outcome ->
                "`${CatalogText.label(outcome = outcome)}`"
            }
            out.append("**").append(label).append(":** ").append(text).append("\n\n")
        }

        // Stated per branch, because a rule can halt on one verdict and carry on with the other. A
        // reader who misses this reads every rule below as still applying.
        if (stops) {
            out.append("**Stops here").append(if (label == "Then") "" else " (otherwise)")
                .append(":** no rule listed after this one is evaluated.\n\n")
        }
    }

    // ── shared formatting ─────────────────────────────────────────────────────

    /**
     * Makes [text] safe inside a table cell.
     *
     * A `|` would end the cell and shift every column after it, and a line break would end the row,
     * so both have to go. Backticks are left alone: a rule id or an outcome name cannot contain one.
     */
    private fun cell(text: String): String {
        return text
            .replace(oldValue = "|", newValue = "\\|")
            .replace(regex = Regex(pattern = "\\s*\\R\\s*"), replacement = " ")
            .trim()
    }

    /**
     * The anchor a wiki generates for a `###` heading: lowercased, spaces to hyphens, punctuation
     * dropped. Rule ids are normally already in that shape, so this only matters for the ones that
     * are not.
     */
    private fun anchor(text: String): String {
        return text
            .lowercase()
            .replace(regex = Regex(pattern = "[^a-z0-9\\s-]"), replacement = "")
            .trim()
            .replace(regex = Regex(pattern = "\\s+"), replacement = "-")
    }
}
