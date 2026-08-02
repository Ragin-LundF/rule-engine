package ruleengine.export.docx

import ruleengine.export.CatalogText
import ruleengine.export.dto.CatalogOutcome
import ruleengine.export.dto.CatalogRule
import ruleengine.export.dto.CatalogRuleFile
import ruleengine.export.dto.RuleCatalog
import java.io.ByteArrayOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/**
 * Writes a [RuleCatalog] as a Word document, for handing to a customer.
 *
 * A `.docx` rather than a PDF because it is what a business reviewer can actually work with: Word
 * comments and track-changes are how they already annotate a document, and a PDF is one
 * File → Export away when they want one. It is also a format this module can produce with no
 * dependency at all — see [DocxParts].
 *
 * The document is structured for someone who will not read it end to end: an index of every rule
 * with a link to it, a table of the outcomes the rule set can produce, then one section per rule
 * file. Each rule leads with its description, states its condition as sentences, and shows the exact
 * rule-language condition underneath for whoever has to verify it.
 */
object DocxCatalogWriter {

    /**
     * A fixed timestamp on every ZIP entry.
     *
     * Without it each entry records the moment it was written, so exporting an unchanged rule set
     * twice produces two different files — which makes a diff meaningless and a checksum useless.
     * 1980-01-01 is the earliest instant the ZIP format can represent.
     */
    private const val FIXED_ENTRY_TIME = 315_532_800_000L

    private val INDEX_HEADERS = listOf("Rule", "What it does", "Outcome")

    /** Column widths as percentages of the page. The description column carries the most text. */
    private val INDEX_WIDTHS = listOf(28, 46, 26)

    private val OUTCOME_HEADERS = listOf("Output", "Value", "Produced by")
    private val OUTCOME_WIDTHS = listOf(22, 34, 44)

    /**
     * Renders [catalog] to `.docx` bytes.
     *
     * [generatedOn] is printed under the title when given. It is a parameter rather than a clock
     * read so that the output stays a pure function of its input; the caller decides whether the
     * document is dated.
     */
    fun write(catalog: RuleCatalog, generatedOn: String? = null): ByteArray {
        val parts = linkedMapOf(
            // [Content_Types].xml first: it is what a reader consults to interpret everything else,
            // and some tools give up rather than scan the whole archive for it.
            DocxParts.CONTENT_TYPES_PATH to DocxParts.contentTypes(),
            DocxParts.ROOT_RELS_PATH to DocxParts.rootRelationships(),
            DocxParts.DOCUMENT_RELS_PATH to DocxParts.documentRelationships(),
            DocxParts.STYLES_PATH to DocxParts.styles(),
            DocxParts.NUMBERING_PATH to DocxParts.numbering(),
            DocxParts.DOCUMENT_PATH to document(catalog = catalog, generatedOn = generatedOn),
        )

        val buffer = ByteArrayOutputStream()
        ZipOutputStream(buffer).use { zip ->
            parts.forEach { (path, xml) ->
                val entry = ZipEntry(path)
                entry.time = FIXED_ENTRY_TIME
                zip.putNextEntry(entry)
                zip.write(xml.toByteArray(charset = Charsets.UTF_8))
                zip.closeEntry()
            }
        }

        return buffer.toByteArray()
    }

    // ── document body ─────────────────────────────────────────────────────────

    private fun document(catalog: RuleCatalog, generatedOn: String?): String {
        val bookmarks = catalog.rules.mapIndexed { index, rule -> rule.id to index }.toMap()
        val body = StringBuilder()

        appendCover(out = body, catalog = catalog, generatedOn = generatedOn)
        appendIndex(out = body, catalog = catalog, bookmarks = bookmarks)
        appendOutcomes(out = body, catalog = catalog, bookmarks = bookmarks)
        catalog.files.forEach { file -> appendFile(out = body, file = file, bookmarks = bookmarks) }

        return """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>""" +
            "<w:document xmlns:w=\"${DocxParts.WORDPROCESSING_NS}\"><w:body>" +
            body +
            DocxParts.sectionProperties() +
            "</w:body></w:document>"
    }

