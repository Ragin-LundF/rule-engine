package ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Surface
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import ui.editor.rules.ACTION_SCHEMA_EXAMPLE
import ui.editor.rules.AppButton
import ui.editor.rules.Chip
import ui.editor.rules.FIELD_SCHEMA_EXAMPLE
import ui.editor.rules.MANIFEST_EXAMPLE
import ui.editor.rules.PanelDivider
import ui.editor.rules.PlainCodeEditor
import ui.editor.rules.SectionHeader
import ui.editor.rules.StatusKind
import ui.editor.rules.drawBottomLine
import ui.editor.rules.drawTopLine

// ── Main composable ───────────────────────────────────────────────────────────

@Composable
actual fun RuleEditor() {
    var schemaText by remember { mutableStateOf(value = "") }
    var actionSchemaText by remember { mutableStateOf(value = "") }
    var manifestText by remember { mutableStateOf(value = "") }
    var ruleText by remember { mutableStateOf(value = "") }
    var status by remember { mutableStateOf(value = "Ready") }
    var statusKind by remember { mutableStateOf(value = StatusKind.IDLE) }
    var schemaExpanded by remember { mutableStateOf(value = false) }
    var actionsExpanded by remember { mutableStateOf(value = false) }
    var showManifestYaml by remember { mutableStateOf(value = false) }
    val scope = rememberCoroutineScope()

    fun setStatus(msg: String, kind: StatusKind) {
        status = msg
        statusKind = kind
    }

    Column(modifier = Modifier.fillMaxSize().background(color = Bg)) {

        // ── Top bar ───────────────────────────────────────────────────────────
        Surface(color = BgSurface, elevation = 0.dp) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .drawBottomLine(w = 1.dp, color = BorderColor)
                    .padding(horizontal = 20.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(space = 10.dp),
            ) {
                Text(text = "⚙", fontSize = 18.sp)
                Text(
                    text = "Rule Engine",
                    style = MaterialTheme.typography.h6,
                    color = TextPrimary,
                )
                Chip(label = "Editor", bg = BgElevated, textColor = PrimaryBlue)
                Spacer(modifier = Modifier.weight(weight = 1f))
                AppButton(label = "Load Manifest") {
                    scope.launch {
                        val m = pickManifestFile()
                        if (m != null) {
                            manifestText = m.first
                            setStatus(msg = "Manifest loaded", kind = StatusKind.SUCCESS)
                        } else {
                            setStatus(msg = "Manifest load cancelled", kind = StatusKind.IDLE)
                        }
                    }
                }
            }
        }

        // ── Main layout ───────────────────────────────────────────────────────
        Row(
            modifier = Modifier
                .weight(weight = 1f)
                .fillMaxWidth()
                .padding(all = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(space = 12.dp),
        ) {

            // ── Left panel ────────────────────────────────────────────────────
            Column(
                modifier = Modifier
                    .width(width = 380.dp)
                    .fillMaxHeight()
                    .clip(shape = RoundedCornerShape(size = 8.dp))
                    .background(color = BgSurface)
                    .border(width = 1.dp, color = BorderColor, shape = RoundedCornerShape(size = 8.dp))
                    .padding(all = 14.dp),
            ) {
                Text(
                    text = "Schema",
                    style = MaterialTheme.typography.h6,
                    color = TextPrimary,
                )
                PanelDivider()

                LazyColumn(modifier = Modifier.weight(weight = 1f).fillMaxWidth()) {

                    // ── Field Schema YAML ─────────────────────────────────────
                    item {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(bottom = 4.dp),
                        ) {
                            SectionHeader(title = "Field Schema YAML")
                            Spacer(modifier = Modifier.weight(1f))
                            Box(
                                modifier = Modifier
                                    .clip(shape = RoundedCornerShape(size = 3.dp))
                                    .background(color = BgHover)
                                    .border(width = 1.dp, color = BorderColor, shape = RoundedCornerShape(size = 3.dp))
                                    .clickable { schemaExpanded = !schemaExpanded }
                                    .padding(horizontal = 6.dp, vertical = 2.dp),
                            ) {
                                Text(
                                    text = if (schemaExpanded) "▲ Collapse" else "▼ Expand",
                                    style = MaterialTheme.typography.caption,
                                    color = TextSecondary,
                                )
                            }
                        }
                        PlainCodeEditor(
                            text = schemaText,
                            onTextChange = { schemaText = it },
                            placeholder = "# Paste or type schema YAML\n# or press Example to start from a template",
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(if (schemaExpanded) 320.dp else 140.dp),
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            AppButton(label = "Example") {
                                schemaText = FIELD_SCHEMA_EXAMPLE
                                setStatus(msg = "Example schema loaded", kind = StatusKind.SUCCESS)
                            }
                            AppButton(label = "Load") {
                                scope.launch {
                                    val c = pickSchemaFile()
                                    if (c != null) {
                                        schemaText = c
                                        setStatus(msg = "Schema loaded", kind = StatusKind.SUCCESS)
                                    }
                                }
                            }
                            AppButton(label = "Save") {
                                if (schemaText.isNotBlank()) {
                                    saveSchemaToFile(filename = "schema.yaml", content = schemaText)
                                    setStatus(msg = "Schema saved", kind = StatusKind.SUCCESS)
                                } else {
                                    setStatus(msg = "Nothing to save", kind = StatusKind.IDLE)
                                }
                            }
                            AppButton(label = "Clear", danger = true) {
                                schemaText = ""
                                setStatus(msg = "Schema cleared", kind = StatusKind.IDLE)
                            }
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                    }

                    item { PanelDivider() }

                    // ── Action Schema YAML ────────────────────────────────────
                    item {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(bottom = 4.dp),
                        ) {
                            SectionHeader(title = "Action Schema YAML")
                            Spacer(modifier = Modifier.weight(1f))
                            Box(
                                modifier = Modifier
                                    .clip(shape = RoundedCornerShape(size = 3.dp))
                                    .background(color = BgHover)
                                    .border(width = 1.dp, color = BorderColor, shape = RoundedCornerShape(size = 3.dp))
                                    .clickable { actionsExpanded = !actionsExpanded }
                                    .padding(horizontal = 6.dp, vertical = 2.dp),
                            ) {
                                Text(
                                    text = if (actionsExpanded) "▲ Collapse" else "▼ Expand",
                                    style = MaterialTheme.typography.caption,
                                    color = TextSecondary,
                                )
                            }
                        }
                        PlainCodeEditor(
                            text = actionSchemaText,
                            onTextChange = { actionSchemaText = it },
                            placeholder = "# Paste or type actions YAML\n# or press Example to start from a template",
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(height = if (actionsExpanded) 280.dp else 110.dp),
                        )
                        Spacer(Modifier.height(6.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(space = 6.dp)) {
                            AppButton(label = "Example") {
                                actionSchemaText = ACTION_SCHEMA_EXAMPLE
                                setStatus(msg = "Example action schema loaded", kind = StatusKind.SUCCESS)
                            }
                            AppButton(label = "Load") {
                                scope.launch {
                                    val c = pickSchemaFile()
                                    if (c != null) {
                                        actionSchemaText = c
                                        setStatus(msg = "Actions loaded", kind = StatusKind.SUCCESS)
                                    }
                                }
                            }
                            AppButton(label = "Save") {
                                if (actionSchemaText.isNotBlank()) {
                                    saveActionsToFile(filename = "actions.yaml", content = actionSchemaText)
                                    setStatus(msg = "Actions saved", kind = StatusKind.SUCCESS)
                                } else {
                                    setStatus(msg = "Nothing to save", kind = StatusKind.IDLE)
                                }
                            }
                            AppButton(label = "Clear", danger = true) {
                                actionSchemaText = ""
                                setStatus(msg = "Actions cleared", kind = StatusKind.IDLE)
                            }
                        }
                        Spacer(Modifier.height(8.dp))
                    }

                    item { PanelDivider() }

                    // ── Manifest section ──────────────────────────────────────
                    item {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(bottom = 4.dp),
                        ) {
                            SectionHeader(title = "Manifest")
                            Spacer(Modifier.weight(1f))
                            Box(
                                modifier = Modifier
                                    .clip(shape = RoundedCornerShape(size = 3.dp))
                                    .background(color = BgHover)
                                    .border(width = 1.dp, color = BorderColor, shape = RoundedCornerShape(size = 3.dp))
                                    .clickable { showManifestYaml = !showManifestYaml }
                                    .padding(horizontal = 6.dp, vertical = 2.dp),
                            ) {
                                Text(
                                    text = if (showManifestYaml) "▲ Collapse" else "✎ Edit YAML",
                                    style = MaterialTheme.typography.caption,
                                    color = TextSecondary,
                                )
                            }
                        }
                        if (showManifestYaml) {
                            PlainCodeEditor(
                                text = manifestText,
                                onTextChange = { manifestText = it },
                                placeholder = "# Paste or type manifest YAML here",
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(height = 200.dp),
                            )
                            Spacer(modifier = Modifier.height(height = 6.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(space = 6.dp)) {
                                AppButton(label = "Example") {
                                    manifestText = MANIFEST_EXAMPLE
                                    setStatus(msg = "Example manifest loaded", kind = StatusKind.SUCCESS)
                                }
                                AppButton(label = "Save") {
                                    if (manifestText.isNotBlank()) {
                                        saveManifestToFile(filename = "manifest.yaml", content = manifestText)
                                        setStatus(msg = "Manifest saved", kind = StatusKind.SUCCESS)
                                    } else {
                                        setStatus(msg = "Nothing to save", kind = StatusKind.IDLE)
                                    }
                                }
                                AppButton(label = "Clear", danger = true) {
                                    manifestText = ""
                                    setStatus(msg = "Manifest cleared", kind = StatusKind.IDLE)
                                }
                            }
                            Spacer(modifier = Modifier.height(8.dp))
                        }

                        if (manifestText.isEmpty()) {
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
                                    text = "Use \"Load Manifest\" above or open\n\"✎ Edit YAML\" to create one.",
                                    style = MaterialTheme.typography.body2,
                                    color = TextMuted,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                        }
                    }

                    item { Spacer(Modifier.height(8.dp)) }
                }
            }

            // ── Right panel ───────────────────────────────────────────────────
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .clip(shape = RoundedCornerShape(size = 8.dp))
                    .background(color = BgSurface)
                    .border(width = 1.dp, color = BorderColor, shape = RoundedCornerShape(size = 8.dp))
                    .padding(all = 14.dp),
            ) {
                // Header with action buttons
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Text(
                        text = "Rule Editor",
                        style = MaterialTheme.typography.h6,
                        color = TextPrimary,
                    )
                    Spacer(Modifier.weight(1f))
                    AppButton(label = "Load Rule") {
                        scope.launch {
                            val c = pickRuleFile()
                            if (c != null) {
                                ruleText = c
                                setStatus(msg = "Rule loaded", kind = StatusKind.SUCCESS)
                            } else {
                                setStatus(msg = "Load cancelled", kind = StatusKind.IDLE)
                            }
                        }
                    }
                    AppButton(label = "Save Rule") {
                        if (ruleText.isNotBlank()) {
                            saveRuleToFile(filename = "rule.rule", content = ruleText)
                            setStatus(msg = "Rule saved", kind = StatusKind.SUCCESS)
                        } else {
                            setStatus(msg = "Nothing to save", kind = StatusKind.IDLE)
                        }
                    }
                    AppButton(label = "Copy Rule") {
                        if (ruleText.isNotBlank()) {
                            copyToClipboard(text = ruleText)
                            setStatus(msg = "Rule copied to clipboard", kind = StatusKind.SUCCESS)
                        } else {
                            setStatus(msg = "Nothing to copy", kind = StatusKind.IDLE)
                        }
                    }
                }

                PanelDivider()

                // Rule editor (takes remaining vertical space)
                PlainCodeEditor(
                    text = ruleText,
                    onTextChange = { ruleText = it },
                    placeholder = "# Write your rules here…\n" +
                            "rule \"example\" {\n" +
                            "    when field > value\n" +
                            "    then action \"result\"\n" +
                            "}",
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(weight = 1f),
                )

                Spacer(modifier = Modifier.height(10.dp))

                // Diagnostics section
                Text(
                    text = "Diagnostics",
                    style = MaterialTheme.typography.h6,
                    color = TextPrimary,
                )
                Spacer(modifier = Modifier.height(6.dp))

                // Validation is JVM-only; show informational message
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(100.dp)
                        .clip(shape = RoundedCornerShape(6.dp))
                        .background(color = Bg)
                        .border(width = 1.dp, color = BorderColor, shape = RoundedCornerShape(size = 6.dp))
                        .padding(all = 14.dp),
                ) {
                    Text(
                        text = "Live validation is available in the desktop build.\n" +
                                "Use the desktop app to validate your rules with the full rule engine.",
                        style = TextStyle(
                            fontFamily = FontFamily.Monospace,
                            fontSize = 12.sp,
                            color = TextMuted,
                        ),
                    )
                }
            }
        }

        // ── Status bar ────────────────────────────────────────────────────────
        Surface(color = BgSurface, elevation = 0.dp) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .drawTopLine(w = 1.dp, color = BorderColor)
                    .padding(horizontal = 18.dp, vertical = 5.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                val dot = when (statusKind) {
                    StatusKind.SUCCESS -> AccentGreen
                    StatusKind.ERROR -> AccentRed
                    StatusKind.IDLE -> TextMuted
                }
                Box(modifier = Modifier.size(7.dp).background(color = dot, shape = CircleShape))
                Text(
                    text = status,
                    style = MaterialTheme.typography.caption,
                    color = TextSecondary,
                )
            }
        }
    }
}
