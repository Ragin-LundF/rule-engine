@file:OptIn(ExperimentalComposeUiApi::class)

package ui

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material.Surface
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.window.ComposeViewport

fun main() {
    ComposeViewport(viewportContainerId = "compose-canvas") {
        AppTheme {
            Surface(
                modifier = Modifier.fillMaxSize(),
                color = Bg,
            ) {
                RuleEditor()
            }
        }
    }
}

