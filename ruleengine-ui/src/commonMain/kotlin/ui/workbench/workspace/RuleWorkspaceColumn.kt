package ui.workbench.workspace

import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.Divider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.testTag
import ui.BorderColor

/**
 * The middle column: the context bar on top, then the canvas with its drawer.
 *
 * Two things here are load-bearing.
 *
 * **It clips.** This is the one elastic column, and it is the only one whose content can be wider than
 * the column — a long condition, an open pipeline. Clipping at the column edge is what keeps that
 * content from painting over the context column; the content scrolls inside its own box instead. Do not
 * remove `clipToBounds` because nothing *looks* wider today.
 *
 * **It owns `Esc`.** The key is caught on the way down (`onPreviewKeyEvent`), so it works while a text
 * field in the open editor has focus, and the column takes focus whenever the selection moves so that it
 * also works straight after clicking a chip. A dropdown that is open receives the key first and closes,
 * which is the one level up a reader expects from it.
 */
@Suppress("FunctionNaming")
@Composable
fun RuleWorkspaceColumn(
    contextBar: @Composable () -> Unit,
    selectionKey: Any?,
    onEscape: () -> Unit,
    modifier: Modifier = Modifier,
    canvas: @Composable () -> Unit,
) {
    val focus = remember { FocusRequester() }
    LaunchedEffect(key1 = selectionKey) {
        // Guarded: the first selection can arrive in the same frame the column is first composed, and a
        // requester that is not attached yet throws rather than queueing the request.
        if (selectionKey != null) {
            runCatching { focus.requestFocus() }
        }
    }
    Column(
        modifier = modifier
            .clipToBounds()
            .testTag(tag = WorkspaceLayout.WORKSPACE_TAG)
            .onPreviewKeyEvent { event ->
                if (event.type == KeyEventType.KeyDown && event.key == Key.Escape) {
                    onEscape()
                    true
                } else {
                    false
                }
            }
            .focusRequester(focusRequester = focus)
            .focusable(),
    ) {
        contextBar()
        Divider(color = BorderColor)
        Column(modifier = Modifier.fillMaxWidth().weight(weight = 1f)) {
            canvas()
        }
    }
}
