package ui.dock

/**
 * Where a declaration sits in the YAML the editors generate.
 *
 * These read the output of `FieldSchemaYamlBridge`, `ActionSchemaYamlBridge` and `ManifestYamlBridge`,
 * so they encode those writers' shapes — two-space indentation, a `fields:` key for nested members, a
 * `- id:` sequence for manifest entries. That coupling is deliberate and it is why the tests drive
 * these functions through the real bridges rather than through hand-written YAML: if a writer changes
 * its layout, a test fails instead of a highlight silently landing on the wrong lines.
 *
 * Parsing rather than using a YAML library on purpose. The dock previews text that is regenerated on
 * every keystroke and is routinely mid-edit; a parse failure would blank the highlight exactly when the
 * author most needs to see where they are.
 */

/** One line of a document, with where it sits in the whole string. */
private class YamlLine(val text: String, val start: Int, val endExclusive: Int) {
    val isBlank: Boolean get() = text.isBlank()

    /** Leading spaces. Meaningless for a blank line — callers must skip those instead of asking. */
    val indent: Int get() = text.indexOfFirst { char -> char != ' ' }.let { if (it < 0) 0 else it }
}

private fun String.yamlLines(): List<YamlLine> {
    val out = mutableListOf<YamlLine>()
    var start = 0
    while (start <= length) {
        val newline = indexOf(char = '\n', startIndex = start)
        val end = if (newline == -1) length else newline
        out.add(
            element = YamlLine(
                text = substring(startIndex = start, endIndex = end),
                start = start,
                endExclusive = end,
            ),
        )
        if (newline == -1) break
        start = newline + 1
    }
    return out
}

/**
 * The index of the line declaring `<key>:` at exactly [indent], searched in `[from, until)`.
 *
 * Stops at the first line shallower than [indent] that is not blank: that line belongs to an outer
 * block, so continuing past it would find a same-named key somewhere else entirely — `lender` appears
 * at two depths in the loan-decisioning sample alone.
 */
private fun List<YamlLine>.indexOfKey(key: String, indent: Int, from: Int, until: Int): Int? {
    val header = " ".repeat(n = indent) + key + ":"
    for (index in from until until) {
        val line = this[index]
        if (line.isBlank) continue
        if (line.indent < indent) return null
        if (line.indent == indent && (line.text == header || line.text.startsWith(prefix = "$header "))) {
            return index
        }
    }
    return null
}

/**
 * The last line index belonging to the block opened at [headerIndex], inclusive.
 *
 * Everything indented deeper than the header. Blank lines are neither included as content nor treated
 * as the end — a blank line has no indentation to compare, and the bridges emit one after `schema:`.
 */
private fun List<YamlLine>.blockEnd(headerIndex: Int, until: Int): Int {
    val baseIndent = this[headerIndex].indent
    return deeperRunEnd(from = headerIndex + 1, until = until, deeperThan = baseIndent) ?: headerIndex
}

/**
 * The last index of the unbroken run of lines indented deeper than [deeperThan], or null when the run
 * is empty.
 *
 * `filterNot { isBlank }` before `takeWhile` is what lets a blank line sit inside a block: it is
 * skipped rather than ending the run, which matters because the schema bridge emits one after
 * `schema:` and every field would otherwise be invisible.
 */
private fun List<YamlLine>.deeperRunEnd(from: Int, until: Int, deeperThan: Int): Int? =
    (from until until)
        .asSequence()
        .filterNot { index -> this[index].isBlank }
        .takeWhile { index -> this[index].indent > deeperThan }
        .lastOrNull()

private fun List<YamlLine>.rangeOf(headerIndex: Int, lastIndex: Int): IntRange =
    this[headerIndex].start..(this[lastIndex].endExclusive - 1).coerceAtLeast(minimumValue = this[headerIndex].start)

/**
 * The lines a schema field owns: its `path:` header and everything under it.
 *
 * [dottedPath] is the form a rule names the field by, and the form the Inspector selects — so
 * `existingLoans.lender` walks `fields:` → `existingLoans:` → `fields:` → `lender:`. Each level is two
 * spaces deeper than the last, and each nested level sits under its parent's own `fields:` key, which
 * is what `FieldSchemaYamlBridge.appendFields` writes.
 */
internal fun schemaFieldRange(yaml: String, dottedPath: String): IntRange? {
    val segments = dottedPath.split(".").filter { part -> part.isNotBlank() }
    if (segments.isEmpty() || yaml.isEmpty()) return null

    val lines = yaml.yamlLines()
    // Every level is looked for under a `fields:` key — the document's own for a top-level field, the
    // parent's for a member. That is what keeps `existingLoans.lender` from matching `applicant.lender`.
    var container = lines.indexOfKey(key = "fields", indent = 0, from = 0, until = lines.size) ?: return null
    var containerEnd = lines.size
    var indent = INDENT
    var header = -1

    for (depth in segments.indices) {
        header = lines.indexOfKey(
            key = segments[depth],
            indent = indent,
            from = container + 1,
            until = containerEnd,
        ) ?: return null

        if (depth == segments.lastIndex) break

        val fieldEnd = lines.blockEnd(headerIndex = header, until = containerEnd)
        container = lines.indexOfKey(
            key = "fields",
            indent = indent + INDENT,
            from = header + 1,
            until = fieldEnd + 1,
        ) ?: return null
        containerEnd = fieldEnd + 1
        indent += INDENT * 2
    }

    return lines.rangeOf(
        headerIndex = header,
        lastIndex = lines.blockEnd(headerIndex = header, until = containerEnd),
    )
}

/** The lines an action owns: its `name:` header under `actions:`, plus its `purpose` and `argTypes`. */
internal fun actionRange(yaml: String, name: String): IntRange? {
    if (name.isBlank() || yaml.isEmpty()) return null
    val lines = yaml.yamlLines()
    val root = lines.indexOfKey(key = "actions", indent = 0, from = 0, until = lines.size) ?: return null
    val header = lines.indexOfKey(key = name, indent = INDENT, from = root + 1, until = lines.size) ?: return null
    return lines.rangeOf(headerIndex = header, lastIndex = lines.blockEnd(headerIndex = header, until = lines.size))
}

/**
 * The lines a manifest entry owns: its `- id: <entryId>` line through the end of that sequence item.
 *
 * A sequence item is not a mapping, so [blockEnd] does not apply: the `- ` marker sits at the item's
 * own indentation while its sibling keys are two spaces deeper. The item therefore ends at the line
 * before the next `- ` at the same indentation, or at the end of `entries:`.
 */
internal fun manifestEntryRange(yaml: String, entryId: String): IntRange? {
    if (entryId.isBlank() || yaml.isEmpty()) return null
    val lines = yaml.yamlLines()
    val root = lines.indexOfKey(key = "entries", indent = 0, from = 0, until = lines.size) ?: return null

    val marker = " ".repeat(n = INDENT) + "- id: "
    val header = (root + 1 until lines.size).firstOrNull { index ->
        val text = lines[index].text
        text.startsWith(prefix = marker) && text.removePrefix(prefix = marker).trim().trim('"') == entryId
    } ?: return null

    val last = lines.deeperRunEnd(from = header + 1, until = lines.size, deeperThan = INDENT) ?: header
    return lines.rangeOf(headerIndex = header, lastIndex = last)
}

/** Two spaces, which is what every bridge in the module writes. */
private const val INDENT: Int = 2
