package ui

import androidx.compose.runtime.Composable

expect suspend fun pickSchemaFile(): String?
expect suspend fun pickRuleFile(): String?
expect suspend fun pickActionsFile(): String?
expect suspend fun pickInputJsonFile(): String?
expect suspend fun pickManifestFile(): Pair<String, String>?
expect fun saveRuleToFile(filename: String, content: String)
expect fun saveSchemaToFile(filename: String, content: String)
expect fun saveActionsToFile(filename: String, content: String)
expect fun saveManifestToFile(filename: String, content: String)
expect fun copyToClipboard(text: String)

@Composable
expect fun RuleEditor(closeController: AppCloseController = AppCloseController())

