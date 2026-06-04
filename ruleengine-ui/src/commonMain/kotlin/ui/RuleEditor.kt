package ui

import androidx.compose.runtime.*
import androidx.compose.foundation.layout.*
import androidx.compose.material.*
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import kotlinx.coroutines.CoroutineScope
import androidx.compose.runtime.rememberCoroutineScope
expect suspend fun pickSchemaFile(): String?
expect suspend fun pickRuleFile(): String?
expect suspend fun pickManifestFile(): Pair<String, String>?
expect fun saveRuleToFile(filename: String, content: String)
expect fun saveSchemaToFile(filename: String, content: String)
expect fun saveActionsToFile(filename: String, content: String)
expect fun copyToClipboard(text: String)

@Composable
expect fun RuleEditor()

