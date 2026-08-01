package ui.theme

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import ui.theme.model.AppPalette

/** Global theme switch. jvmMain sets [isDark] at startup and persists changes. */
object ThemeController {
    var isDark: Boolean by mutableStateOf(true)

    val palette: AppPalette
        get() = if (isDark) DarkPalette else LightPalette
}
