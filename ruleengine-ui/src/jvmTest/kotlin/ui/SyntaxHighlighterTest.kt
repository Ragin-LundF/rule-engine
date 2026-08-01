package ui

import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.style.TextDecoration
import ruleengine.core.errors.Severity
import ruleengine.core.errors.ValidationDiagnostic
import ruleengine.schema.ActionSchemaLoader
import ruleengine.schema.FieldSchemaLoader
import ui.dsl.annotateRule
import ui.theme.ThemeController
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

private val SCHEMA_TEXT = """
schema: transaction-v1
fields:
  purpose:
    type: text
    operators: [contains, equals]
  amount:
    type: decimal
    operators: [gte, lte]
""".trimIndent()

private val ACTIONS_TEXT = """
actions:
  label:
    argTypes: [string]
""".trimIndent()

/**
 * Characterization tests for the rule-DSL highlighter, which had none.
 *
 * They assert on the spans the builder emits rather than on colours by name, so they pin *which
 * token got which treatment* — the thing a refactor of the token loop could silently change.
 */
class SyntaxHighlighterTest {

    private val schema = FieldSchemaLoader.loadFromString(content = SCHEMA_TEXT, nameHint = "transaction-v1")
    private val actions = ActionSchemaLoader.loadFromString(content = ACTIONS_TEXT)

    private fun annotate(text: String, diagnostics: List<ValidationDiagnostic> = emptyList()) =
        annotateRule(text = text, schema = schema, actions = actions, diagnostics = diagnostics)

    /** Every style whose range covers the first occurrence of [token]. */
    private fun stylesOn(annotated: AnnotatedString, token: String): List<SpanStyle> {
        val at = annotated.text.indexOf(token)
        require(at >= 0) { "'$token' not in ${annotated.text}" }
        return annotated.spanStyles.filter { at >= it.start && at < it.end }.map { it.item }
    }

    private fun colorOf(annotated: AnnotatedString, token: String) =
        stylesOn(annotated = annotated, token = token).firstNotNullOfOrNull { it.color.takeIf { c -> c.alpha > 0f } }

    @Test
    fun `empty text is returned unstyled`() {
        val result = annotateRule(text = "", schema = schema, actions = actions)

        assertEquals(expected = "", actual = result.text)
        assertTrue(actual = result.spanStyles.isEmpty())
    }

    @Test
    fun `the text is never altered, only annotated`() {
        val src = "rule \"r\" {\n  when\n    purpose contains \"rent\"\n  then\n    label \"x\"\n}"

        assertEquals(expected = src, actual = annotate(text = src).text)
    }

    @Test
    fun `structure keywords, fields and actions each get their own colour`() {
        val result = annotate(text = "rule \"r\" {\n  when\n    purpose contains \"rent\"\n  then\n    label \"x\"\n}")

        val keyword = colorOf(annotated = result, token = "rule")
        val field = colorOf(annotated = result, token = "purpose")
        val action = colorOf(annotated = result, token = "label")

        assertEquals(
            expected = keyword,
            actual = colorOf(annotated = result, token = "when"),
            message = "when is structure",
        )
        assertTrue(actual = keyword != field, message = "a keyword must not look like a field")
        assertTrue(actual = field != action, message = "a field must not look like an action")
    }

    @Test
    fun `named operators and logic words are distinguished from plain identifiers`() {
        val result = annotate(
            text = "rule \"r\" {\n  when\n    purpose contains \"a\"\n    and amount >= 5\n  then\n    label \"x\"\n}",
        )

        assertTrue(actual = colorOf(annotated = result, token = "contains") != null)
        assertTrue(
            actual = colorOf(annotated = result, token = "and") != colorOf(annotated = result, token = "contains"),
            message = "logic words are their own category",
        )
    }

    @Test
    fun `strings and numbers get distinct colours`() {
        val result = annotate(text = "rule \"r\" {\n  when\n    amount >= 500\n  then\n    label \"x\"\n}")

        assertTrue(actual = colorOf(annotated = result, token = "500") != colorOf(annotated = result, token = "\"r\""))
    }

    @Test
    fun `a comment is italic and muted to the end of its line`() {
        val src = "# a note\nrule \"r\" {\n}"
        val result = annotate(text = src)

        val onComment = stylesOn(annotated = result, token = "# a note")
        assertTrue(actual = onComment.any { it.fontStyle == FontStyle.Italic }, message = "got: $onComment")
        // The rule keyword on the next line must be untouched by the comment span.
        assertTrue(actual = stylesOn(annotated = result, token = "rule").none { it.fontStyle == FontStyle.Italic })
    }

