package ui

import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isAltPressed
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.isMetaPressed
import androidx.compose.ui.input.key.isShiftPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.type

/**
 * Lets the window hand the save shortcut to the editor.
 *
 * The same split as [AppCloseController], for the same reason: the key event arrives at the window,
 * which knows nothing about projects, while the editor owns the workspace that can save one. A
 * modifier on the editor's own subtree would be shorter but only fires while focus sits inside it,
 * so the shortcut would go dead whenever a dialog or the diagram holds focus.
 */
class AppSaveController {

    /** Installed by the editor once it is composed. */
    var onSaveRequested: (() -> Unit)? = null

    /**
     * True when [event] was the save chord and has been handled, so the window consumes it.
     *
     * Both modifiers are accepted rather than looked up per platform: `Cmd + S` is the macOS
     * spelling, `Ctrl + S` the Windows/Linux one, and neither is bound to anything else here.
     * Alt and Shift disqualify the chord so `Cmd + Shift + S` stays free for Save As.
     */
    fun handleKey(event: KeyEvent): Boolean {
        if (event.type != KeyEventType.KeyDown) return false
        if (event.key != Key.S) return false
        if (!event.isMetaPressed && !event.isCtrlPressed) return false
        if (event.isAltPressed || event.isShiftPressed) return false
        val handler = onSaveRequested ?: return false
        handler()
        return true
    }
}
