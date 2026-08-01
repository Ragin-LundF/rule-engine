package ruleengine.export.docx

import ruleengine.dsl.parser.Parser
import ruleengine.export.RuleCatalogBuilder
import ruleengine.export.dto.ParsedRuleFile
import ruleengine.export.dto.RuleCatalog
import java.io.ByteArrayInputStream
import java.nio.file.Path
import java.util.zip.ZipInputStream
import javax.xml.XMLConstants
import javax.xml.parsers.DocumentBuilderFactory
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * The `.docx` is written by hand, so these tests stand in for the word processor.
 *
 * Word reports any malformed part as "the file is corrupt" with no hint as to which one, so the
 * checks that matter most are structural: every declared part is present, and every part parses as
 * XML. A test that only looked for expected text would pass on a file nothing can open.
 */
class DocxCatalogWriterTest {

    private val manifestPath: Path = Path.of("src/test/resources/warehouse-shipments/manifest.yaml")

    private fun warehouseCatalog(): RuleCatalog {
        return RuleCatalogBuilder.fromManifest(manifestPath = manifestPath).single()
    }

    private fun catalogOf(rules: String): RuleCatalog {
        return RuleCatalogBuilder.build(
            projectName = "p",
            entryId = "e",
            files = listOf(
                ParsedRuleFile(
                    relativePath = "r.rule",
                    rules = Parser(input = rules).parseRules(),
                )
            ),
        )
    }

    /** Reads the package back into part-path → UTF-8 text. */
    private fun unzip(bytes: ByteArray): Map<String, String> {
        val parts = linkedMapOf<String, String>()
        ZipInputStream(ByteArrayInputStream(bytes)).use { zip ->
            while (true) {
                val entry = zip.nextEntry ?: break
                parts[entry.name] = zip.readBytes().toString(Charsets.UTF_8)
            }
        }

        return parts
    }

