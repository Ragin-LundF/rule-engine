package ui.workbench

import ui.workbench.model.mode.RightPanelTab
import java.util.prefs.Preferences

/**
 * Stores the right panel's open state and its tab in the OS-level user preferences, in the same node
 * as the theme choice.
 *
 * The panel used to reset to collapsed on every launch, so a user who wanted the Inspector open had
 * to reopen it each session. Persisting the tab as well as the open state matters for the same
 * reason: reopening on Simulate when the last thing you used was the Inspector is the same loss in
 * miniature.
 */
object RightPanelPersistence {
    private const val EXPANDED_KEY = "rightPanelExpanded"
    private const val TAB_KEY = "rightPanelTab"
    private val prefs: Preferences = Preferences.userRoot().node("rule-engine-ui")

    /**
     * Collapsed on a first launch, matching the layout reason the default was chosen for: expanded,
     * this panel and the rule tree together leave the Builder's rows too little width.
     */
    fun loadExpanded(): Boolean = prefs.getBoolean(EXPANDED_KEY, false)

    fun saveExpanded(expanded: Boolean) = prefs.putBoolean(EXPANDED_KEY, expanded)

    /** Falls back to the Inspector for an absent or unrecognised stored name. */
    fun loadTab(): RightPanelTab {
        val stored = prefs.get(TAB_KEY, null)
        return RightPanelTab.entries.firstOrNull { tab -> tab.name == stored } ?: RightPanelTab.INSPECTOR
    }

    fun saveTab(tab: RightPanelTab) = prefs.put(TAB_KEY, tab.name)
}
