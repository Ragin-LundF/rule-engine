package ui.dock

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The background a span actually sets, or null when it sets none.
 *
 * [SpanStyle.background] defaults to [Color.Unspecified] rather than to null, so a plain `!= null` test
 * matches every span in the document — which is exactly the trap that made the first draft of this test
 * pass nothing and then fail everything.
 */
private val SpanStyle.declaredBackground: Color?
    get() = background.takeIf { color -> color != Color.Unspecified }

/**
 * The one invariant the dock's preview rests on: a highlight is a background, so it stacks with syntax
 * colouring instead of replacing it.
 *
 * Colours are passed explicitly throughout — the defaults read the live theme, and a test that depended
 * on that would assert whatever palette happened to be loaded.
 */
class DockHighlightTest {

    private val context = Color(color = 0xFF112233)
    private val focus = Color(color = 0xFF445566)

    private fun highlighted(
        source: AnnotatedString,
        vararg highlights: DockHighlight,
    ): AnnotatedString = source.withDockHighlights(
        highlights = highlights.toList(),
        contextColor = context,
        focusColor = focus,
    )

    /** A syntax span and a highlight span over the same characters, both present afterwards. */
    @Test
    fun `a highlight keeps the syntax colour underneath it`() {
        val source = buildAnnotatedString {
            withStyle(style = SpanStyle(color = Color.Red, fontWeight = FontWeight.Bold)) {
                append("rule")
            }
            append(" x")
        }

        val result = highlighted(
            source,
            DockHighlight(range = 0..3, kind = DockHighlightKind.CONTEXT),
        )

        val atZero = result.spanStyles.filter { span -> span.start <= 0 && span.end > 0 }
        assertEquals(expected = Color.Red, actual = atZero.firstNotNullOf { span -> span.item.color })
        assertEquals(
            expected = FontWeight.Bold,
            actual = atZero.firstNotNullOf { span -> span.item.fontWeight },
        )
        assertEquals(
            expected = context,
            actual = atZero.firstNotNullOf { span -> span.item.declaredBackground },
        )
        assertEquals(expected = source.text, actual = result.text)
    }

    /** The narrower mark is added last, so it is the one that paints. */
    @Test
    fun `focus is applied after context so it wins where they overlap`() {
        val source = AnnotatedString(text = "abcdefghij")

        val result = highlighted(
            source,
            // Deliberately listed focus-first: the ordering must come from the kind, not the list.
            DockHighlight(range = 3..5, kind = DockHighlightKind.FOCUS),
            DockHighlight(range = 0..9, kind = DockHighlightKind.CONTEXT),
        )

        val backgrounds = result.spanStyles.mapNotNull { span -> span.item.declaredBackground }
        assertEquals(expected = listOf(context, focus), actual = backgrounds)
    }

    @Test
    fun `a range past the end of the text is clamped rather than thrown`() {
        val source = AnnotatedString(text = "short")

        val result = highlighted(
            source,
            DockHighlight(range = 2..9_999, kind = DockHighlightKind.CONTEXT),
        )

        val span = result.spanStyles.single()
        assertEquals(expected = 2, actual = span.start)
        assertEquals(expected = source.text.length, actual = span.end)
    }

    @Test
    fun `a negative start is clamped to the beginning`() {
        val result = highlighted(
            AnnotatedString(text = "short"),
            DockHighlight(range = -20..2, kind = DockHighlightKind.FOCUS),
        )

        val span = result.spanStyles.single()
        assertEquals(expected = 0, actual = span.start)
        assertEquals(expected = 3, actual = span.end)
    }

    /** An empty range marks nothing rather than a zero-width sliver. */
    @Test
    fun `an empty range adds no span`() {
        val result = highlighted(
            AnnotatedString(text = "abc"),
            // Built rather than written as `2..1`: an empty range is the point of the test, and a
            // descending literal is a static-analysis error in its own right.
            DockHighlight(range = IntRange(start = 2, endInclusive = 1), kind = DockHighlightKind.CONTEXT),
        )

        assertTrue(actual = result.spanStyles.isEmpty())
    }

    @Test
    fun `no highlights and empty text are both the identity`() {
        val source = AnnotatedString(text = "abc")
        assertEquals(expected = source, actual = highlighted(source))

        val empty = AnnotatedString(text = "")
        assertEquals(
            expected = empty,
            actual = highlighted(empty, DockHighlight(range = 0..2, kind = DockHighlightKind.FOCUS)),
        )
    }

    /**
     * The real annotator, not a hand-built stand-in: this is the case that regresses if a highlighter
     * ever starts setting `background` itself.
     */
    @Test
    fun `real rule highlighting survives being layered`() {
        val dsl = """
            rule "a" {
              when { amount >= 300 }
              then { label rent }
            }
        """.trimIndent()

        val annotated = ui.dsl.annotateRule(text = dsl, schema = null, actions = null)
        val syntaxSpans = annotated.spanStyles.size
        assertTrue(
            actual = syntaxSpans > 0,
            message = "annotateRule produced no spans, so this test would prove nothing",
        )
        assertNull(
            actual = annotated.spanStyles.firstOrNull { span -> span.item.declaredBackground != null },
            message = "a highlighter that sets background would flatten the dock's own marks",
        )

        val result = annotated.withDockHighlights(
            highlights = listOf(DockHighlight(range = 0..dsl.length - 1, kind = DockHighlightKind.CONTEXT)),
            contextColor = context,
            focusColor = focus,
        )

        assertEquals(expected = syntaxSpans + 1, actual = result.spanStyles.size)
        assertEquals(expected = dsl, actual = result.text)
    }
}
