package ui.settings

import java.util.prefs.Preferences

/** Stores non-project preferences in the OS-level user preferences, alongside the theme choice. */
object SettingsPersistence {
    private const val AUTOCOMPLETE_SHORTCUT_KEY = "autocompleteShortcut"
    private val prefs = Preferences.userRoot().node("rule-engine-ui")

    fun loadAutoCompleteShortcut(): AutoCompleteShortcut {
        return AutoCompleteShortcut.fromNameOrDefault(name = prefs.get(AUTOCOMPLETE_SHORTCUT_KEY, null))
    }

    fun saveAutoCompleteShortcut(shortcut: AutoCompleteShortcut) {
        prefs.put(AUTOCOMPLETE_SHORTCUT_KEY, shortcut.name)
    }
}
