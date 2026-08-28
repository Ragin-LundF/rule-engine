package ui.dock

import ui.dock.model.DockSurface
import java.util.prefs.Preferences

/**
 * Stores the dock's height and whether it is open, in the same preferences node as the theme and the
 * right panel.
 *
 * **One height, four open flags.** Height is a statement about how much of the window a reader wants
 * given to reference material, and that answer does not change between areas — per-area heights would
 * make the canvas jump every time the area changed, for a reason nobody asked for. Whether the dock is
 * *open* genuinely does differ, and [DockSurface.openByDefault] is where that decision lives.
 *
 * Clamped on read as well as on write, for the reason [RightPanelPersistence] gives for the same
 * treatment: a stored value can outlive the range that produced it. Unlike the right panel, the dock
 * has a second, dynamic limit that depends on the window — that one belongs to the layout and is
 * deliberately not stored here, so a session on a small screen cannot overwrite a preference set on a
 * large one.
 */
object DockPersistence {
    private const val HEIGHT_KEY = "dockHeight"
    private const val EXPANDED_PREFIX = "dockExpanded."
    private val prefs: Preferences = Preferences.userRoot().node("rule-engine-ui")

    fun loadHeight(): Float = prefs.getFloat(HEIGHT_KEY, DEFAULT_HEIGHT).coerceIn(MIN_HEIGHT, MAX_HEIGHT)

    fun saveHeight(height: Float) {
        prefs.putFloat(HEIGHT_KEY, height.coerceIn(MIN_HEIGHT, MAX_HEIGHT))
    }

    fun loadExpanded(surface: DockSurface): Boolean =
        prefs.getBoolean(EXPANDED_PREFIX + surface.name, surface.openByDefault)

    fun saveExpanded(surface: DockSurface, expanded: Boolean) {
        prefs.putBoolean(EXPANDED_PREFIX + surface.name, expanded)
    }

    /** What the Builder's dock used to cap its body at, which was a readable amount of text. */
    const val DEFAULT_HEIGHT: Float = 220f

    /** Below this the header is most of the panel and the preview shows two lines. */
    const val MIN_HEIGHT: Float = 120f

    /**
     * An upper bound rather than none: past this the canvas is the smaller half, and the dock is
     * reference material. The window may impose a lower ceiling than this — see `CanvasDockScaffold`.
     */
    const val MAX_HEIGHT: Float = 640f
}