    /**
     * The three things that stop the document reading as a flat list of independent checks.
     *
     * Each is written only for a rule set it applies to: a reader of a rule set with no branches, no
     * variables and no `stop` should not have to hold caveats that never come up. Ordered by how much
     * they demand of the reader, strongest last — `stop` changes whether the rules further down apply
     * at all.
     */
    private fun appendCaveats(out: StringBuilder, catalog: RuleCatalog) {
        if (catalog.rules.any { rule -> rule.elseOutcomes.isNotEmpty() }) {
            out.append(
                DocxXml.paragraph(
                    style = "Normal",
                    text = "Some rules also say what happens when they do not match. Those rules " +
                        "contribute an outcome either way — the one listed under \"Then\" when the rule " +
                        "matches, the one under \"Otherwise\" when it does not.",
                )
            )
        }
        if (catalog.rules.any { rule -> rule.publishes.isNotEmpty() || rule.elsePublishes.isNotEmpty() }) {
            out.append(
                DocxXml.paragraph(
                    style = "Normal",
                    text = "Some rules publish a named value that the rules after them read. Those " +
                        "rules are order-dependent: the value only reaches a rule listed later, and " +
                        "only if the rule that publishes it matched.",
                )
            )
        }
        if (catalog.rules.any { rule -> rule.stopsOnThen || rule.stopsOnElse }) {
            out.append(
                DocxXml.paragraph(
                    style = "Normal",
                    text = "Some rules end the run. Where a rule says so, the rules listed after it are " +
                        "not evaluated at all for that record — so a rule further down does not apply, " +
                        "whether or not it would have matched.",
                )
            )
        }
    }

    private fun appendCover(out: StringBuilder, catalog: RuleCatalog, generatedOn: String?) {
        out.append(
            DocxXml.paragraph(
                style = "Title",
                text = "Rule overview — ${catalog.projectName ?: catalog.entryId ?: "rules"}",
            )
        )

        val facts = buildList {
            catalog.entryId?.let { entry -> add("Entry $entry") }
            add(CatalogText.count(n = catalog.rules.size, singular = "rule"))
            add(CatalogText.count(n = catalog.files.size, singular = "rule file"))
            catalog.schemaPath?.let { path -> add("Input contract $path") }
            generatedOn?.let { date -> add("Generated $date") }
        }
        out.append(DocxXml.paragraph(style = "Subtitle", text = facts.joinToString(separator = "  ·  ")))

        // Said once, plainly: every reader has met a first-match-wins engine, and without this they
        // will read the rest of the document as a decision list rather than a set of independent checks.
        out.append(
            DocxXml.paragraph(
                style = "Normal",
                text = "Every rule is checked against every record, and each one that matches " +
                    "contributes its own outcome — a later rule never overrides an earlier one. Rules " +
                    "are evaluated in the order they are listed below, which is the order the engine " +
                    "uses: rule-file order, then the order the rules appear inside each file.",
            )
        )
        appendCaveats(out = out, catalog = catalog)
        out.append(
            DocxXml.paragraph(
                style = "Normal",
                text = "Each rule below has a permanent identifier. Quote it when asking for a " +
                    "change, so it is unambiguous which rule is meant.",
            )
        )
    }

    // ── index ─────────────────────────────────────────────────────────────────

    private fun appendIndex(out: StringBuilder, catalog: RuleCatalog, bookmarks: Map<String, Int>) {
        if (catalog.rules.isEmpty()) {
            out.append(DocxXml.paragraph(style = "Normal", text = "No rules are defined in this entry."))
            return
        }

        out.append(DocxXml.paragraph(style = "Heading1", text = "At a glance"))

        val rows = catalog.rules.map { rule ->
            listOf(
                DocxXml.linkCell(id = rule.id, bookmarks = bookmarks),
                DocxXml.textCell(text = summaryOf(rule = rule)),
                DocxXml.codeCell(text = outcomeSummary(rule = rule)),
            )
        }
        out.append(
            DocxXml.table(headers = INDEX_HEADERS, widths = INDEX_WIDTHS, rows = rows)
        )
    }

    private fun summaryOf(rule: CatalogRule): String {
        return rule.description ?: CatalogText.flatten(condition = rule.condition)
    }

