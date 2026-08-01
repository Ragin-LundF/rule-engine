package ui.workbench.rules

import ui.editor.rules.model.ViewMode
import ui.workbench.model.mode.RuleMode

/**
 * Convert the platform-neutral [RuleMode] used by [RuleWorkbenchState]
 * into the desktop-specific [ViewMode] used by the legacy editor chrome.
 */
fun RuleMode.toViewMode(): ViewMode = when (this) {
    RuleMode.BUILDER -> ViewMode.BUILDER
    RuleMode.CODE -> ViewMode.CODE
    RuleMode.DIAGRAM -> ViewMode.DIAGRAM
    RuleMode.TEST -> ViewMode.TEST
    RuleMode.TABLE -> ViewMode.TABLE
}

/**
 * Convert the desktop-specific [ViewMode] back into the platform-neutral [RuleMode].
 */
fun ViewMode.toRuleMode(): RuleMode = when (this) {
    ViewMode.BUILDER -> RuleMode.BUILDER
    ViewMode.CODE -> RuleMode.CODE
    ViewMode.DIAGRAM -> RuleMode.DIAGRAM
    ViewMode.TEST -> RuleMode.TEST
    ViewMode.TABLE -> RuleMode.TABLE
}
