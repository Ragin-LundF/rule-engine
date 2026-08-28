package ui.workbench.areas

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import ui.copyToClipboard
import ui.dock.CanvasDockScaffold
import ui.dock.CheckList
import ui.dock.DockController
import ui.dock.DockHighlight
import ui.dock.DockHighlightKind
import ui.dock.EditorDock
import ui.dock.checksBadge
import ui.dock.fileDockTab
import ui.dock.model.DockBadge
import ui.dock.model.DockBadgeKind
import ui.dock.model.DockSurface
import ui.dock.model.DockTab
import ui.schema.SchemaIssue
import ui.yaml.annotateYaml
import ui.yaml.model.YamlEditorType

/**
 * The dock the three YAML areas share: the file they generate, its checks, and who uses what.
 *
 * One function rather than three near-identical blocks, because these areas differ only in which file
 * they own and which selection highlights it. The Builder's dock is separate — its file is DSL, its
 * highlight is a rule block plus a row, and it has a test tab instead of a usages one.
 *
 * A note on what the text can be. The Schema and Actions panels push their generated YAML upward only
 * when the visual model is valid (`SyncModelAndYaml` will not publish a schema with a blank or duplicate
 * path), so while the canvas shows such a row the buffer this previews is the *last valid* one.
 * [staleNotice] is how the tab says so rather than quietly showing text that contradicts the Checks tab
 * beside it.
 */
@Suppress("FunctionNaming", "LongParameterList")
@Composable
internal fun YamlAreaWithDock(
    surface: DockSurface,
    dock: DockController,
    fileName: String,
    yaml: String,
    editorType: YamlEditorType,
    highlight: IntRange?,
    issues: List<SchemaIssue>,
    modifier: Modifier = Modifier,
    staleNotice: String? = null,
    /** Selects the declaration a check is about — the reason a check names its subject. */
    onSelectIssue: ((String) -> Unit)? = null,
    /** The flow diagram for this area, moved out of the mode tabs. Null hides the tab. */
    usagesContent: (@Composable () -> Unit)? = null,
    content: @Composable () -> Unit,
) {
    val expanded = dock.isExpanded(surface = surface)
    CanvasDockScaffold(
        expanded = expanded,
        dockHeight = dock.heightDp.dp,
        onDockResize = if (expanded) {
            { delta: Dp, available: Dp ->
                dock.setHeight(value = dock.heightDp + delta.value, ceiling = available.value)
            }
        } else {
            null
        },
        onDockResetHeight = { dock.resetHeight() },
        modifier = modifier,
        dock = {
            EditorDock(
                tabs = yamlDockTabs(
                    fileName = fileName,
                    yaml = yaml,
                    editorType = editorType,
                    highlight = highlight,
                    issues = issues,
                    staleNotice = staleNotice,
                    onSelectIssue = onSelectIssue,
                    usagesContent = usagesContent,
                ),
                selectedTabId = dock.selectedTab(surface = surface),
                onSelectTab = { tabId -> dock.selectTab(surface = surface, tabId = tabId) },
                expanded = expanded,
                onToggleExpanded = { dock.toggleExpanded(surface = surface) },
                onCopy = { copyToClipboard(text = yaml) },
            )
        },
        canvas = content,
    )
}

@Suppress("LongParameterList")
@Composable
private fun yamlDockTabs(
    fileName: String,
    yaml: String,
    editorType: YamlEditorType,
    highlight: IntRange?,
    issues: List<SchemaIssue>,
    staleNotice: String?,
    onSelectIssue: ((String) -> Unit)?,
    usagesContent: (@Composable () -> Unit)?,
): List<DockTab> = buildList {
    add(
        element = fileDockTab(
            title = if (staleNotice == null) fileName else "$fileName (last valid)",
            text = yaml,
            annotate = { text -> annotateYaml(text = text, editorType = editorType) },
            highlights = highlight
                ?.let { range -> listOf(DockHighlight(range = range, kind = DockHighlightKind.CONTEXT)) }
                .orEmpty(),
            badge = staleNotice?.let { DockBadge(text = "stale", kind = DockBadgeKind.WARNING) },
        ),
    )
    add(
        element = DockTab(id = "checks", title = "Checks", badge = checksBadge(issues = issues)) {
            CheckList(
                issues = issues,
                onSelect = onSelectIssue,
                allClearText = "Nothing to report — this file would load as it stands.",
                notice = staleNotice,
            )
        },
    )
    usagesContent?.let { content ->
        // The Usages *mode* used to replace the editor to show this. A tab of the dock shows the same
        // diagram beside the thing it is about, which is what it was always for.
        add(element = DockTab(id = "usages", title = "Usages") { UsagesBody(content = content) })
    }
}

@Suppress("FunctionNaming")
@Composable
private fun UsagesBody(content: @Composable () -> Unit) {
    androidx.compose.foundation.layout.Box(modifier = Modifier.fillMaxSize()) {
        content()
    }
}
