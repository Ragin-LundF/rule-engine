package ui.settings

import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.isAltPressed
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.key

/**
 * Which key combination opens the completion popup.
 *
 * Configurable because the obvious choice is not available everywhere: on macOS `Ctrl + Space` is
 * the system input-source switcher and `Cmd + Space` is Spotlight, so both are intercepted before
 * the app sees them. The Enter-based options collide with nothing on any platform and are the
 * reliable fallback.
 */
enum class AutoCompleteShortcut(val label: String) {

    CTRL_SPACE(label = "Ctrl + Space") {
        override fun matches(event: KeyEvent): Boolean {
            return event.isCtrlPressed && !event.isAltPressed && event.key == Key.Spacebar
        }
    },

    ALT_SPACE(label = "Alt + Space") {
        override fun matches(event: KeyEvent): Boolean {
            return event.isAltPressed && !event.isCtrlPressed && event.key == Key.Spacebar
        }
    },

    CTRL_ENTER(label = "Ctrl + Enter") {
        override fun matches(event: KeyEvent): Boolean {
            return event.isCtrlPressed && !event.isAltPressed && event.key == Key.Enter
        }
    },

    ALT_ENTER(label = "Alt + Enter") {
        override fun matches(event: KeyEvent): Boolean {
            return event.isAltPressed && !event.isCtrlPressed && event.key == Key.Enter
        }
    };

    /** True when [event] is this shortcut. Callers check it only for key-down events. */
    abstract fun matches(event: KeyEvent): Boolean

    /** True when pressing this shortcut could also insert a character the editor must swallow. */
    val insertsCharacter: Boolean
        get() = this == CTRL_SPACE || this == ALT_SPACE

    companion object {
        val Default: AutoCompleteShortcut = CTRL_SPACE

        fun fromNameOrDefault(name: String?): AutoCompleteShortcut {
            return entries.firstOrNull { entry -> entry.name == name } ?: Default
        }
    }
}
