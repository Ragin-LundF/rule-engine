package ui.dock

import androidx.compose.runtime.MutableState
import androidx.compose.runtime.snapshots.SnapshotStateMap
import ui.dock.model.DockSurface

/**
 * Owns the dock's height, which surfaces are open, and which tab each is showing — persisting every
 * change.
 *
 * A controller rather than flags edited in place, following `RightPanelController`: the values are
 * persisted, and a second writer is how a stored value drifts away from the one on screen. Everything
 * that resizes, opens, closes or switches the dock goes through here.
 *
 * [saveHeight] and [saveExpanded] are parameters with real defaults, which is the seam
 * `RightPanelController` lacks — its tests write to the developer's actual preferences as a side
 * effect. The idiom is already in the codebase at
 * `SettingsController.setAutoCompleteShortcut(persist = SettingsPersistence::saveAutoCompleteShortcut)`.
 */
class DockController(
    private val height: MutableState<Float>,
    private val expanded: SnapshotStateMap<DockSurface, Boolean>,
    private val tab: SnapshotStateMap<DockSurface, String>,
    private val saveHeight: (Float) -> Unit = DockPersistence::saveHeight,
    private val saveExpanded: (DockSurface, Boolean) -> Unit = DockPersistence::saveExpanded,
) {

    /**
     * The dock's height in dp.
     *
     * Read straight through rather than cached, so a drag and the stored value cannot disagree
     * mid-gesture.
     */
    val heightDp: Float
        get() = height.value

    fun isExpanded(surface: DockSurface): Boolean = expanded[surface] ?: surface.openByDefault

    fun setExpanded(surface: DockSurface, value: Boolean) {
        expanded[surface] = value
        saveExpanded(surface, value)
    }

    fun toggleExpanded(surface: DockSurface) {
        setExpanded(surface = surface, value = !isExpanded(surface = surface))
    }

    fun selectedTab(surface: DockSurface): String = tab[surface] ?: DockSurface.FILE_TAB_ID

    /** Not persisted: which tab you were last on is a within-session detail, unlike whether it is open. */
    fun selectTab(surface: DockSurface, tabId: String) {
        tab[surface] = tabId
    }

    /**
     * Resizes the dock, clamping to what is usable.
     *
     * The one clamp point, so a second caller cannot let the height escape the range. [ceiling] is what
     * the layout can honour at the current window size; it bounds the *drag* but is never written to
     * preferences, so a small window borrows height rather than destroying a preference set on a large
     * one. It is floored at [DockPersistence.MIN_HEIGHT] because a ceiling below the minimum would
     * otherwise invert the range.
     */
    fun setHeight(value: Float, ceiling: Float = DockPersistence.MAX_HEIGHT) {
        val upper = ceiling.coerceIn(DockPersistence.MIN_HEIGHT, DockPersistence.MAX_HEIGHT)
        val clamped = value.coerceIn(DockPersistence.MIN_HEIGHT, upper)
        height.value = clamped
        saveHeight(clamped)
    }

    /** Back to the default, which is the only way out of a height dragged to an extreme. */
    fun resetHeight() {
        setHeight(value = DockPersistence.DEFAULT_HEIGHT)
    }
}
