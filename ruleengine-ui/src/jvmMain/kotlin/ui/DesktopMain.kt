package ui

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material.Surface
import androidx.compose.ui.Modifier
import io.github.ragin_lundf.ruleengine_ui.generated.resources.Res
import io.github.ragin_lundf.ruleengine_ui.generated.resources.app
import org.jetbrains.compose.resources.painterResource
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState

fun main() = application {
    Window(
        onCloseRequest = ::exitApplication,
        title = "Rule Engine Editor",
        icon = painterResource(Res.drawable.app),
        state = rememberWindowState(size = DpSize(1440.dp, 900.dp))
    ) {
        AppTheme {
            Surface(modifier = Modifier.fillMaxSize(), color = Bg) {
                RuleEditor()
            }
        }
    }
}

