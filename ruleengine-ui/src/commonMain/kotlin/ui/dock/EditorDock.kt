package ui.dock

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import ui.AccentGreen
import ui.AccentOrange
import ui.AccentRed
import ui.BgElevated
import ui.BgSurface
import ui.BorderColor
import ui.PrimaryBlue
import ui.TextPrimary
import ui.TextSecondary
import ui.components.TinyButton
import ui.dock.model.DockBadge
import ui.dock.model.DockBadgeKind
import ui.dock.model.DockSurface
import ui.dock.model.DockTab

/**
 * The strip under a canvas: what the file being edited is about to say, and what is wrong with it.
 *
 * Every editor in the workbench generates its file from a model, so what it is *about to write* is the
 * one thing an author most needs to be able to see without leaving the canvas. Switching to the YAML or
 * Code tab shows it, but by then the edit has already happened — and the round trip is what stops
 * people checking at all.
 *
 * Shared by the Builder's two canvases and the Schema, Actions and Manifest areas, so those five
 * surfaces cannot drift into five different preview panels. What a surface supplies is its tabs; the
 * header, the resize, the read-only text surface and the highlighting are the dock's.
 *
 * The header is always visible, even collapsed, because the badge on a tab is a problem count and a
 * count nobody can see until they open a panel arrives too late to be worth having.
 */
@Suppress("FunctionNaming", "LongParameterList")
@Composable
fun EditorDock(
    tabs: List<DockTab>,
    selectedTabId: String,
    onSelectTab: (String) -> Unit,
    expanded: Boolean,
    onToggleExpanded: () -> Unit,
    modifier: Modifier = Modifier,
    /** Copies the whole previewed file. Null hides the button. */
    onCopy: (() -> Unit)? = null,
) {
    if (tabs.isEmpty()) return
    val selected = tabs.firstOrNull { tab -> tab.id == selectedTabId } ?: tabs.first()

    Column(
        modifier = modifier
            .fillMaxSize()
            .clip(shape = RoundedCornerShape(size = 8.dp))
            .background(color = BgSurface)
            .border(width = 1.dp, color = BorderColor, shape = RoundedCornerShape(size = 8.dp)),
    ) {
        DockHeader(
            tabs = tabs,
            selectedTabId = selected.id,
            expanded = expanded,
            onSelectTab = onSelectTab,
            onToggleExpanded = onToggleExpanded,
            onCopy = onCopy,
        )
        if (expanded) {
            Column(modifier = Modifier.fillMaxSize()) {
                selected.content()
            }
        }
    }
}

/**
 * The tab strip, the badges, and the two controls.
 *
 * Clicking a tab while the dock is shut opens it as well as selecting it: a tab that responds by
 * changing a selection nobody can see reads as a dead control.
 */
@OptIn(ExperimentalLayoutApi::class)
@Suppress("FunctionNaming", "LongParameterList")
@Composable
private fun DockHeader(
    tabs: List<DockTab>,
    selectedTabId: String,
    expanded: Boolean,
    onSelectTab: (String) -> Unit,
    onToggleExpanded: () -> Unit,
    onCopy: (() -> Unit)?,
) {
    // FlowRow rather than Row: the dock is as wide as the centre panel, which shrinks as the Inspector
    // is dragged wider, and a tab strip that overflows would push the show/hide button off the edge.
    FlowRow(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 5.dp),
        horizontalArrangement = Arrangement.spacedBy(space = 4.dp),
        verticalArrangement = Arrangement.spacedBy(space = 2.dp),
    ) {
        tabs.forEach { tab ->
            DockTabButton(
                tab = tab,
                selected = tab.id == selectedTabId && expanded,
                onClick = {
                    if (!expanded) onToggleExpanded()
                    onSelectTab(tab.id)
                },
            )
        }
        Row(
            modifier = Modifier.fillMaxWidth().weight(weight = 1f),
            horizontalArrangement = Arrangement.End,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (onCopy != null && expanded) {
                TinyButton(text = "copy", onClick = onCopy)
            }
            TinyButton(text = if (expanded) "hide" else "show", onClick = onToggleExpanded)
        }
    }
}

@Suppress("FunctionNaming")
@Composable
private fun DockTabButton(tab: DockTab, selected: Boolean, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .clip(shape = RoundedCornerShape(size = 6.dp))
            .background(color = if (selected) BgElevated else Color.Transparent)
            .clickable(onClick = onClick)
            .padding(horizontal = 9.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(space = 6.dp),
    ) {
        Text(
            text = tab.title,
            style = MaterialTheme.typography.caption.copy(fontWeight = FontWeight.SemiBold),
            color = if (selected) TextPrimary else TextSecondary,
            maxLines = 1,
            softWrap = false,
        )
        tab.badge?.let { badge -> DockBadgeChip(badge = badge) }
    }
}

@Suppress("FunctionNaming")
@Composable
private fun DockBadgeChip(badge: DockBadge) {
    val colour = when (badge.kind) {
        DockBadgeKind.OK -> AccentGreen
        DockBadgeKind.INFO -> PrimaryBlue
        DockBadgeKind.WARNING -> AccentOrange
        DockBadgeKind.ERROR -> AccentRed
    }
    Text(
        text = badge.text,
        style = MaterialTheme.typography.caption.copy(fontWeight = FontWeight.SemiBold),
        color = colour,
        maxLines = 1,
        softWrap = false,
        modifier = Modifier
            .clip(shape = RoundedCornerShape(percent = 50))
            .background(color = colour.copy(alpha = BADGE_FILL_ALPHA))
            .padding(horizontal = 6.dp, vertical = 1.dp),
    )
}

/**
 * The generated-file tab, built the same way for every surface.
 *
 * A function rather than four hand-rolled `DockTab`s, so the preview cannot end up being five slightly
 * different widgets — which is the outcome the whole shared dock exists to prevent.
 */
fun fileDockTab(
    title: String,
    text: String,
    annotate: (String) -> AnnotatedString,
    highlights: List<DockHighlight> = emptyList(),
    badge: DockBadge? = null,
    id: String = DockSurface.FILE_TAB_ID,
): DockTab = DockTab(id = id, title = title, badge = badge) {
    ReadOnlyCodeView(text = text, annotate = annotate, highlights = highlights)
}

/** How much of a badge's own colour fills it behind the text. */
private const val BADGE_FILL_ALPHA: Float = 0.14f

/** The dock's height when it is shut: the header, and nothing else. */
internal val COLLAPSED_DOCK_HEIGHT: Dp = 34.dp