    private fun parseXml(xml: String) {
        val factory = DocumentBuilderFactory.newInstance()
        // The parts declare no external entities; refusing to fetch any keeps a malformed document
        // from turning a unit test into a network call.
        factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true)
        factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD, "")
        factory.newDocumentBuilder().parse(ByteArrayInputStream(xml.toByteArray(Charsets.UTF_8)))
    }

    private fun document(catalog: RuleCatalog, generatedOn: String? = null): String {
        val parts = unzip(bytes = DocxCatalogWriter.write(catalog = catalog, generatedOn = generatedOn))

        return parts.getValue(key = "word/document.xml")
    }

    // ── package structure ─────────────────────────────────────────────────────

    @Test
    fun `writes every part the format requires`() {
        val parts = unzip(bytes = DocxCatalogWriter.write(catalog = warehouseCatalog()))

        assertEquals(
            expected = listOf(
                "[Content_Types].xml",
                "_rels/.rels",
                "word/_rels/document.xml.rels",
                "word/styles.xml",
                "word/numbering.xml",
                "word/document.xml",
            ),
            actual = parts.keys.toList(),
        )
    }

    @Test
    fun `puts the content types part first`() {
        // Some readers look for it at the start rather than scanning the whole archive.
        val parts = unzip(bytes = DocxCatalogWriter.write(catalog = warehouseCatalog()))

        assertEquals(expected = "[Content_Types].xml", actual = parts.keys.first())
    }

    @Test
    fun `every part is well-formed xml`() {
        val parts = unzip(bytes = DocxCatalogWriter.write(catalog = warehouseCatalog()))

        parts.forEach { (path, xml) -> parseXml(xml = xml) }
        assertEquals(expected = 6, actual = parts.size)
    }

    @Test
    fun `declares a content type for every part that needs one`() {
        val parts = unzip(bytes = DocxCatalogWriter.write(catalog = warehouseCatalog()))
        val contentTypes = parts.getValue(key = "[Content_Types].xml")

        listOf("/word/document.xml", "/word/styles.xml", "/word/numbering.xml").forEach { part ->
            assertTrue(
                actual = contentTypes.contains(other = "PartName=\"$part\""),
                message = "Undeclared part $part makes the package unopenable",
            )
        }
    }

    // ── determinism ───────────────────────────────────────────────────────────

    @Test
    fun `writing the same catalog twice produces identical bytes`() {
        // ZIP entries record a timestamp by default, which would make every export differ from the
        // last one and any diff or checksum of the document meaningless.
        assertContentEquals(
            expected = DocxCatalogWriter.write(catalog = warehouseCatalog()),
            actual = DocxCatalogWriter.write(catalog = warehouseCatalog()),
        )
    }

    // ── content ───────────────────────────────────────────────────────────────

    @Test
    fun `names every rule in the document`() {
        val catalog = warehouseCatalog()
        val document = document(catalog = catalog)

        catalog.rules.forEach { rule ->
            assertTrue(
                actual = document.contains(other = ">${rule.id}<"),
                message = "Rule '${rule.id}' is missing from the document",
            )
        }
    }

    @Test
    fun `writes the description, the sentences and the technical condition`() {
        val document = document(catalog = warehouseCatalog())

        assertTrue(
            actual = document.contains(
                other = "Gold-tier customers shipping on an express service get the premium service assessment."
            ),
            message = "The description is missing",
        )
        assertTrue(actual = document.contains(other = "Customer › Tier is &quot;gold&quot;"))
        assertTrue(actual = document.contains(other = "Applies when all of the following are true"))
        assertTrue(
            actual = document.contains(
                other = "shipment.customer.tier equals &quot;gold&quot; and " +
                    "shipment.service contains &quot;express&quot;"
            ),
            message = "The rule-language condition is missing",
        )
    }

    @Test
    fun `every index link points at a bookmark that exists`() {
        val document = document(catalog = warehouseCatalog())
        val anchors = Regex(pattern = "w:anchor=\"([^\"]+)\"").findAll(input = document)
            .map { match -> match.groupValues[1] }
            .toSet()
        val bookmarks = Regex(pattern = "w:bookmarkStart [^>]*w:name=\"([^\"]+)\"").findAll(input = document)
            .map { match -> match.groupValues[1] }
            .toSet()

        assertTrue(actual = anchors.isNotEmpty(), message = "The document has no links at all")
        assertEquals(
            expected = emptySet(),
            actual = anchors - bookmarks,
            message = "These links lead nowhere",
        )
    }

    @Test
    fun `dates the document only when the caller supplies a date`() {
        assertTrue(actual = document(catalog = warehouseCatalog(), generatedOn = "2026-08-01")
            .contains(other = "Generated 2026-08-01"))
        assertFalse(actual = document(catalog = warehouseCatalog()).contains(other = "Generated "))
    }

    @Test
    fun `nests a grouped condition one bullet level deeper`() {
        val document = document(
            catalog = catalogOf(
                rules = """
                    rule "r" {
                      when
                        (amount >= 1 or amount <= 0)
                        and purpose contains "x"
                      then
                        label "a"
                    }
                """.trimIndent()
            )
        )

        assertTrue(actual = document.contains(other = "Any of the following is true:"))
        assertTrue(
            actual = document.contains(other = "<w:ilvl w:val=\"1\"/>"),
            message = "A nested group must indent, or the boolean structure is lost",
        )
    }

    // ── escaping ──────────────────────────────────────────────────────────────

    @Test
    fun `escapes the characters that would corrupt the package`() {
        // A description is free text. One unescaped ampersand makes document.xml malformed, and Word
        // reports that as a corrupt file rather than as a formatting problem.
        val bytes = DocxCatalogWriter.write(
            catalog = catalogOf(
                rules = """
                    rule "r" {
                      description "Tom & Jerry <b> \"quoted\" & 'single'"
                      when
                        amount >= 1
                      then
                        label "a"
                    }
                """.trimIndent()
            )
        )
        val document = unzip(bytes = bytes).getValue(key = "word/document.xml")

        parseXml(xml = document)
        assertTrue(actual = document.contains(other = "Tom &amp; Jerry &lt;b&gt;"), message = document)
        assertFalse(
            actual = document.contains(other = "Tom & Jerry"),
            message = "A raw ampersand survived escaping",
        )
    }

    @Test
    fun `drops control characters xml cannot represent`() {
        val catalog = RuleCatalogBuilder.build(
            projectName = "p",
            entryId = "e",
            files = listOf(
                ParsedRuleFile(
                    relativePath = "r.rule",
                    rules = Parser(
                        input = "rule \"r\" {\n  description \"ab\"\n  when\n    amount >= 1\n" +
                            "  then\n    label \"a\"\n}"
                    ).parseRules(),
                )
            ),
        )
        val document = unzip(bytes = DocxCatalogWriter.write(catalog = catalog))
            .getValue(key = "word/document.xml")

        // XML 1.0 cannot encode a BEL at all, not even as a numeric reference, so it has to go.
        parseXml(xml = document)
        assertTrue(actual = document.contains(other = ">ab<"), message = document)
    }

    // ── edge cases ────────────────────────────────────────────────────────────

    @Test
    fun `an entry with no rules still produces an openable document`() {
        val catalog = RuleCatalog(
            projectName = "empty",
            entryId = "e",
            schemaPath = null,
            files = emptyList(),
        )
        val parts = unzip(bytes = DocxCatalogWriter.write(catalog = catalog))

        parts.forEach { (_, xml) -> parseXml(xml = xml) }
        assertTrue(
            actual = parts.getValue(key = "word/document.xml")
                .contains(other = "No rules are defined in this entry.")
        )
    }

    @Test
    fun `the body ends with a paragraph rather than a table`() {
        // Word offers to repair a document whose body ends on a table.
        val document = document(catalog = warehouseCatalog())
        val body = document.substringAfter(delimiter = "<w:body>").substringBefore(delimiter = "<w:sectPr")

        assertTrue(
            actual = body.trimEnd().endsWith(suffix = "</w:p>"),
            message = "Body ends with: ${body.takeLast(n = 120)}",
        )
    }
}
