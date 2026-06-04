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
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.TextRange
import ruleengine.core.errors.ValidationDiagnostic
import ruleengine.dsl.parser.Parser
import ruleengine.compiler.Validator
import ruleengine.schema.FieldSchemaLoader
import ruleengine.schema.ActionSchemaLoader
import ruleengine.core.domain.ActionSchema
import ruleengine.core.domain.ActionDefinition
import ruleengine.core.domain.FieldSchema
import ruleengine.core.domain.FieldDefinition
import ruleengine.core.domain.FieldId
import ruleengine.core.domain.FieldType
import ruleengine.manifest.ManifestLoader
import ruleengine.manifest.ProjectManifest
import java.nio.file.Path
import java.nio.file.Files

@Composable
actual fun RuleEditor() {
    var schemaText by remember { mutableStateOf("") }
    var ruleValue by remember { mutableStateOf(TextFieldValue("")) }
    var status by remember { mutableStateOf("") }
    var diagnosticsText by remember { mutableStateOf("") }
    val scope = rememberCoroutineScope()

    var parsedSchema by remember { mutableStateOf<FieldSchema?>(null) }
    var actionSchemaText by remember { mutableStateOf("") }
    var parsedActionSchema by remember { mutableStateOf<ActionSchema?>(null) }
    var manifestText by remember { mutableStateOf("") }
    var manifestBaseDir by remember { mutableStateOf<String?>(null) }
    var parsedManifest by remember { mutableStateOf<ProjectManifest?>(null) }
    var selectedManifestEntryId by remember { mutableStateOf<String?>(null) }
    var diagnosticsList by remember { mutableStateOf<List<ValidationDiagnostic>>(emptyList()) }

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

                        Button(onClick = {
                            scope.launch {
                                val manifest = pickManifestFile()
                                if (manifest != null) {
                                    manifestText = manifest.first
                                    manifestBaseDir = manifest.second
                                    parsedManifest = try { ManifestLoader.loadFromString(manifestText) } catch (_: Exception) { null }
                                    status = "Loaded manifest"
                                } else status = "Manifest load canceled"
                            }
                        }) { Text("Load Manifest") }
                    }

                    Spacer(modifier = Modifier.height(8.dp))
                    Text("Actions", style = MaterialTheme.typography.subtitle1)
                    OutlinedTextField(value = actionSchemaText, onValueChange = {
                        actionSchemaText = it
                        parsedActionSchema = try { ActionSchemaLoader.loadFromString(it) } catch (_: Exception) { null }
                    }, modifier = Modifier.fillMaxWidth().height(120.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(onClick = {
                            scope.launch {
                                val content = pickSchemaFile()
                                if (content != null) {
                                    actionSchemaText = content
                                    parsedActionSchema = try { ActionSchemaLoader.loadFromString(content) } catch (_: Exception) { null }
                                    status = "Loaded action schema"
                                } else status = "Action schema load canceled"
                            }
                        }) { Text("Load Actions") }
                        Button(onClick = { actionSchemaText = ""; parsedActionSchema = null; status = "Cleared actions" }) { Text("Clear") }
                    }

                    Spacer(modifier = Modifier.height(8.dp))
                    Text("Fields", style = MaterialTheme.typography.subtitle1)
                    Divider()
                    parsedSchema?.let { s ->
                        LazyColumn(modifier = Modifier.fillMaxSize()) {
                            items(s.fields.entries.toList()) { (fid, def) ->
                                FieldItem(fid, def) { insertText ->
                                    // insert at current caret position
                                    val pos = ruleValue.selection.start
                                    val newText = ruleValue.text.substring(0, pos) + insertText + ruleValue.text.substring(pos)
                                    val newPos = pos + insertText.length
                                    ruleValue = TextFieldValue(newText, selection = TextRange(newPos))
                                }
                            }
                        }
                    } ?: Text("No schema parsed", style = MaterialTheme.typography.body2)

                    parsedManifest?.let { manifest ->
                        Spacer(modifier = Modifier.height(8.dp))
                        Text("Manifest entries", style = MaterialTheme.typography.subtitle1)
                        Divider()
                        LazyColumn(modifier = Modifier.fillMaxHeight(0.25f)) {
                            items(manifest.entries) { entry ->
                                    Row(modifier = Modifier.fillMaxWidth().clickable {
                                    selectedManifestEntryId = entry.id
                                    manifestBaseDir?.let { base ->
                                        entry.schema?.let { sp ->
                                            try {
                                                val p = Path.of(base, sp)
                                                val content = Files.readString(p)
                                                schemaText = content
                                                parsedSchema = try { FieldSchemaLoader.loadFromString(content, p.fileName.toString()) } catch (_: Exception) { null }
                                            } catch (_: Exception) { }
                                        }
                                        entry.actions?.let { ap ->
                                            try {
                                                val p = Path.of(base, ap)
                                                val content = Files.readString(p)
                                                actionSchemaText = content
                                                parsedActionSchema = try { ActionSchemaLoader.loadFromString(content) } catch (_: Exception) { null }
                                            } catch (_: Exception) { }
                                        }
                                        }
                                }.padding(6.dp), verticalAlignment = Alignment.CenterVertically) {
                                    Text(entry.id, modifier = Modifier.weight(1f))
                                    Text(entry.rules.size.toString() + " rules", style = MaterialTheme.typography.caption)
                                }
                            }
                        }
                    }

                    parsedActionSchema?.let { aschema ->
                        Spacer(modifier = Modifier.height(8.dp))
                        Text("Available Actions", style = MaterialTheme.typography.subtitle1)
                        LazyColumn(modifier = Modifier.fillMaxHeight(0.25f)) {
                            items(aschema.actions.entries.toList()) { (name, def) ->
                                Row(modifier = Modifier.fillMaxWidth().clickable {
                                    val placeholder = when (def.argTypes.firstOrNull()) {
                                        ruleengine.core.domain.ActionArgType.INTEGER -> " 0"
                                        ruleengine.core.domain.ActionArgType.DECIMAL -> " 0"
                                        else -> " \"arg\""
                                    }
                                    val insertText = "${name} ${placeholder}"
                                    val pos = ruleValue.selection.start
                                    val newText = ruleValue.text.substring(0, pos) + insertText + ruleValue.text.substring(pos)
                                    val newPos = pos + insertText.length
                                    ruleValue = TextFieldValue(newText, selection = TextRange(newPos))
                                }.padding(6.dp), verticalAlignment = Alignment.CenterVertically) {
                                    Text(name, modifier = Modifier.weight(1f))
                                    Text(def.argTypes.joinToString(", ") { it.name.lowercase() }, style = MaterialTheme.typography.caption)
                                }
                            }
                        }
                    }
                }
            }

            // Rule panel
            Card(modifier = Modifier.weight(0.65f).fillMaxHeight()) {
                Column(modifier = Modifier.padding(8.dp)) {
                    Text("Rule Editor", style = MaterialTheme.typography.h6)
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(value = ruleValue, onValueChange = { ruleValue = it }, modifier = Modifier.fillMaxWidth().height(220.dp), textStyle = androidx.compose.ui.text.TextStyle(fontFamily = FontFamily.Monospace))

                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(onClick = {
                            scope.launch {
                                val content = pickRuleFile()
                                if (content != null) { ruleValue = TextFieldValue(content); status = "Loaded rule" } else status = "Rule load canceled"
                            }
                        }) { Text("Load Rule") }

                        Button(onClick = {
                            if (ruleValue.text.isNotBlank()) {
                                saveRuleToFile("rule.rule", ruleValue.text)
                                status = "Saved rule to filesystem (if supported)"
                            } else status = "No rule to save"
                        }) { Text("Save Rule") }

                        Button(onClick = {
                            if (ruleValue.text.isNotBlank()) {
                                copyToClipboard(ruleValue.text)
                                status = "Copied rule to clipboard"
                            } else status = "No rule to copy"
                        }) { Text("Copy Rule") }

                        Button(onClick = {
                            // Validate
                            scope.launch {
                                try {
                                    if (parsedSchema == null) { status = "No schema loaded"; return@launch }
                                    if (ruleValue.text.isBlank()) { status = "No rule to validate"; return@launch }

                                    val asts = Parser(ruleValue.text).parseRules()
                                    val result = Validator.validate(asts = asts, schema = parsedSchema!!, actions = parsedActionSchema)
                                    if (result.isValid) {
                                        status = "Validation OK"
                                        diagnosticsText = "OK"
                                        diagnosticsList = emptyList()
                                    } else {
                                        status = "Validation failed"
                                        diagnosticsList = result.diagnostics
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
                    if (diagnosticsList.isEmpty()) {
                        OutlinedTextField(value = diagnosticsText, onValueChange = {}, modifier = Modifier.fillMaxWidth().height(140.dp), readOnly = true)
                    } else {
                        LazyColumn(modifier = Modifier.fillMaxWidth().height(140.dp)) {
                            items(diagnosticsList) { d ->
                                Row(modifier = Modifier.fillMaxWidth().clickable {
                                    // try to move caret to the diagnostic position if available
                                    try {
                                        val line = d.line ?: -1
                                        val col = d.column ?: -1
                                        if (line > 0) {
                                            // compute offset
                                            val lines = ruleValue.text.lines()
                                            var offset = 0
                                            for (i in 0 until minOf(line - 1, lines.size - 1)) offset += lines[i].length + 1
                                            if (col > 0) offset += (col - 1)
                                            val pos = offset.coerceIn(0, ruleValue.text.length)
                                            ruleValue = TextFieldValue(ruleValue.text, selection = TextRange(pos))
                                        }
                                    } catch (_: Exception) { }
                                }.padding(6.dp)) {
                                    Text(text = "[${d.severity}] ${d.message}", modifier = Modifier.weight(1f))
                                    d.suggestion?.let { Text(text = it, style = MaterialTheme.typography.caption) }
                                }
                            }
                        }
                    }
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

