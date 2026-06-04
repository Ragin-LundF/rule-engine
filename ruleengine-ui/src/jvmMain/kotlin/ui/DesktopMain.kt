package ui

import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Surface

fun main() = application {
    Window(onCloseRequest = ::exitApplication, title = "Rule Editor") {
        MaterialTheme {
            Surface {
                RuleEditor()
            }
        }
    }
}

