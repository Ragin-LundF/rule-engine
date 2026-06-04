package ui

import androidx.compose.runtime.*
import androidx.compose.foundation.layout.*
import androidx.compose.material.*
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch

@Composable
actual fun RuleEditor() {
    var schemaText by remember { mutableStateOf("") }
    var ruleText by remember { mutableStateOf("") }
    var status by remember { mutableStateOf("") }

    Column(modifier = androidx.compose.ui.Modifier.padding(16.dp).fillMaxSize(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Schema", style = MaterialTheme.typography.h6)
        OutlinedTextField(value = schemaText, onValueChange = { schemaText = it }, modifier = androidx.compose.ui.Modifier.fillMaxWidth().height(150.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = {
                val scope = rememberCoroutineScope()
                scope.launch {
                    val content = pickSchemaFile()
                    if (content != null) { schemaText = content; status = "Loaded schema" } else status = "Schema load canceled"
                }
            }) { Text("Load Schema") }
            Button(onClick = {
                schemaText = ""
                status = "Cleared schema"
            }) { Text("Clear") }
        }

        Spacer(modifier = androidx.compose.ui.Modifier.height(8.dp))

        Text("Rule", style = MaterialTheme.typography.h6)
        OutlinedTextField(value = ruleText, onValueChange = { ruleText = it }, modifier = androidx.compose.ui.Modifier.fillMaxWidth().height(200.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = {
                val scope = rememberCoroutineScope()
                scope.launch {
                    val content = pickRuleFile()
                    if (content != null) { ruleText = content; status = "Loaded rule" } else status = "Rule load canceled"
                }
            }) { Text("Load Rule") }
            Button(onClick = {
                if (ruleText.isNotBlank()) {
                    saveRuleToFile("rule.rule", ruleText)
                    status = "Saved rule to filesystem (if supported)"
                } else status = "No rule to save"
            }) { Text("Save Rule") }
            Button(onClick = {
                if (ruleText.isNotBlank()) {
                    copyToClipboard(ruleText)
                    status = "Copied rule to clipboard"
                } else status = "No rule to copy"
            }) { Text("Copy Rule") }
        }

        Spacer(modifier = androidx.compose.ui.Modifier.height(8.dp))
        Text("Status: $status")
    }
}

