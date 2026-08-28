package ui.dock

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import ui.PrimaryBlue
import ui.PrimaryGlow

/**
 * Why a stretch of the previewed file is marked.
 *
 * Two levels rather than one, because the two questions a reader has are different sizes: *which rule
 * am I editing* is a block, and *which line is the row I just clicked* is one line inside it. Marking
 * only the line loses the rule; marking only the rule loses the row.
 */
enum class DockHighlightKind {
    /** The whole selected declaration — the open rule, or a field with everything under it. */
    CONTEXT,

    /** The one line the selection points at, inside the context. */
    FOCUS,
}

/** A stretch of the previewed file to mark, as a character range into that file. */
data class DockHighlight(val range: IntRange, val kind: DockHighlightKind)

/**
 * [this] with a background behind each highlight, and nothing else touched.
 *
 * **Background only, and that is what makes the two layers compose.** The syntax highlighters set
 * `color`, `fontWeight`, `fontStyle` and text decoration, and never `background` — so a background laid
 * over their output is additive. The dock this replaces set `color = PrimaryBlue` as well, which is why
 * the one line a reader was looking at was the one line that lost its syntax colours.
 *
 * [DockHighlightKind.CONTEXT] spans are applied before [DockHighlightKind.FOCUS] ones so the narrower
 * mark wins where they overlap, which they normally do — the focus line sits inside its context block.
 *
 * Ranges are clamped rather than trusted: they are computed from a *generated* file, and the state that
 * generated it can change between the two. A stale range is a wrong highlight; an out-of-bounds one
 * would be a crash.
 *
 * The colours are parameters with theme-reading defaults so a test can assert the layering without a
 * theme. Note [PrimaryGlow] is already `PrimaryBlue.copy(alpha = 0.15f)`, so the focus level cannot be
 * made by dimming it further — it is a stronger alpha of the same hue.
 */
internal fun AnnotatedString.withDockHighlights(
    highlights: List<DockHighlight>,
    contextColor: Color = PrimaryGlow,
    focusColor: Color = PrimaryBlue.copy(alpha = FOCUS_ALPHA),
): AnnotatedString {
    if (highlights.isEmpty() || isEmpty()) return this

    val builder = AnnotatedString.Builder(text = this)
    DockHighlightKind.entries.forEach { kind ->
        val color = if (kind == DockHighlightKind.CONTEXT) contextColor else focusColor
        highlights
            .filter { highlight -> highlight.kind == kind }
            .forEach { highlight ->
                val start = highlight.range.first.coerceIn(minimumValue = 0, maximumValue = length)
                val end = (highlight.range.last + 1).coerceIn(minimumValue = 0, maximumValue = length)
                if (end > start) {
                    builder.addStyle(style = SpanStyle(background = color), start = start, end = end)
                }
            }
    }
    return builder.toAnnotatedString()
}

/** Stronger than [PrimaryGlow], because the focus line sits on top of a context block. */
private const val FOCUS_ALPHA: Float = 0.30f
