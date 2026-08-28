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
    private const val WIDTH_KEY = "rightPanelWidth"
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

    /**
     * The panel's width in dp, clamped to the range the layout supports.
     *
     * Clamped on *read* as well as on write, because a stored value can outlive the range that produced
     * it — a width saved on a wide display would otherwise leave nothing for the centre panel on a
     * laptop screen, with no way to drag it back because the handle would be off the edge.
     */
    fun loadWidth(): Float {
        return prefs.getFloat(WIDTH_KEY, DEFAULT_WIDTH).coerceIn(MIN_WIDTH, MAX_WIDTH)
    }

    fun saveWidth(width: Float) {
        prefs.putFloat(WIDTH_KEY, width.coerceIn(MIN_WIDTH, MAX_WIDTH))
    }

    /** Wide enough for the Inspector's two-column fields without crowding the Builder beside it. */
    const val DEFAULT_WIDTH: Float = 320f

    /** Below this the Inspector's own labels wrap and it stops being readable. */
    const val MIN_WIDTH: Float = 260f

    /**
     * An upper bound rather than none at all: past this the centre canvas is the narrow one, and a
     * layout the user cannot recover from by dragging is worse than one they cannot reach.
     */
    const val MAX_WIDTH: Float = 720f
}
