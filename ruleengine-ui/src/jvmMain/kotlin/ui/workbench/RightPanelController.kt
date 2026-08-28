package ui.workbench

import androidx.compose.runtime.MutableState
import ui.workbench.model.WorkbenchAction
import ui.workbench.model.mode.RightPanelTab

/**
 * Owns the right panel's open state and its tab, persisting every change.
 *
 * A controller rather than two flags edited in place, because the open state is now persisted and a
 * second writer is how a stored value drifts away from the one on screen. Everything that opens,
 * closes or switches the panel goes through here, so there is exactly one place that writes.
 *
 * Follows `BuilderRulesController`: a plain class over Compose state rather than a flow, so a click
 * takes effect in the same frame.
 *
 * @param expanded  The editor state's own flag, so the panel's width still animates from one source.
 * @param width     The panel's width in dp, dragged by the splitter and persisted like the rest.
 * @param viewModel Holds the selected tab; read through it rather than copied, so the two cannot
 *                  disagree about which tab is showing.
 */
class RightPanelController(
    private val expanded: MutableState<Boolean>,
    private val width: MutableState<Float>,
    private val viewModel: RuleWorkbenchViewModel,
    // Injected so a test can observe what would be stored without writing to the developer's real
    // preferences node — which `RightPanelWidthTest` did as a side effect of every assertion. The idiom
    // is `SettingsController.setAutoCompleteShortcut(persist = ...)` and `DockController`'s.
    private val saveExpanded: (Boolean) -> Unit = RightPanelPersistence::saveExpanded,
    private val saveWidth: (Float) -> Unit = RightPanelPersistence::saveWidth,
    private val saveTab: (RightPanelTab) -> Unit = RightPanelPersistence::saveTab,
) {

    /**
     * Whether the panel is open *on the Inspector tab*.
     *
     * Open on Simulate is not open here: the top bar's Inspector button reports this, and a button
     * that looked pressed while Simulate was showing would be lying about what it opens.
     */
    val isInspectorOpen: Boolean
        get() = expanded.value && viewModel.state.value.rightPanelTab == RightPanelTab.INSPECTOR

    fun setExpanded(value: Boolean) {
        expanded.value = value
        saveExpanded(value)
    }

    /**
     * The panel's width in dp while it is open.
     *
     * Read straight through to the state rather than cached, so the splitter's drag and the stored value
     * cannot disagree mid-gesture.
     */
    val widthDp: Float
        get() = width.value

    /**
     * Resizes the panel, clamping to what the layout supports.
     *
     * Every drag delta comes through here rather than being applied to the state directly, so the clamp
     * is applied once and in one place — a splitter that clamped its own deltas would let the width
     * escape the range whenever a second caller appeared.
     */
    fun setWidth(value: Float) {
        val clamped = value.coerceIn(
            RightPanelPersistence.MIN_WIDTH,
            RightPanelPersistence.MAX_WIDTH,
        )
        width.value = clamped
        saveWidth(clamped)
    }

    fun selectTab(tab: RightPanelTab) {
        viewModel.dispatch(action = WorkbenchAction.SelectRightPanelTab(tab = tab))
        saveTab(tab)
    }

    /** Opens the panel *and* switches to the Inspector, so callers cannot open it on the wrong tab. */
    fun showInspector() {
        setExpanded(value = true)
        selectTab(tab = RightPanelTab.INSPECTOR)
    }

    /**
     * What the top bar's button does.
     *
     * Closing when the Inspector is already showing matters: without it the button is a no-op the
     * second time it is pressed, which reads as broken.
     */
    fun toggleInspector() {
        if (isInspectorOpen) setExpanded(value = false) else showInspector()
    }

    /** What the panel's own `⟨` / `⟩` icons do: open or close without changing the tab. */
    fun toggleExpanded() = setExpanded(value = !expanded.value)
}
