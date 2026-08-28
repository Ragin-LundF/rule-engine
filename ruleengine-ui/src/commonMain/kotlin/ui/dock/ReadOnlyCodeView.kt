package ui.dock

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import ui.TextPrimary
import ui.theme.ThemeController

/**
 * A file, shown and not editable.
 *
 * The one genuinely new primitive in the dock, because nothing in the module rendered highlighted text
 * without also letting it be typed into. `SelectionContainer` + `Text` rather than a
 * `BasicTextField(readOnly = true)`, for four reasons that all point the same way:
 *
 * - a text field takes focus, so clicking the preview would pull focus off the row or the editor the
 *   reader was working in, and a caret in a surface that cannot be typed into reads as a bug;
 * - the previewed text is regenerated on every edit, and rebuilding a `TextFieldValue` per
 *   recomposition is the failure `YamlEditorPane` already documents at length;
 * - a text field owns its own scrolling and fights the horizontal scroll a long DSL line needs, while
 *   `Text` composes with both;
 * - scrolling the marked line into view is `onTextLayout` plus `getLineTop`, which is direct here and
 *   would mean writing a selection into the value there.
 *
 * Selection and copy still work — that is what `SelectionContainer` is for. The dock's header carries a
 * copy button as well, for the whole file at once.
 */
@Suppress("FunctionNaming")
@Composable
internal fun ReadOnlyCodeView(
    text: String,
    annotate: (String) -> AnnotatedString,
    highlights: List<DockHighlight>,
    modifier: Modifier = Modifier,
) {
    // Keyed on the theme as well as on the inputs. `annotate` reads the palette through
    // `ThemeController`, and a colour read inside `remember` does not subscribe to it — so without
    // `isDark` in the key the preview would keep last theme's colours until the text next changed.
    val annotated = remember(text, annotate, ThemeController.isDark) { annotate(text) }
    val marked = remember(annotated, highlights) { annotated.withDockHighlights(highlights = highlights) }

    val vertical = rememberScrollState()
    val horizontal = rememberScrollState()
    var layout by remember { mutableStateOf<TextLayoutResult?>(value = null) }

    // The point of marking the focus line is lost if it is three screens down in a long file.
    val focus = highlights.firstOrNull { highlight -> highlight.kind == DockHighlightKind.FOCUS }
        ?: highlights.firstOrNull()
    LaunchedEffect(key1 = focus, key2 = layout) {
        val result = layout ?: return@LaunchedEffect
        val offset = focus?.range?.first ?: return@LaunchedEffect
        if (offset > result.layoutInput.text.length) return@LaunchedEffect
        val line = result.getLineForOffset(offset = offset)
        vertical.animateScrollTo(value = result.getLineTop(lineIndex = line).toInt())
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .verticalScroll(state = vertical),
    ) {
        SelectionContainer {
            Text(
                text = marked,
                style = MaterialTheme.typography.body2.copy(fontFamily = FontFamily.Monospace),
                color = TextPrimary,
                onTextLayout = { result -> layout = result },
                modifier = Modifier
                    .horizontalScroll(state = horizontal)
                    .padding(horizontal = 12.dp, vertical = 8.dp),
            )
        }
    }
}