    /**
     * The index cell for a rule's outputs.
     *
     * An `else` outcome is prefixed rather than listed as a peer: in one glance cell, `label:low` under
     * `label:high` would read as two outcomes the rule produces together instead of one or the other.
     */
    private fun outcomeSummary(rule: CatalogRule): String {
        if (rule.outcomes.isEmpty() && rule.elseOutcomes.isEmpty()) {
            return "—"
        }

        val lines = rule.outcomes.map { outcome -> CatalogText.label(outcome = outcome) } +
                rule.elseOutcomes.map { outcome -> "otherwise ${CatalogText.label(outcome = outcome)}" }
        return lines.joinToString(separator = "\n")
    }

    // ── outcome summary ───────────────────────────────────────────────────────

    private fun appendOutcomes(out: StringBuilder, catalog: RuleCatalog, bookmarks: Map<String, Int>) {
        val byOutcome = catalog.rulesByOutcome()
        if (byOutcome.isEmpty()) {
            return
        }

        out.append(DocxXml.paragraph(style = "Heading1", text = "Outcomes this rule set can produce"))
        out.append(
            DocxXml.paragraph(
                style = "Normal",
                text = "One record can receive several outcomes at once — one from every rule that " +
                    "matches it.",
            )
        )

        val rows = byOutcome.map { (outcome, rules) ->
            listOf(
                DocxXml.codeCell(text = outcome.action),
                DocxXml.codeCell(text = outcome.arguments.joinToString(separator = ", ").ifEmpty { "—" }),
                DocxXml.cell(
                    runs = rules.joinToString(separator = DocxXml.run(text = ", ")) { rule ->
                        DocxXml.hyperlink(id = rule.id, bookmarks = bookmarks)
                    }
                ),
            )
        }
        out.append(
            DocxXml.table(headers = OUTCOME_HEADERS, widths = OUTCOME_WIDTHS, rows = rows)
        )
    }

    // ── one rule file ─────────────────────────────────────────────────────────

    private fun appendFile(out: StringBuilder, file: CatalogRuleFile, bookmarks: Map<String, Int>) {
        out.append(DocxXml.paragraph(style = "Heading1", text = file.relativePath))

        if (file.rules.isEmpty()) {
            out.append(DocxXml.paragraph(style = "Normal", text = "This file defines no rules."))
            return
        }

        file.rules.forEach { rule -> appendRule(out = out, rule = rule, bookmarks = bookmarks) }
    }

    private fun appendRule(out: StringBuilder, rule: CatalogRule, bookmarks: Map<String, Int>) {
        val index = bookmarks.getValue(key = rule.id)
        out.append(DocxXml.bookmarkedHeading(id = index, name = DocxXml.bookmarkName(index = index), text = rule.id))

        rule.description?.let { description ->
            out.append(DocxXml.paragraph(style = "Normal", text = description))
        }

        out.append(DocxXml.paragraph(style = "FieldLabel", text = CatalogText.intro(condition = rule.condition)))
        CatalogText.walk(condition = rule.condition, depth = 0, unwrapRoot = true) { text, depth ->
            out.append(DocxXml.bullet(text = text, depth = depth))
        }

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

        out.append(DocxXml.paragraph(style = "FieldLabel", text = "In the rule language"))
        out.append(DocxXml.paragraph(style = "TechCondition", text = rule.technicalCondition))
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
            out.append(DocxXml.paragraph(style = "FieldLabel", text = publishesLabel))
            publishes.forEach { name ->
                out.append(DocxXml.bullet(text = name, depth = 0, code = true))
            }
        }

        if (outcomes.isNotEmpty()) {
            out.append(DocxXml.paragraph(style = "FieldLabel", text = label))
            outcomes.forEach { outcome ->
                out.append(DocxXml.bullet(text = CatalogText.label(outcome = outcome), depth = 0, code = true))
            }
        }

        // Stated per branch, because a rule can halt on one verdict and carry on with the other. A
        // reader who misses this reads every rule below as still applying.
        if (stops) {
            val suffix = if (label == "Then") "" else " (otherwise)"
            out.append(DocxXml.paragraph(style = "FieldLabel", text = "Stops here$suffix"))
            out.append(
                DocxXml.paragraph(style = "Normal", text = "No rule listed after this one is evaluated.")
            )
        }
    }
}
