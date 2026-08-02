package ui

import androidx.compose.runtime.Composable

expect suspend fun pickRuleFile(): String?
expect suspend fun pickInputJsonFile(): String?
expect fun copyToClipboard(text: String)

@Composable
expect fun RuleEditor(
    closeController: AppCloseController = AppCloseController(),
    saveController: AppSaveController = AppSaveController(),
)

