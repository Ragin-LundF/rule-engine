package ui.editor.rules

import ruleengine.dsl.parser.Parser
import ui.builder.OperatorOptions
import ui.diagrams.model.RuleSource
import ui.workbench.builderCatalogVariablesFrom
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * The open editor buffer standing in for the file it was loaded from.
 *
 * Without it the Builder's operand picker reads only what is on disk, so a variable does not exist as
 * far as the dropdowns are concerned until the file is saved — the row that declares it and the row
 * that reads it can never both be on screen.
 */
class OpenBufferOverlayTest {

    private val paths = listOf("a.rule", "b.rule")
    private val saved = mapOf("a.rule" to "saved a", "b.rule" to "saved b")

    @Test
    fun `the open file is replaced by the buffer and the others are left alone`() {
        val merged = withOpenBuffer(
            paths = paths,
            saved = saved,
            openPath = "b.rule",
            bufferText = "edited b",
        )

        assertEquals(expected = listOf("a.rule" to "saved a", "b.rule" to "edited b"), actual = merged)
    }

    @Test
    fun `nothing is replaced when no file is open`() {
        val merged = withOpenBuffer(paths = paths, saved = saved, openPath = null, bufferText = "ignored")

        assertEquals(expected = listOf("a.rule" to "saved a", "b.rule" to "saved b"), actual = merged)
    }

    /** Manifest order decides variable scope, so it has to survive the substitution. */
    @Test
    fun `manifest order is kept`() {
        val merged = withOpenBuffer(
            paths = listOf("b.rule", "a.rule"),
            saved = saved,
            openPath = "a.rule",
            bufferText = "edited a",
        )

        assertEquals(expected = listOf("b.rule", "a.rule"), actual = merged.map { (path, _) -> path })
    }

    /** A file created but never written still contributes what its buffer declares. */
    @Test
    fun `an unsaved file contributes its buffer`() {
        val merged = withOpenBuffer(
            paths = paths + "c.rule",
            saved = saved,
            openPath = "c.rule",
            bufferText = "new c",
        )

        assertEquals(expected = "c.rule" to "new c", actual = merged.last())
    }

    @Test
    fun `a path with no content and no buffer is dropped`() {
        val merged = withOpenBuffer(
            paths = paths + "gone.rule",
            saved = saved,
            openPath = "a.rule",
            bufferText = "edited a",
        )

        assertEquals(expected = listOf("a.rule", "b.rule"), actual = merged.map { (path, _) -> path })
    }

    /**
     * The behaviour the user sees: add an `add` row to the open file, and the list it writes is
     * offered by the operand picker straight away.
     */
    @Test
    fun `a variable added in the buffer reaches the operand catalog`() {
        val savedText = """
            rule "billing" {
              description "d"
              when
                amount >= 1
              then
                label "billing"
            }
        """.trimIndent()
        val editedText = savedText.replace(
            oldValue = """    label "billing"""",
            newValue = """    label "billing"
                add "billing" to topics""",
        )

        assertEquals(
            expected = emptyList(),
            actual = catalogOf(text = savedText),
            message = "the saved file declares no variable",
        )
        assertEquals(
            expected = listOf("\$topics" to OperatorOptions.LIST_VARIABLE_TYPE),
            actual = catalogOf(text = editedText),
        )
    }

    private fun catalogOf(text: String): List<Pair<String, String>> {
        val merged = withOpenBuffer(
            paths = listOf("topics.rule"),
            saved = mapOf("topics.rule" to ""),
            openPath = "topics.rule",
            bufferText = text,
        )
        val files = merged.map { (path, content) ->
            RuleSource(relativePath = path, rules = Parser(input = content).parseRules())
        }
        return builderCatalogVariablesFrom(files = files, uptoRuleId = "billing")
            .map { info -> info.id to info.type }
    }
}
