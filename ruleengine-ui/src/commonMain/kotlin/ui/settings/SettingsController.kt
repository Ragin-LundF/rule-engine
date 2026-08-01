package ui.settings

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/**
 * User preferences that are not part of a project.
 *
 * Mirrors `ThemeController`: commonMain holds the live value so composables observe it, and jvmMain
 * loads it at startup and persists changes.
 */
object SettingsController {

    var autoCompleteShortcut: AutoCompleteShortcut by mutableStateOf(AutoCompleteShortcut.Default)
        private set

    /** Set by the settings screen; the platform layer persists it. */
    fun setAutoCompleteShortcut(shortcut: AutoCompleteShortcut, persist: (AutoCompleteShortcut) -> Unit = {}) {
        autoCompleteShortcut = shortcut
        persist(shortcut)
    }
}
