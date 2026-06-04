package ui

import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import androidx.compose.material.Surface
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp

fun main() = application {
    Window(
        onCloseRequest = ::exitApplication,
        title = "Rule Engine Editor",
        state = rememberWindowState(size = DpSize(1440.dp, 900.dp))
    ) {
        AppTheme {
            Surface(modifier = Modifier.fillMaxSize(), color = Bg) {
                RuleEditor()
            }
        }
    }
}

