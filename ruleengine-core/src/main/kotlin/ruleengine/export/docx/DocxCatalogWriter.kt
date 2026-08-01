package ruleengine.export.docx

import ruleengine.export.dto.CatalogOutcome
import ruleengine.export.dto.CatalogRule
import ruleengine.export.dto.CatalogRuleFile
import ruleengine.export.dto.PlainAll
import ruleengine.export.dto.PlainAny
import ruleengine.export.dto.PlainCondition
import ruleengine.export.dto.PlainLeaf
import ruleengine.export.dto.PlainNot
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

    private fun appendCover(out: StringBuilder, catalog: RuleCatalog, generatedOn: String?) {
        out.append(
            DocxXml.paragraph(
                style = "Title",
                text = "Rule overview — ${catalog.projectName ?: catalog.entryId ?: "rules"}",
            )
        )

        val facts = buildList {
            catalog.entryId?.let { entry -> add("Entry $entry") }
            add(count(n = catalog.rules.size, singular = "rule"))
            add(count(n = catalog.files.size, singular = "rule file"))
            catalog.schemaPath?.let { path -> add("Input contract $path") }
            generatedOn?.let { date -> add("Generated $date") }
        }
        out.append(DocxXml.paragraph(style = "Subtitle", text = facts.joinToString(separator = "  ·  ")))

        // Said once, plainly: every reader has met a first-match-wins engine, and without this they
        // will read the rest of the document as a decision list rather than a set of independent checks.
        out.append(
            DocxXml.paragraph(
                style = "Normal",
                text = "Rules are independent. Every rule is checked against every record, and each " +
                    "one that matches contributes its own outcome — a later rule never overrides an " +
                    "earlier one.",
            )
        )
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
        return rule.description ?: flatten(condition = rule.condition)
    }

    private fun outcomeSummary(rule: CatalogRule): String {
        if (rule.outcomes.isEmpty()) {
            return "—"
        }

        return rule.outcomes.joinToString(separator = "\n") { outcome -> label(outcome = outcome) }
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

        out.append(DocxXml.paragraph(style = "FieldLabel", text = introFor(condition = rule.condition)))
        appendCondition(out = out, condition = rule.condition, depth = 0, unwrapRoot = true)

        if (rule.outcomes.isNotEmpty()) {
            out.append(DocxXml.paragraph(style = "FieldLabel", text = "Then"))
            rule.outcomes.forEach { outcome ->
                out.append(DocxXml.bullet(text = label(outcome = outcome), depth = 0, code = true))
            }
        }

        out.append(DocxXml.paragraph(style = "FieldLabel", text = "In the rule language"))
        out.append(DocxXml.paragraph(style = "TechCondition", text = rule.technicalCondition))
    }

    private fun introFor(condition: PlainCondition): String {
        return when (condition) {
            is PlainAll -> "Applies when all of the following are true"
            is PlainAny -> "Applies when any of the following is true"
            is PlainNot -> "Applies when the following is not true"
            is PlainLeaf -> "Applies when"
        }
    }

    /**
     * Writes the condition as bullets.
     *
     * [unwrapRoot] drops the outermost group's own bullet, because the label above already said "all
     * of the following" — repeating it would indent every rule in the document one level for nothing.
     */
    private fun appendCondition(
        out: StringBuilder,
        condition: PlainCondition,
        depth: Int,
        unwrapRoot: Boolean = false,
    ) {
        when (condition) {
            is PlainLeaf -> out.append(DocxXml.bullet(text = condition.text, depth = depth))

            is PlainAll -> appendChildren(
                out = out,
                children = condition.children,
                depth = depth,
                unwrapRoot = unwrapRoot,
                header = "All of the following are true:",
            )

            is PlainAny -> appendChildren(
                out = out,
                children = condition.children,
                depth = depth,
                unwrapRoot = unwrapRoot,
                header = "Any of the following is true:",
            )

            is PlainNot -> {
                if (unwrapRoot) {
                    appendCondition(out = out, condition = condition.child, depth = depth)
                } else {
                    out.append(DocxXml.bullet(text = "The following is not true:", depth = depth))
                    appendCondition(out = out, condition = condition.child, depth = depth + 1)
                }
            }
        }
    }

    private fun appendChildren(
        out: StringBuilder,
        children: List<PlainCondition>,
        depth: Int,
        unwrapRoot: Boolean,
        header: String,
    ) {
        if (unwrapRoot) {
            children.forEach { child -> appendCondition(out = out, condition = child, depth = depth) }
            return
        }

        out.append(DocxXml.bullet(text = header, depth = depth))
        children.forEach { child -> appendCondition(out = out, condition = child, depth = depth + 1) }
    }

    // ── shared with the Markdown renderer ─────────────────────────────────────

    private fun label(outcome: CatalogOutcome): String {
        if (outcome.arguments.isEmpty()) {
            return outcome.action
        }

        return "${outcome.action} ${outcome.arguments.joinToString(separator = ", ")}"
    }

    private fun count(n: Int, singular: String): String {
        return if (n == 1) "1 $singular" else "$n ${singular}s"
    }

    private fun flatten(condition: PlainCondition): String {
        return when (condition) {
            is PlainLeaf -> condition.text
            is PlainNot -> "not (${flatten(condition = condition.child)})"
            is PlainAll -> condition.children.joinToString(separator = " and ") { child ->
                flatten(condition = child)
            }

            is PlainAny -> condition.children.joinToString(separator = " or ") { child ->
                flatten(condition = child)
            }
        }
    }
}