    /** A keyword inside a comment stays comment-coloured — the token pass skips commented offsets. */
    @Test
    fun `keywords inside a comment are not highlighted as keywords`() {
        val result = annotate(text = "# rule when then\nrule \"r\" {\n}")

        val onCommented = stylesOn(annotated = result, token = "rule")
        assertTrue(actual = onCommented.all { it.fontStyle == FontStyle.Italic }, message = "got: $onCommented")
    }

    @Test
    fun `an unterminated comment runs to the end of the text`() {
        val src = "rule \"r\" {\n} # trailing"
        val result = annotate(text = src)

        assertTrue(actual = stylesOn(annotated = result, token = "# trailing").any { it.fontStyle == FontStyle.Italic })
    }

    /** Half-written text is the normal state while typing, so a lexer failure must not escape. */
    @Test
    fun `text the lexer cannot read is returned rather than thrown`() {
        val result = annotate(text = "rule \"unterminated {{{ @@@")

        assertEquals(expected = "rule \"unterminated {{{ @@@", actual = result.text)
    }

    // ── diagnostic underlines ─────────────────────────────────────────────────

    @Test
    fun `a diagnostic with a position underlines from that column to end of line`() {
        val src = "rule \"r\" {\n  when\n    bad_field equals \"x\"\n}"
        val result = annotate(
            text = src,
            diagnostics = listOf(
                ValidationDiagnostic(severity = Severity.ERROR, message = "unknown", line = 3, column = 5),
            ),
        )

        val underlines = result.spanStyles.filter { it.item.textDecoration == TextDecoration.Underline }
        assertEquals(expected = 1, actual = underlines.size)
        assertEquals(expected = src.indexOf("bad_field"), actual = underlines.single().start)
        assertEquals(expected = src.indexOf("\n}"), actual = underlines.single().end)
    }

    @Test
    fun `errors and warnings underline in different colours`() {
        val src = "rule \"r\" {\n  when\n    a equals \"x\"\n}"
        val result = annotate(
            text = src,
            diagnostics = listOf(
                ValidationDiagnostic(severity = Severity.ERROR, message = "e", line = 1, column = 1),
                ValidationDiagnostic(severity = Severity.WARNING, message = "w", line = 2, column = 1),
            ),
        )

        val colors = result.spanStyles
            .filter { it.item.textDecoration == TextDecoration.Underline }
            .map { it.item.color }
        assertEquals(expected = 2, actual = colors.size)
        assertEquals(expected = 2, actual = colors.toSet().size, message = "severity must be visible in the colour")
    }

    @Test
    fun `a diagnostic with no line is skipped`() {
        val result = annotate(
            text = "rule \"r\" {\n}",
            diagnostics = listOf(ValidationDiagnostic(severity = Severity.ERROR, message = "no position")),
        )

        assertTrue(actual = result.spanStyles.none { it.item.textDecoration == TextDecoration.Underline })
    }

    /**
     * The highlighter bakes palette colours into its spans, so its output is theme-dependent.
     *
     * That is what makes it wrong to cache without the theme as a key: both editors `remember` this
     * result, and a snapshot read inside a remember block does not invalidate it. Switching theme
     * used to leave dark-mode token colours on a light background.
     */
    @Test
    fun `identifier colours differ between the light and dark palettes`() {
        val src = "rule \"r\" {\n  when\n    unknown_ident equals \"x\"\n  then\n    label \"y\"\n}"
        val wasDark = ThemeController.isDark
        try {
            ThemeController.isDark = true
            val dark = colorOf(annotated = annotate(text = src), token = "unknown_ident")
            ThemeController.isDark = false
            val light = colorOf(annotated = annotate(text = src), token = "unknown_ident")

            assertTrue(actual = dark != light, message = "both were $dark — the cache key would not matter")
        } finally {
            ThemeController.isDark = wasDark
        }
    }

    @Test
    fun `a diagnostic pointing past the end of the text is skipped`() {
        val result = annotate(
            text = "rule \"r\" {\n}",
            diagnostics = listOf(
                ValidationDiagnostic(severity = Severity.ERROR, message = "e", line = 99, column = 1),
            ),
        )

        assertTrue(actual = result.spanStyles.none { it.item.textDecoration == TextDecoration.Underline })
    }
}
