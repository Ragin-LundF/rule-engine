package ruleengine.export.docx

/**
 * The WordprocessingML fragments the document is assembled from.
 *
 * Separate from [DocxCatalogWriter] because the two answer different questions: this one knows how
 * to spell a paragraph, a table and a bookmark in the format; the writer knows what a rule overview
 * should say. Keeping the markup here means the writer reads as document structure rather than as
 * angle brackets.
 *
 * Every string returned is a well-formed fragment — no caller ever concatenates half a tag.
 */
internal object DocxXml {

    fun paragraph(style: String, text: String): String {
        return "<w:p><w:pPr><w:pStyle w:val=\"$style\"/></w:pPr>${runs(text = text)}</w:p>"
    }

    fun bullet(text: String, depth: Int, code: Boolean = false): String {
        val properties = "<w:pPr><w:pStyle w:val=\"ConditionBullet\"/>" +
            "<w:numPr><w:ilvl w:val=\"$depth\"/><w:numId w:val=\"${DocxParts.BULLET_NUM_ID}\"/></w:numPr>" +
            "</w:pPr>"

        return "<w:p>$properties${runs(text = text, style = if (code) "CodeText" else null)}</w:p>"
    }

    fun bookmarkedHeading(id: Int, name: String, text: String): String {
        // The bookmark wraps the heading text rather than sitting before the paragraph, so following
        // the link scrolls the heading itself into view instead of the blank line above it.
        return "<w:p><w:pPr><w:pStyle w:val=\"Heading2\"/></w:pPr>" +
            "<w:bookmarkStart w:id=\"$id\" w:name=\"$name\"/>" +
            runs(text = text) +
            "<w:bookmarkEnd w:id=\"$id\"/>" +
            "</w:p>"
    }

    fun hyperlink(id: String, bookmarks: Map<String, Int>): String {
        val index = bookmarks[id] ?: return run(text = id, style = "CodeText")
        val anchor = bookmarkName(index = index)

        return "<w:hyperlink w:anchor=\"$anchor\">${run(text = id, style = "Hyperlink")}</w:hyperlink>"
    }

    /**
     * A generated name rather than the rule id.
     *
     * A bookmark name may only contain letters, digits and underscores, must start with a letter and
     * is capped at 40 characters — a rule id satisfies none of that reliably, and a sanitised one
     * could collide with another rule's.
     */
    fun bookmarkName(index: Int): String {
        return "Rule$index"
    }

    /**
     * Splits [text] into runs at line breaks.
     *
     * A `<w:t>` cannot contain a newline — Word collapses it to a space — so an explicit `<w:br/>`
     * is the only way to keep two outcomes on separate lines inside one table cell.
     */
    fun runs(text: String, style: String? = null): String {
        return text.split("\n").joinToString(separator = "<w:br/>") { line ->
            run(text = line, style = style)
        }
    }

    fun run(text: String, style: String? = null): String {
        val properties = style?.let { name -> "<w:rPr><w:rStyle w:val=\"$name\"/></w:rPr>" }.orEmpty()

        return "<w:r>$properties<w:t xml:space=\"preserve\">${escape(text = text)}</w:t></w:r>"
    }

    // ── tables ────────────────────────────────────────────────────────────────

    fun table(headers: List<String>, widths: List<Int>, rows: List<List<String>>): String {
        val grid = widths.joinToString(separator = "", prefix = "<w:tblGrid>", postfix = "</w:tblGrid>") { width ->
            "<w:gridCol w:w=\"${width * 90}\"/>"
        }
        val headerRow = headers.mapIndexed { index, header ->
            tableCell(
                content = paragraph(style = "TableHeaderCell", text = header),
                width = widths[index],
                shaded = true,
            )
        }.joinToString(separator = "")

        val body = rows.joinToString(separator = "") { row ->
            val cells = row.mapIndexed { index, content ->
                tableCell(content = content, width = widths[index], shaded = false)
            }.joinToString(separator = "")
            "<w:tr>$cells</w:tr>"
        }

        // `tblHeader` repeats the header row on every page the table spills onto — a thirteen-row
        // index survives a page break, and without it the columns lose their names halfway down.
        val properties = "<w:tblPr><w:tblW w:w=\"5000\" w:type=\"pct\"/>${DocxParts.tableBorders()}" +
            "<w:tblLayout w:type=\"fixed\"/></w:tblPr>"

        // A paragraph after the table: Word treats two adjacent tables, or a table at the very end of
        // the body, as malformed and offers to repair the file.
        return "<w:tbl>$properties$grid<w:tr><w:trPr><w:tblHeader/></w:trPr>$headerRow</w:tr>$body</w:tbl>" +
            "<w:p><w:pPr><w:spacing w:after=\"0\"/></w:pPr></w:p>"
    }

    fun tableCell(content: String, width: Int, shaded: Boolean): String {
        val shading = if (shaded) DocxParts.headerCellShading() else ""
        val properties = "<w:tcPr><w:tcW w:w=\"${width * 50}\" w:type=\"pct\"/>$shading</w:tcPr>"

        return "<w:tc>$properties$content</w:tc>"
    }

    fun textCell(text: String): String {
        return paragraph(style = "TableCell", text = text)
    }

    fun codeCell(text: String): String {
        return "<w:p><w:pPr><w:pStyle w:val=\"TableCell\"/></w:pPr>" +
            runs(text = text, style = "CodeText") +
            "</w:p>"
    }

    fun cell(runs: String): String {
        return "<w:p><w:pPr><w:pStyle w:val=\"TableCell\"/></w:pPr>$runs</w:p>"
    }

    fun linkCell(id: String, bookmarks: Map<String, Int>): String {
        return "<w:p><w:pPr><w:pStyle w:val=\"TableCell\"/></w:pPr>" +
            hyperlink(id = id, bookmarks = bookmarks) +
            "</w:p>"
    }

    // ── text handling ─────────────────────────────────────────────────────────

    /**
     * Escapes [text] for XML content.
     *
     * Not optional: a rule description is free text, and a single unescaped `&` or `<` turns the
     * document part into malformed XML, which Word reports as a corrupt file rather than as a
     * formatting glitch. Control characters are dropped for the same reason — XML 1.0 has no way to
     * represent them at all, not even as a numeric reference.
     */
    fun escape(text: String): String {
        // Positional: StringBuilder is a Java type, so named arguments are unavailable.
        val out = StringBuilder(text.length)
        text.forEach { character ->
            when {
                character == '&' -> out.append("&amp;")
                character == '<' -> out.append("&lt;")
                character == '>' -> out.append("&gt;")
                character == '"' -> out.append("&quot;")
                character == '\'' -> out.append("&apos;")
                character == '\t' -> out.append(' ')
                character.isXmlSafe() -> out.append(character)
            }
        }

        return out.toString()
    }

    fun Char.isXmlSafe(): Boolean {
        return this >= ' ' || this == '\n' || this == '\r'
    }
}
