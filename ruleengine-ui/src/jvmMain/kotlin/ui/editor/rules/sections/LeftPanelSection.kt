package ui.editor.rules.sections

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import ruleengine.manifest.ManifestLoader
import ruleengine.schema.ActionSchemaLoader
import ruleengine.schema.FieldSchemaLoader
import ui.AccentGreen
import ui.AccentPurple
import ui.Bg
import ui.BgElevated
import ui.BgHover
import ui.BgSurface
import ui.BorderColor
import ui.PrimaryBlue
import ui.TextMuted
import ui.TextPrimary
import ui.TextSecondary
import ui.YamlEditorType
import ui.annotateYaml
import ui.editor.rules.ACTION_SCHEMA_EXAMPLE
import ui.editor.rules.AppButton
import ui.editor.rules.Chip
import ui.editor.rules.DesktopRuleEditorActionsSection
import ui.editor.rules.DesktopRuleEditorSchemaSection
import ui.editor.rules.FIELD_SCHEMA_EXAMPLE
import ui.editor.rules.MANIFEST_EXAMPLE
import ui.editor.rules.PanelDivider
import ui.editor.rules.RuleEditorState
import ui.editor.rules.SectionHeader
import ui.editor.rules.StatusKind
import ui.pickSchemaFile
import ui.saveActionsToFile
import ui.saveManifestToFile
import ui.saveSchemaToFile

