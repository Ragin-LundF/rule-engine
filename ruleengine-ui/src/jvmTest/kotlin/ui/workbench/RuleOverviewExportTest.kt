package ui.workbench

import kotlinx.coroutines.CoroutineScope
import ruleengine.schema.FieldSchemaLoader
import ui.editor.rules.RuleEditorState
import java.io.ByteArrayInputStream
import java.nio.file.Path
import java.time.LocalDate
import java.util.zip.ZipInputStream
import kotlin.coroutines.EmptyCoroutineContext
import kotlin.io.path.absolutePathString
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

/**
 * Exercises the export against the real `warehouse-shipments` project, driving the same state the
 * workbench holds — so what these tests build is what the toolbar button builds.
 */
class RuleOverviewExportTest {

    private val projectDir: Path =
        Path.of("../ruleengine-core/src/test/resources/warehouse-shipments").toAbsolutePath().normalize()

    /** A state loaded the way the workbench loads it, with one rule file open. */
    private fun loadedState(): RuleEditorState {
        val state = RuleEditorState(scope = CoroutineScope(context = EmptyCoroutineContext))
        state.manifestBaseDir.value = projectDir.absolutePathString()
        state.parsedManifest.value = ruleengine.manifest.ManifestLoader.load(
            path = projectDir.resolve("manifest.yaml")
        )
        state.selectedManifestEntry.value = "shipment-assessment"
        state.parsedSchema.value = FieldSchemaLoader.load(path = projectDir.resolve("schema.yaml"))
        state.loadSingleManifestRuleFile("rules/route-risk.rule")

        return state
    }

    private fun ready(format: RuleOverviewExport.Format): RuleOverviewExport.Result.Ready {
        val result = RuleOverviewExport.export(
            state = loadedState(),
            format = format,
            today = LocalDate.of(2026, 8, 1),
        )
        assertIs<RuleOverviewExport.Result.Ready>(value = result)

        return result
    }

    @Test
    fun `exports the whole entry, not only the open file`() {
        // The open file is one of three. Which file the author happens to be editing is a detail of
        // editing; the document describes the rule set a customer is assessed against.
        assertEquals(expected = 13, actual = ready(format = RuleOverviewExport.Format.MARKDOWN).ruleCount)
    }

    @Test
    fun `names the file after the project and the entry`() {
        assertEquals(
            expected = "warehouse-shipments-shipment-assessment.md",
            actual = ready(format = RuleOverviewExport.Format.MARKDOWN).fileName,
        )
        assertEquals(
            expected = "warehouse-shipments-shipment-assessment.docx",
            actual = ready(format = RuleOverviewExport.Format.WORD).fileName,
        )
    }

    @Test
    fun `markdown carries the rules and the field labels from the schema`() {
        val markdown = ready(format = RuleOverviewExport.Format.MARKDOWN).bytes.toString(Charsets.UTF_8)

        assertTrue(actual = markdown.startsWith(prefix = "# Rule overview — warehouse-shipments"))
        assertTrue(actual = markdown.contains(other = "### tracking-gap"))
        // "Customer › Tier" only appears when the schema reached the renderer.
        assertTrue(actual = markdown.contains(other = "Customer › Tier is \"gold\""), message = markdown)
    }

    @Test
    fun `word export is a zip package`() {
        val bytes = ready(format = RuleOverviewExport.Format.WORD).bytes

        assertEquals(expected = 'P'.code.toByte(), actual = bytes[0])
        assertEquals(expected = 'K'.code.toByte(), actual = bytes[1])
    }

    /** The `.docx` is a compressed archive, so its text has to be read out of the document part. */
    private fun wordDocumentXml(bytes: ByteArray): String {
        ZipInputStream(ByteArrayInputStream(bytes)).use { zip ->
            while (true) {
                val entry = zip.nextEntry ?: break
                if (entry.name == "word/document.xml") {
                    return zip.readBytes().toString(Charsets.UTF_8)
                }
            }
        }

        return ""
    }

    @Test
    fun `only the word document is dated`() {
        val markdown = ready(format = RuleOverviewExport.Format.MARKDOWN).bytes.toString(Charsets.UTF_8)
        val word = wordDocumentXml(bytes = ready(format = RuleOverviewExport.Format.WORD).bytes)

        // The wiki page is regenerated and keeps its own history; a date in it would be one more
        // line changing on every export. The handed-over document needs one.
        assertTrue(actual = !markdown.contains(other = "2026-08-01"), message = "Markdown must not be dated")
        assertTrue(actual = word.contains(other = "Generated 2026-08-01"), message = "Word must be dated")
    }

    @Test
    fun `word document covers the whole entry`() {
        val document = wordDocumentXml(bytes = ready(format = RuleOverviewExport.Format.WORD).bytes)

        assertTrue(actual = document.contains(other = ">premium-service-promise<"), message = "first file")
        assertTrue(actual = document.contains(other = ">tracking-gap<"), message = "last file")
    }

    @Test
    fun `reports why it cannot export when no entry is selected`() {
        val state = RuleEditorState(scope = CoroutineScope(context = EmptyCoroutineContext))

        val result = RuleOverviewExport.export(state = state, format = RuleOverviewExport.Format.MARKDOWN)

        assertIs<RuleOverviewExport.Result.Unavailable>(value = result)
        assertTrue(
            actual = result.reason.contains(other = "manifest entry"),
            message = result.reason,
        )
    }

    @Test
    fun `reports an entry that has no rules rather than writing an empty document`() {
        val state = loadedState()
        state.parsedManifest.value = ruleengine.manifest.ProjectManifest(
            name = "empty",
            entries = listOf(
                ruleengine.manifest.ManifestEntry(id = "shipment-assessment", rules = emptyList())
            ),
        )

        val result = RuleOverviewExport.export(state = state, format = RuleOverviewExport.Format.MARKDOWN)

        assertIs<RuleOverviewExport.Result.Unavailable>(value = result)
        assertTrue(actual = result.reason.contains(other = "no rules"), message = result.reason)
    }

    @Test
    fun `detects that the open rule file has unsaved changes`() {
        // The export reads from disk, so the toolbar has to be able to say when the buffer differs.
        val state = loadedState()
        assertTrue(actual = !state.currentRuleFileHasUnsavedChanges())

        state.ruleValue.value = state.ruleValue.value.copy(text = "rule \"edited\" {\n  when\n")
        assertTrue(actual = state.currentRuleFileHasUnsavedChanges())
    }
}
