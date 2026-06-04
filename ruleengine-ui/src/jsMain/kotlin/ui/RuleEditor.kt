package ui

import androidx.compose.runtime.Composable

// Minimal JS actual implementation so the common expect/actual contracts are satisfied.
// The JS/web UI currently uses plain DOM (BrowserUI.kt) for interaction, so this composable
// is a lightweight placeholder and may be extended to a Compose-for-Web implementation later.
@Composable
actual fun RuleEditor() {
    // no-op placeholder for JS target
}

