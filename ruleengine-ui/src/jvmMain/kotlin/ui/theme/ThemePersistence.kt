package ui.theme

import java.util.prefs.Preferences

/** Stores the theme choice in the OS-level user preferences (survives restarts). */
object ThemePersistence {
    private const val KEY = "theme"
    private val prefs = Preferences.userRoot().node("rule-engine-ui")

    fun loadIsDark(): Boolean = prefs.get(KEY, "dark") == "dark"

    fun saveIsDark(isDark: Boolean) = prefs.put(KEY, if (isDark) "dark" else "light")
}
