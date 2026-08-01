package ui

import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.font.FontStyle
import ui.autocompletion.CompletionKind
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Characterization tests for YAML completions and highlighting, neither of which had any.
 *
 * The completion side is a lookup table with eleven mutually exclusive cases; these pin which case
 * wins for a given cursor position, which is the part a restructure could silently reorder.
 */
class YamlHighlighterTest {

    private fun completions(
        currentKey: String? = null,
        parentKey: String? = null,
        isValue: Boolean = false,
        isListItem: Boolean = false,
        indent: Int = 0,
        editorType: YamlEditorType = YamlEditorType.FIELD_SCHEMA,
    ) = buildYamlCompletions(
        context = YamlCursorContext(
            currentKey = currentKey,
            parentKey = parentKey,
            isValue = isValue,
            isListItem = isListItem,
            currentIndent = indent,
        ),
        editorType = editorType,
    )

    private fun labels(items: List<ui.autocompletion.CompletionItem>) = items.map { it.label }

    // ── values ────────────────────────────────────────────────────────────────

    @Test
    fun `the value of type offers field types`() {
        val items = completions(currentKey = "type", isValue = true)

        assertTrue(actual = "text" in labels(items), message = "got: ${labels(items)}")
        assertTrue(actual = items.all { it.kind == CompletionKind.LITERAL })
    }

    @Test
    fun `the value of format offers date patterns`() {
        assertTrue(actual = "dd.MM.yyyy" in labels(completions(currentKey = "format", isValue = true)))
    }

    @Test
    fun `list items under normalizers, operators and argTypes each offer their own vocabulary`() {
        assertTrue(actual = completions(parentKey = "normalizers", isListItem = true).isNotEmpty())
        assertTrue(
            actual = completions(parentKey = "operators", isListItem = true).all { it.kind == CompletionKind.OPERATOR },
        )
        assertTrue(actual = completions(parentKey = "argTypes", isListItem = true).isNotEmpty())
    }

    // ── keys ──────────────────────────────────────────────────────────────────

    @Test
    fun `indent 4 offers the properties of a definition, per editor type`() {
        val fieldKeys = labels(completions(indent = 4, editorType = YamlEditorType.FIELD_SCHEMA))
        val actionKeys = labels(completions(indent = 4, editorType = YamlEditorType.ACTION_SCHEMA))

        assertTrue(actual = "operators" in fieldKeys, message = "got: $fieldKeys")
        assertEquals(expected = listOf("argTypes"), actual = actionKeys)
    }

    @Test
    fun `indent 0 offers the document's top-level keys`() {
        assertEquals(
            expected = listOf("schema", "fields"),
            actual = labels(completions(indent = 0, editorType = YamlEditorType.FIELD_SCHEMA)),
        )
        assertEquals(
            expected = listOf("actions"),
            actual = labels(completions(indent = 0, editorType = YamlEditorType.ACTION_SCHEMA)),
        )
    }

    /**
     * Every key case appends a different suffix, and the differences are deliberate: `argTypes` is
     * always a list, a field property's value is typed on the same line, and an action schema's only
     * top-level key opens a block. Pinned exactly — asserting "not a list" let a changed suffix
     * through once already.
     */
    @Test
    fun `each key case inserts its own exact suffix`() {
        assertEquals(
            expected = "type:",
            actual = completions(indent = 4, editorType = YamlEditorType.FIELD_SCHEMA).first().insertText,
        )
        assertEquals(
            expected = "argTypes: []",
            actual = completions(indent = 4, editorType = YamlEditorType.ACTION_SCHEMA).single().insertText,
        )
        assertEquals(
            expected = "schema: ",
            actual = completions(indent = 0, editorType = YamlEditorType.FIELD_SCHEMA).first().insertText,
        )
        assertEquals(
            expected = "actions:",
            actual = completions(indent = 0, editorType = YamlEditorType.ACTION_SCHEMA).single().insertText,
        )
        assertEquals(
            expected = "schema: ",
            actual = completions(indent = 2, parentKey = "fields").first().insertText,
        )
    }

    /** A value case must beat a key case: at indent 4 on a `type:` line, the types win. */
    @Test
    fun `a value position outranks the key position at the same indent`() {
        val items = completions(currentKey = "type", isValue = true, indent = 4)

        assertTrue(actual = "text" in labels(items), message = "got: ${labels(items)}")
        assertTrue(actual = "operators" !in labels(items))
    }

    @Test
    fun `a position that matches nothing offers nothing`() {
        assertEquals(expected = emptyList(), actual = completions(indent = 7))
    }

    // ── highlighting ──────────────────────────────────────────────────────────

    private fun stylesOn(annotated: AnnotatedString, token: String): List<SpanStyle> {
        val at = annotated.text.indexOf(token)
        require(at >= 0) { "'$token' not in ${annotated.text}" }
        return annotated.spanStyles.filter { at >= it.start && at < it.end }.map { it.item }
    }

    @Test
    fun `empty text is returned unstyled`() {
        val result = annotateYaml(text = "", editorType = YamlEditorType.FIELD_SCHEMA)

        assertEquals(expected = "", actual = result.text)
        assertTrue(actual = result.spanStyles.isEmpty())
    }

    @Test
    fun `the text is never altered, only annotated`() {
        val src = "schema: demo\nfields:\n  amount:\n    type: decimal\n"

        assertEquals(expected = src, actual = annotateYaml(text = src, editorType = YamlEditorType.FIELD_SCHEMA).text)
    }

    @Test
    fun `a comment line is muted and italic`() {
        val result = annotateYaml(text = "# a note\nschema: demo", editorType = YamlEditorType.FIELD_SCHEMA)

        assertTrue(actual = stylesOn(annotated = result, token = "# a note").any { it.fontStyle == FontStyle.Italic })
        assertTrue(actual = stylesOn(annotated = result, token = "schema").none { it.fontStyle == FontStyle.Italic })
    }

    @Test
    fun `keys, colons and values are coloured separately`() {
        val src = "schema: demo"
        val result = annotateYaml(text = src, editorType = YamlEditorType.FIELD_SCHEMA)

        val key = stylesOn(annotated = result, token = "schema").firstOrNull()?.color
        val value = stylesOn(annotated = result, token = "demo").firstOrNull()?.color
        assertTrue(actual = key != null && value != null, message = "got ${result.spanStyles}")
        assertTrue(actual = key != value, message = "a key must not look like its value")
    }

    @Test
    fun `a list item's dash and value are styled`() {
        val src = "fields:\n  amount:\n    operators:\n      - gte\n"
        val result = annotateYaml(text = src, editorType = YamlEditorType.FIELD_SCHEMA)

        assertTrue(actual = stylesOn(annotated = result, token = "gte").isNotEmpty())
    }

    /** Nesting is tracked by indent, so a de-indent must pop back to the shallower parent. */
    @Test
    fun `a de-indented key is styled as the shallower level it returns to`() {
        val src = "fields:\n  amount:\n    type: decimal\nschema: demo"
        val result = annotateYaml(text = src, editorType = YamlEditorType.FIELD_SCHEMA)

        val topLevel = stylesOn(annotated = result, token = "fields").firstOrNull()?.color
        val returned = stylesOn(annotated = result, token = "schema").firstOrNull()?.color
        assertEquals(expected = topLevel, actual = returned)
    }

    @Test
    fun `a key-only line still colours its key and colon`() {
        val result = annotateYaml(text = "fields:", editorType = YamlEditorType.FIELD_SCHEMA)

        assertEquals(expected = 2, actual = result.spanStyles.size, message = "key + colon")
    }
}
