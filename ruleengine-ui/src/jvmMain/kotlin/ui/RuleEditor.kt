package ui

import androidx.compose.runtime.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.clickable
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.*
import androidx.compose.ui.unit.dp
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import kotlinx.coroutines.launch
import ruleengine.dsl.parser.Parser
import ruleengine.compiler.Validator
import ruleengine.schema.FieldSchemaLoader
import ruleengine.core.domain.FieldSchema
import ruleengine.core.domain.FieldDefinition
import ruleengine.core.domain.FieldId
import ruleengine.core.domain.FieldType

@Composable
actual fun RuleEditor() {
    var schemaText by remember { mutableStateOf("") }
    var ruleText by remember { mutableStateOf("") }
    var status by remember { mutableStateOf("") }
    var diagnosticsText by remember { mutableStateOf("") }
    val scope = rememberCoroutineScope()

    var parsedSchema by remember { mutableStateOf<FieldSchema?>(null) }

    Column(modifier = Modifier.padding(12.dp).fillMaxSize()) {
        Row(modifier = Modifier.fillMaxWidth().weight(1f), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            // Schema panel
            Card(modifier = Modifier.weight(0.35f).fillMaxHeight()) {
                Column(modifier = Modifier.padding(8.dp)) {
                    Text("Schema", style = MaterialTheme.typography.h6)
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(value = schemaText, onValueChange = {
                        schemaText = it
                        parsedSchema = try { FieldSchemaLoader.loadFromString(it, "ui-schema") } catch (_: Exception) { null }
                    }, modifier = Modifier.fillMaxWidth().height(180.dp))

                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                        Button(onClick = {
                            scope.launch {
                                val content = pickSchemaFile()
                                if (content != null) {
                                    schemaText = content
                                    parsedSchema = try { FieldSchemaLoader.loadFromString(content, "ui-schema") } catch (_: Exception) { null }
                                    status = "Loaded schema"
                                } else status = "Schema load canceled"
                            }
                        }) { Text("Load Schema") }
                        Button(onClick = { schemaText = ""; parsedSchema = null; status = "Cleared schema" }) { Text("Clear") }
                    }

                    Spacer(modifier = Modifier.height(8.dp))
                    Text("Fields", style = MaterialTheme.typography.subtitle1)
                    Divider()
                    parsedSchema?.let { s ->
                        LazyColumn(modifier = Modifier.fillMaxSize()) {
                            items(s.fields.entries.toList()) { (fid, def) ->
                                FieldItem(fid, def) { insertText -> ruleText = ruleText + insertText }
                            }
                        }
                    } ?: Text("No schema parsed", style = MaterialTheme.typography.body2)
                }
            }

            // Rule panel
            Card(modifier = Modifier.weight(0.65f).fillMaxHeight()) {
                Column(modifier = Modifier.padding(8.dp)) {
                    Text("Rule Editor", style = MaterialTheme.typography.h6)
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(value = ruleText, onValueChange = { ruleText = it }, modifier = Modifier.fillMaxWidth().height(220.dp), textStyle = androidx.compose.ui.text.TextStyle(fontFamily = FontFamily.Monospace))

                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(onClick = {
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

                        Button(onClick = {
                            // Validate
                            scope.launch {
                                try {
                                    if (parsedSchema == null) { status = "No schema loaded"; return@launch }
                                    if (ruleText.isBlank()) { status = "No rule to validate"; return@launch }

                                    val asts = Parser(ruleText).parseRules()
                                    val result = Validator.validate(asts = asts, schema = parsedSchema!!)
                                    if (result.isValid) {
                                        status = "Validation OK"
                                        diagnosticsText = "OK"
                                    } else {
                                        status = "Validation failed"
                                        diagnosticsText = result.diagnostics.joinToString("\n") { d -> "[${d.severity}] ${d.message}${d.suggestion?.let { " (suggest: $it)" } ?: "" }" }
                                    }
                                } catch (e: Exception) {
                                    status = "Validation error: ${e.message}"
                                    diagnosticsText = e.toString()
                                }
                            }
                        }) { Text("Validate") }
                    }

                    Spacer(modifier = Modifier.height(8.dp))
                    Text("Status: $status")
                    Spacer(modifier = Modifier.height(8.dp))
                    Text("Diagnostics", style = MaterialTheme.typography.subtitle1)
                    OutlinedTextField(value = diagnosticsText, onValueChange = {}, modifier = Modifier.fillMaxWidth().height(140.dp), readOnly = true)
                }
            }
        }
    }
}

@Composable
fun FieldItem(id: FieldId, def: FieldDefinition, onInsert: (String) -> Unit) {
    Column(modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(text = id.value, style = MaterialTheme.typography.subtitle1, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(text = def.type.name.lowercase(), style = MaterialTheme.typography.caption)
        }
        if (def.operators.isNotEmpty()) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                def.operators.forEach { op ->
                    val opText = op.value
                    Text(text = opText, modifier = Modifier
                        .clickable { // insert field operator and placeholder value
                            val placeholder = when (def.type) {
                                FieldType.TEXT, FieldType.STRING_SET -> " \"value\""
                                FieldType.INTEGER, FieldType.DECIMAL -> " 0"
                                FieldType.BOOLEAN -> " true"
                                else -> " \"value\""
                            }
                            onInsert("${id.value} $opText$placeholder")
                        }
                        .padding(4.dp))
                }
            }
        }
    }
}