/** Left panel: schema, actions, and manifest sections. */
@Suppress("FunctionNaming", "LongMethod", "CyclomaticComplexMethod")
@Composable
fun LeftPanelSection(state: RuleEditorState, scope: CoroutineScope, modifier: Modifier = Modifier) {
    var parsedSchema by state.parsedSchema
    var schemaExpanded by state.schemaExpanded
    var schemaFieldValue by state.schemaFieldValue
    var schemaText by state.schemaText
    var actionsExpanded by state.actionsExpanded
    var actionFieldValue by state.actionFieldValue
    var actionSchemaText by state.actionSchemaText
    var parsedActionSchema by state.parsedActionSchema
    var ruleValue by state.ruleValue
    var manifestText by state.manifestText
    var manifestFieldValue by state.manifestFieldValue
    var parsedManifest by state.parsedManifest
    var selectedManifestEntry by state.selectedManifestEntry
    var showManifestYaml by state.showManifestYaml

    Column(
        modifier = modifier
            .clip(shape = RoundedCornerShape(size = 8.dp))
            .background(color = BgSurface)
            .border(width = 1.dp, color = BorderColor, shape = RoundedCornerShape(size = 8.dp))
            .padding(all = 14.dp),
    ) {
        // Panel title
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Schema", style = MaterialTheme.typography.h6, color = TextPrimary)
            Spacer(Modifier.weight(1f))
            parsedSchema?.let {
                Chip(
                    label = "${it.fields.size} fields",
                    bg = AccentGreen.copy(alpha = 0.15f),
                    textColor = AccentGreen
                )
            }
        }
        PanelDivider()

        LazyColumn(modifier = Modifier.weight(1f).fillMaxWidth()) {

            // Schema + Actions extracted into desktop components
            item {
                DesktopRuleEditorSchemaSection(
                    parsedSchema = parsedSchema,
                    schemaExpanded = schemaExpanded,
                    schemaFieldValue = schemaFieldValue,
                    onSchemaFieldValueChange = { newVal ->
                        schemaFieldValue = newVal
                        schemaText = newVal.text
                        parsedSchema = runCatching {
                            FieldSchemaLoader.loadFromString(
                                content = newVal.text,
                                nameHint = "ui-schema"
                            )
                        }.getOrNull()
                    },
                    onExample = {
                        schemaText = FIELD_SCHEMA_EXAMPLE
                        schemaFieldValue = TextFieldValue(text = FIELD_SCHEMA_EXAMPLE)
                        parsedSchema = runCatching {
                            FieldSchemaLoader.loadFromString(
                                content = FIELD_SCHEMA_EXAMPLE,
                                nameHint = "example"
                            )
                        }.getOrNull()
                        state.setStatus(msg = "Example schema loaded", kind = StatusKind.SUCCESS)
                    },
                    onLoad = {
                        scope.launch {
                            val c = pickSchemaFile()
                            if (c != null) {
                                schemaText = c
                                schemaFieldValue = TextFieldValue(text = c)
                                parsedSchema = runCatching {
                                    FieldSchemaLoader.loadFromString(
                                        content = c,
                                        nameHint = "ui-schema"
                                    )
                                }.getOrNull()
                                state.setStatus(msg = "Schema loaded", kind = StatusKind.SUCCESS)
                            }
                        }
                    },
                    onSave = {
                        if (schemaText.isNotBlank()) {
                            saveSchemaToFile(filename = "schema.yaml", content = schemaText)
                            state.setStatus(msg = "Schema saved", kind = StatusKind.SUCCESS)
                        } else {
                            state.setStatus(msg = "Nothing to save", kind = StatusKind.IDLE)
                        }
                    },
                    onClear = {
                        schemaText = ""
                        schemaFieldValue = TextFieldValue(text = "")
                        parsedSchema = null
                        state.setStatus(msg = "Schema cleared", kind = StatusKind.IDLE)
                    },
                    onInsertField = { ins ->
                        val pos = ruleValue.selection.start
                        val newText = ruleValue.text.substring(0, pos) + ins + ruleValue.text.substring(
                            startIndex = pos
                        )
                        ruleValue = TextFieldValue(
                            text = newText,
                            selection = androidx.compose.ui.text.TextRange(index = pos + ins.length)
                        )
                    },
                )
            }

            // Action schema + available actions extracted
            item {
                DesktopRuleEditorActionsSection(
                    parsedActionSchema = parsedActionSchema,
                    actionsExpanded = actionsExpanded,
                    actionFieldValue = actionFieldValue,
                    onActionFieldValueChange = { newVal ->
                        actionFieldValue = newVal
                        actionSchemaText = newVal.text
                        parsedActionSchema = runCatching {
                            ActionSchemaLoader.loadFromString(content = newVal.text)
                        }.getOrNull()
                    },
                    onExample = {
                        actionSchemaText = ACTION_SCHEMA_EXAMPLE
                        actionFieldValue = TextFieldValue(ACTION_SCHEMA_EXAMPLE)
                        parsedActionSchema = runCatching {
                            ActionSchemaLoader.loadFromString(content = ACTION_SCHEMA_EXAMPLE)
                        }.getOrNull()
                        state.setStatus(msg = "Example action schema loaded", kind = StatusKind.SUCCESS)
                    },
                    onLoad = {
                        scope.launch {
                            val c = pickSchemaFile()
                            if (c != null) {
                                actionSchemaText = c
                                actionFieldValue = TextFieldValue(text = c)
                                parsedActionSchema = runCatching {
                                    ActionSchemaLoader.loadFromString(content = c)
                                }.getOrNull()
                                state.setStatus(msg = "Actions loaded", kind = StatusKind.SUCCESS)
                            }
                        }
                    },
                    onSave = {
                        if (actionSchemaText.isNotBlank()) {
                            saveActionsToFile(filename = "actions.yaml", content = actionSchemaText)
                            state.setStatus(msg = "Actions saved", kind = StatusKind.SUCCESS)
                        } else {
                            state.setStatus(msg = "Nothing to save", kind = StatusKind.IDLE)
                        }
                    },
                    onClear = {
                        actionSchemaText = ""
                        actionFieldValue = TextFieldValue("")
                        parsedActionSchema = null
                        state.setStatus(msg = "Actions cleared", kind = StatusKind.IDLE)
                    },
                    onInsertAction = { ins ->
                        val pos = ruleValue.selection.start
                        val newText = ruleValue.text.substring(0, pos) + ins + ruleValue.text.substring(pos)
                        ruleValue = TextFieldValue(
                            newText,
                            selection = androidx.compose.ui.text.TextRange(index = pos + ins.length)
                        )
                    },
                )
            }

            // ── Manifest section ──────────────────────────────────
            item {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(bottom = 4.dp),
                ) {
                    SectionHeader(title = "Manifest")
                    parsedManifest?.let {
                        Spacer(modifier = Modifier.width(8.dp))
                        Chip(
                            label = "${it.entries.size} entries",
                            bg = AccentPurple.copy(alpha = 0.12f),
                            textColor = AccentPurple
                        )
                    }
                    Spacer(modifier = Modifier.weight(weight = 1f))
                    // Toggle between YAML editor and entries list
                    Box(
                        modifier = Modifier
                            .clip(shape = RoundedCornerShape(size = 3.dp))
                            .background(color = BgHover)
                            .border(
                                width = 1.dp,
                                color = BorderColor,
                                shape = RoundedCornerShape(size = 3.dp)
                            )
                            .clickable { showManifestYaml = !showManifestYaml }
                            .padding(horizontal = 6.dp, vertical = 2.dp),
                    ) {
                        Text(
                            text = if (showManifestYaml) "▲ Entries" else "✎ Edit YAML",
                            style = MaterialTheme.typography.caption,
                            color = TextSecondary,
                        )
                    }
                }

                // ── Manifest YAML editor ──────────────────────────
                if (showManifestYaml) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(height = 200.dp)
                            .clip(shape = RoundedCornerShape(size = 4.dp))
                            .background(color = Bg)
                            .border(
                                width = 1.dp,
                                color = BorderColor,
                                shape = RoundedCornerShape(size = 4.dp)
                            )
                            .padding(all = 8.dp),
                    ) {
                        // Plain YAML editor for manifest (no schema-specific completions needed)
                        val annotatedManifest = remember(key1 = manifestText) {
                            annotateYaml(
                                text = manifestText,
                                editorType = YamlEditorType.FIELD_SCHEMA
                            )
                        }
                        // Sync externally-set manifestText into the local field value
                        LaunchedEffect(key1 = manifestText) {
                            if (manifestFieldValue.text != manifestText) {
                                manifestFieldValue = TextFieldValue(manifestText)
                            }
                        }
                        BasicTextField(
                            value = TextFieldValue(
                                annotatedString = annotatedManifest,
                                selection = manifestFieldValue.selection,
                                composition = manifestFieldValue.composition,
                            ),
                            onValueChange = { newVal ->
                                manifestFieldValue = TextFieldValue(
                                    text = newVal.text,
                                    selection = newVal.selection,
                                    composition = newVal.composition,
                                )
                                manifestText = newVal.text
                                // Reset entry selection so first-entry auto-load
                                // triggers again on the new manifest content.
                                selectedManifestEntry = null
                                parsedManifest = runCatching {
                                    ManifestLoader.loadFromString(content = newVal.text)
                                }.getOrNull()
                            },
                            modifier = Modifier.fillMaxSize(),
                            textStyle = TextStyle(
                                fontFamily = FontFamily.Monospace,
                                fontSize = 11.sp,
                                color = TextPrimary,
                                lineHeight = 17.sp,
                            ),
                            cursorBrush = SolidColor(value = PrimaryBlue),
                        )
                        if (manifestText.isEmpty()) {
                            Text(
                                text = "# Paste or type manifest YAML here",
                                style = TextStyle(
                                    fontFamily = FontFamily.Monospace,
                                    fontSize = 11.sp,
                                    color = TextMuted
                                ),
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(6.dp))
                    Row(horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(space = 6.dp)) {
                        AppButton(label = "Example") {
                            manifestText = MANIFEST_EXAMPLE
                            parsedManifest =
                                runCatching {
                                    ManifestLoader.loadFromString(content = MANIFEST_EXAMPLE)
                                }.getOrNull()
                            state.setStatus(msg = "Example manifest loaded", kind = StatusKind.SUCCESS)
                        }
                        AppButton(label = "Save") {
                            if (manifestText.isNotBlank()) {
                                saveManifestToFile(filename = "manifest.yaml", content = manifestText)
                                state.setStatus(msg = "Manifest saved", kind = StatusKind.SUCCESS)
                            } else {
                                state.setStatus(msg = "Nothing to save", kind = StatusKind.IDLE)
                            }
                        }
                        AppButton("Clear", danger = true) {
                            manifestText = ""
                            parsedManifest = null
                            selectedManifestEntry = null
                            state.setStatus(msg = "Manifest cleared", kind = StatusKind.IDLE)
                        }
                    }
                    Spacer(modifier = Modifier.height(height = 8.dp))
                }
            }

            // ── Manifest entries list ─────────────────────────────
            parsedManifest?.let { manifest ->
                items(items = manifest.entries) { entry ->
                    val sel = selectedManifestEntry == entry.id
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(shape = RoundedCornerShape(size = 4.dp))
                            .background(color = if (sel) BgElevated else Color.Transparent)
                            .clickable { state.loadManifestEntry(entry = entry) }
                            .padding(horizontal = 8.dp, vertical = 7.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Box(
                            Modifier.size(size = 6.dp)
                                .background(
                                    color = if (sel) PrimaryBlue else Color.Transparent,
                                    shape = CircleShape
                                )
                        )
                        Spacer(modifier = Modifier.width(width = 8.dp))
                        Column(modifier = Modifier.weight(weight = 1f)) {
                            Text(
                                text = entry.id,
                                style = MaterialTheme.typography.body1,
                                color = if (sel) TextPrimary else TextSecondary,
                            )
                            if (entry.rules.isNotEmpty()) {
                                Text(
                                    text = "${entry.rules.size} rule file(s)",
                                    style = MaterialTheme.typography.caption
                                )
                            }
                        }
                        Chip(label = "${entry.rules.size} rules")
                    }
                }
            } ?: item {
                // No manifest — show a helpful prompt
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(shape = RoundedCornerShape(size = 6.dp))
                        .background(color = Bg)
                        .border(width = 1.dp, color = BorderColor, shape = RoundedCornerShape(size = 6.dp))
                        .padding(all = 12.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = "Use \"Load Manifest\" above or open\n\"Edit YAML\" to create one.",
                        style = MaterialTheme.typography.body2,
                        color = TextMuted,
                    )
                }
            }

            item { Spacer(modifier = Modifier.height(8.dp)) }
        }
    }
}




