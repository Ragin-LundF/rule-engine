package ruleengine.export.docx

/**
 * The fixed XML parts of the `.docx` package — everything except the document body.
 *
 * A `.docx` is a ZIP of a handful of XML parts. Writing them by hand rather than pulling in a
 * library keeps `ruleengine-core` free of any dependency beyond Jackson, and costs nothing at
 * runtime: Word, LibreOffice and Google Docs do all the layout and pagination, so there is no
 * measuring, wrapping or page-breaking code here at all.
 *
 * The parts and their relationships are the minimum a word processor accepts. Removing any one of
 * them, or the `[Content_Types].xml` entry declaring it, makes the file unopenable rather than
 * merely unstyled.
 */
internal object DocxParts {

    const val CONTENT_TYPES_PATH = "[Content_Types].xml"
    const val ROOT_RELS_PATH = "_rels/.rels"
    const val DOCUMENT_PATH = "word/document.xml"
    const val DOCUMENT_RELS_PATH = "word/_rels/document.xml.rels"
    const val STYLES_PATH = "word/styles.xml"
    const val NUMBERING_PATH = "word/numbering.xml"

    /** The bullet list definition every condition bullet references. */
    const val BULLET_NUM_ID = 1

    private const val XML_DECLARATION = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>"""

    const val WORDPROCESSING_NS = "http://schemas.openxmlformats.org/wordprocessingml/2006/main"
    private const val RELATIONSHIPS_NS = "http://schemas.openxmlformats.org/officeDocument/2006/relationships"
    private const val PACKAGE_RELATIONSHIPS_NS = "http://schemas.openxmlformats.org/package/2006/relationships"
    private const val CONTENT_TYPES_NS = "http://schemas.openxmlformats.org/package/2006/content-types"
    private const val OFFICE_DOCUMENT_TYPE = "$RELATIONSHIPS_NS/officeDocument"
    private const val STYLES_TYPE = "$RELATIONSHIPS_NS/styles"
    private const val NUMBERING_TYPE = "$RELATIONSHIPS_NS/numbering"

    // ── palette and type, mirroring the design the Markdown export follows ────

    /** Near-black with a slate bias; a pure black reads harsher than ink on paper. */
    private const val INK = "16202B"
    private const val INK_SECONDARY = "445868"
    private const val MUTED = "78909F"
    private const val ACCENT = "0E6E72"
    private const val HAIRLINE = "DBE4E9"
    private const val CODE_BACKGROUND = "EEF3F5"

    /** All three ship with Word, so nothing has to be embedded and the file stays small. */
    private const val BODY_FONT = "Calibri"
    private const val HEADING_FONT = "Cambria"
    private const val MONO_FONT = "Consolas"

    fun contentTypes(): String {
        return """
            |$XML_DECLARATION
            |<Types xmlns="$CONTENT_TYPES_NS">
            |  <Default Extension="rels" ContentType="application/vnd.openxmlformats-package.relationships+xml"/>
            |  <Default Extension="xml" ContentType="application/xml"/>
            |  <Override PartName="/$DOCUMENT_PATH" ContentType="$DOCUMENT_CONTENT_TYPE"/>
            |  <Override PartName="/$STYLES_PATH" ContentType="$STYLES_CONTENT_TYPE"/>
            |  <Override PartName="/$NUMBERING_PATH" ContentType="$NUMBERING_CONTENT_TYPE"/>
            |</Types>
        """.trimMargin()
    }

    fun rootRelationships(): String {
        return """
            |$XML_DECLARATION
            |<Relationships xmlns="$PACKAGE_RELATIONSHIPS_NS">
            |  <Relationship Id="rId1" Type="$OFFICE_DOCUMENT_TYPE" Target="$DOCUMENT_PATH"/>
            |</Relationships>
        """.trimMargin()
    }

    fun documentRelationships(): String {
        return """
            |$XML_DECLARATION
            |<Relationships xmlns="$PACKAGE_RELATIONSHIPS_NS">
            |  <Relationship Id="rId1" Type="$STYLES_TYPE" Target="styles.xml"/>
            |  <Relationship Id="rId2" Type="$NUMBERING_TYPE" Target="numbering.xml"/>
            |</Relationships>
        """.trimMargin()
    }

    /** The document's whole visual identity. */
    fun styles(): String {
        return """
            |$XML_DECLARATION
            |<w:styles xmlns:w="$WORDPROCESSING_NS">
            |${documentDefaults()}
            |${paragraphStyles()}
            |${characterStyles()}
            |</w:styles>
        """.trimMargin()
    }

    /** Applies to every run and paragraph that does not override it. */
    private fun documentDefaults(): String {
        return """
            |  <w:docDefaults>
            |    <w:rPrDefault>
            |      <w:rPr>
            |        <w:rFonts w:ascii="$BODY_FONT" w:hAnsi="$BODY_FONT" w:cs="$BODY_FONT"/>
            |        <w:color w:val="$INK"/>
            |        <w:sz w:val="22"/>
            |      </w:rPr>
            |    </w:rPrDefault>
            |    <w:pPrDefault>
            |      <w:pPr><w:spacing w:after="140" w:line="276" w:lineRule="auto"/></w:pPr>
            |    </w:pPrDefault>
            |  </w:docDefaults>
        """.trimMargin()
    }

    private fun paragraphStyles(): String {
        return listOf(coverStyles(), headingStyles(), ruleStyles(), tableStyles())
            .joinToString(separator = "\n")
    }

    /** The title block: the document name and the line of facts under it. */
    private fun coverStyles(): String {
        return """
            |  <w:style w:type="paragraph" w:default="1" w:styleId="Normal">
            |    <w:name w:val="Normal"/>
            |  </w:style>
            |  <w:style w:type="paragraph" w:styleId="Title">
            |    <w:name w:val="Title"/>
            |    <w:basedOn w:val="Normal"/>
            |    <w:pPr><w:spacing w:before="0" w:after="120"/></w:pPr>
            |    <w:rPr>
            |      <w:rFonts w:ascii="$HEADING_FONT" w:hAnsi="$HEADING_FONT"/>
            |      <w:b/><w:sz w:val="56"/><w:color w:val="$INK"/>
            |    </w:rPr>
            |  </w:style>
            |  <w:style w:type="paragraph" w:styleId="Subtitle">
            |    <w:name w:val="Subtitle"/>
            |    <w:basedOn w:val="Normal"/>
            |    <w:pPr><w:spacing w:before="0" w:after="240"/></w:pPr>
            |    <w:rPr><w:sz w:val="20"/><w:color w:val="$MUTED"/></w:rPr>
            |  </w:style>
        """.trimMargin()
    }

    /**
     * `Heading1` and `Heading2` must carry the names `heading 1` and `heading 2` and an `outlineLvl`,
     * or Word's navigation pane stays empty — which for a document of this length is the difference
     * between navigable and not.
     */
    private fun headingStyles(): String {
        return """
            |  <w:style w:type="paragraph" w:styleId="Heading1">
            |    <w:name w:val="heading 1"/>
            |    <w:basedOn w:val="Normal"/>
            |    <w:pPr>
            |      <w:keepNext/>
            |      <w:pBdr><w:bottom w:val="single" w:sz="12" w:space="4" w:color="$INK"/></w:pBdr>
            |      <w:spacing w:before="440" w:after="200"/>
            |      <w:outlineLvl w:val="0"/>
            |    </w:pPr>
            |    <w:rPr>
            |      <w:rFonts w:ascii="$HEADING_FONT" w:hAnsi="$HEADING_FONT"/>
            |      <w:b/><w:sz w:val="32"/><w:color w:val="$INK"/>
            |    </w:rPr>
            |  </w:style>
            |  <w:style w:type="paragraph" w:styleId="Heading2">
            |    <w:name w:val="heading 2"/>
            |    <w:basedOn w:val="Normal"/>
            |    <w:pPr>
            |      <w:keepNext/>
            |      <w:spacing w:before="320" w:after="100"/>
            |      <w:outlineLvl w:val="1"/>
            |    </w:pPr>
            |    <w:rPr>
            |      <w:rFonts w:ascii="$MONO_FONT" w:hAnsi="$MONO_FONT"/>
            |      <w:b/><w:sz w:val="24"/><w:color w:val="$ACCENT"/>
            |    </w:rPr>
            |  </w:style>
        """.trimMargin()
    }

    /** The parts of one rule: its labels, its condition bullets and its rule-language line. */
    private fun ruleStyles(): String {
        return """
            |  <w:style w:type="paragraph" w:styleId="FieldLabel">
            |    <w:name w:val="Field Label"/>
            |    <w:basedOn w:val="Normal"/>
            |    <w:pPr><w:keepNext/><w:spacing w:before="200" w:after="60"/></w:pPr>
            |    <w:rPr><w:caps/><w:b/><w:sz w:val="16"/><w:color w:val="$MUTED"/><w:spacing w:val="30"/></w:rPr>
            |  </w:style>
            |  <w:style w:type="paragraph" w:styleId="TechCondition">
            |    <w:name w:val="Technical Condition"/>
            |    <w:basedOn w:val="Normal"/>
            |    <w:pPr>
            |      <w:shd w:val="clear" w:color="auto" w:fill="$CODE_BACKGROUND"/>
            |      <w:spacing w:before="80" w:after="200"/>
            |      <w:ind w:left="120" w:right="120"/>
            |    </w:pPr>
            |    <w:rPr>
            |      <w:rFonts w:ascii="$MONO_FONT" w:hAnsi="$MONO_FONT"/>
            |      <w:sz w:val="18"/><w:color w:val="$INK_SECONDARY"/>
            |    </w:rPr>
            |  </w:style>
            |  <w:style w:type="paragraph" w:styleId="ConditionBullet">
            |    <w:name w:val="Condition Bullet"/>
            |    <w:basedOn w:val="Normal"/>
            |    <w:pPr>
            |      <w:numPr><w:ilvl w:val="0"/><w:numId w:val="$BULLET_NUM_ID"/></w:numPr>
            |      <w:spacing w:after="40"/>
            |      <w:contextualSpacing/>
            |    </w:pPr>
            |  </w:style>
        """.trimMargin()
    }

    private fun tableStyles(): String {
        return """
            |  <w:style w:type="paragraph" w:styleId="TableHeaderCell">
            |    <w:name w:val="Table Header Cell"/>
            |    <w:basedOn w:val="Normal"/>
            |    <w:pPr><w:spacing w:before="60" w:after="60"/></w:pPr>
            |    <w:rPr><w:caps/><w:b/><w:sz w:val="16"/><w:color w:val="$MUTED"/><w:spacing w:val="30"/></w:rPr>
            |  </w:style>
            |  <w:style w:type="paragraph" w:styleId="TableCell">
            |    <w:name w:val="Table Cell"/>
            |    <w:basedOn w:val="Normal"/>
            |    <w:pPr><w:spacing w:before="60" w:after="60"/></w:pPr>
            |    <w:rPr><w:sz w:val="19"/><w:color w:val="$INK_SECONDARY"/></w:rPr>
            |  </w:style>
        """.trimMargin()
    }

    private fun characterStyles(): String {
        return """
            |  <w:style w:type="character" w:styleId="CodeText">
            |    <w:name w:val="Code Text"/>
            |    <w:rPr>
            |      <w:rFonts w:ascii="$MONO_FONT" w:hAnsi="$MONO_FONT"/>
            |      <w:sz w:val="18"/><w:color w:val="$INK"/>
            |    </w:rPr>
            |  </w:style>
            |  <w:style w:type="character" w:styleId="Hyperlink">
            |    <w:name w:val="Hyperlink"/>
            |    <w:rPr>
            |      <w:rFonts w:ascii="$MONO_FONT" w:hAnsi="$MONO_FONT"/>
            |      <w:sz w:val="18"/><w:color w:val="$ACCENT"/><w:u w:val="none"/>
            |    </w:rPr>
            |  </w:style>
        """.trimMargin()
    }

    /**
     * One bullet list definition, four levels deep.
     *
     * The bullet character is a literal `•` rather than the usual Symbol-font `F0B7`, because that
     * trick depends on a font Word has and LibreOffice substitutes — where it renders as a stray
     * letter instead of a bullet.
     */
    fun numbering(): String {
        val levels = (0..3).joinToString(separator = "\n") { level -> bulletLevel(level = level) }

        return """
            |$XML_DECLARATION
            |<w:numbering xmlns:w="$WORDPROCESSING_NS">
            |  <w:abstractNum w:abstractNumId="0">
            |    <w:multiLevelType w:val="hybridMultilevel"/>
            |$levels
            |  </w:abstractNum>
            |  <w:num w:numId="$BULLET_NUM_ID"><w:abstractNumId w:val="0"/></w:num>
            |</w:numbering>
        """.trimMargin()
    }

    private fun bulletLevel(level: Int): String {
        val indent = 360 + level * 360
        val glyph = if (level % 2 == 0) "•" else "◦"

        return """
            |    <w:lvl w:ilvl="$level">
            |      <w:start w:val="1"/>
            |      <w:numFmt w:val="bullet"/>
            |      <w:lvlText w:val="$glyph"/>
            |      <w:lvlJc w:val="left"/>
            |      <w:pPr><w:ind w:left="$indent" w:hanging="360"/></w:pPr>
            |    </w:lvl>
        """.trimMargin()
    }

    private const val DOCUMENT_CONTENT_TYPE =
        "application/vnd.openxmlformats-officedocument.wordprocessingml.document.main+xml"
    private const val STYLES_CONTENT_TYPE =
        "application/vnd.openxmlformats-officedocument.wordprocessingml.styles+xml"
    private const val NUMBERING_CONTENT_TYPE =
        "application/vnd.openxmlformats-officedocument.wordprocessingml.numbering+xml"

    /** A4 portrait with 25 mm margins, in twentieths of a point. */
    fun sectionProperties(): String {
        return "<w:sectPr><w:pgSz w:w=\"11906\" w:h=\"16838\"/>" +
            "<w:pgMar w:top=\"1418\" w:right=\"1418\" w:bottom=\"1418\" w:left=\"1418\" " +
            "w:header=\"708\" w:footer=\"708\" w:gutter=\"0\"/></w:sectPr>"
    }

    /** Hairline borders on every edge, so a table reads as a grid without shouting. */
    fun tableBorders(): String {
        val edges = listOf("top", "left", "bottom", "right", "insideH", "insideV")

        return edges.joinToString(separator = "", prefix = "<w:tblBorders>", postfix = "</w:tblBorders>") { edge ->
            "<w:$edge w:val=\"single\" w:sz=\"4\" w:space=\"0\" w:color=\"$HAIRLINE\"/>"
        }
    }

    fun headerCellShading(): String {
        return "<w:shd w:val=\"clear\" w:color=\"auto\" w:fill=\"$CODE_BACKGROUND\"/>"
    }
}
