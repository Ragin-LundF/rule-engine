package ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.Divider
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
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch

// ── Status kind ───────────────────────────────────────────────────────────────

private enum class StatusKind { IDLE, SUCCESS, ERROR }

// ── Example content templates ─────────────────────────────────────────────────

private val FIELD_SCHEMA_EXAMPLE = """
schema: my-schema

fields:
  fieldName:
    type: text
    normalizers:
      - trim
      - lowercase
    operators:
      - equals
      - contains
      - startsWith
  amount:
    type: integer
    operators:
      - equals
      - greaterThan
      - lessThan
""".trimIndent()

private val ACTION_SCHEMA_EXAMPLE = """
actions:
  label:
    argTypes: [string]
  category:
    argTypes: [string]
  flag:
    argTypes: [string]
  score:
    argTypes: [integer]
""".trimIndent()

private val MANIFEST_EXAMPLE = """
name: my-project

entries:
  - id: sample
    schema: schema.yaml
    actions: actions.yaml
    rules:
      - rules/rule.rule
""".trimIndent()

// ── Modifier helpers ──────────────────────────────────────────────────────────

private fun Modifier.drawBottomLine(w: Dp, color: Color): Modifier = this.drawWithContent {
    drawContent()
    drawLine(color, Offset(0f, size.height), Offset(size.width, size.height), w.toPx())
}

private fun Modifier.drawTopLine(w: Dp, color: Color): Modifier = this.drawWithContent {
    drawContent()
    drawLine(color, Offset(0f, 0f), Offset(size.width, 0f), w.toPx())
}

// ── Helper composables ────────────────────────────────────────────────────────

@Composable
private fun SectionHeader(title: String) {
    Text(
        text = title.uppercase(),
        style = MaterialTheme.typography.subtitle1,
        color = TextSecondary,
        modifier = Modifier.padding(bottom = 6.dp),
    )
}

@Composable
private fun PanelDivider() {
    Divider(
        color = BorderColor,
        thickness = 1.dp,
        modifier = Modifier.padding(vertical = 8.dp),
    )
}

@Composable
private fun AppButton(
    label: String,
    primary: Boolean = false,
    danger: Boolean = false,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    val bg     = when { primary -> PrimaryBlue; danger -> AccentRed.copy(alpha = 0.12f); else -> Color.Transparent }
    val border = when { primary -> PrimaryBlue; danger -> AccentRed;                     else -> BorderColor }
    // Use Bg (dark background) as text color on primary buttons so it contrasts with PrimaryBlue.
    val text   = when { primary -> Bg;          danger -> AccentRed;                     else -> TextSecondary }
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(5.dp))
            .background(bg)
            .border(1.dp, border, RoundedCornerShape(5.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 5.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.button,
            color = text,
        )
    }
}

@Composable
private fun Chip(
    label: String,
    bg: Color = BgElevated,
    textColor: Color = TextSecondary,
) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(3.dp))
            .background(bg)
            .padding(horizontal = 6.dp, vertical = 2.dp),
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.caption,
            color = textColor,
        )
    }
}

/**
 * Plain-text code editor with a line-number gutter.
 *
 * The browser target does not have access to [ruleengine-core] (JVM-only), so
 * syntax highlighting and validation are not available here.  This composable
 * provides the same visual structure as the desktop editor without those features.
 */
@Composable
private fun PlainCodeEditor(
    text: String,
    onTextChange: (String) -> Unit,
    placeholder: String = "",
    modifier: Modifier = Modifier,
) {
    val scrollState = rememberScrollState()
    val lineCount = remember(text) { text.lines().size.coerceAtLeast(1) }
    val lineNumberWidthDp = 40.dp
    val editorPaddingDp = 10.dp

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(6.dp))
            .background(Bg)
            .border(1.dp, BorderColor, RoundedCornerShape(6.dp)),
    ) {
        Row(modifier = Modifier.fillMaxSize()) {
            // Line-number gutter
            Box(
                modifier = Modifier
                    .width(lineNumberWidthDp)
                    .fillMaxHeight()
                    .background(BgSurface),
            ) {
                Column(
                    modifier = Modifier
                        .padding(top = editorPaddingDp, end = 6.dp, start = 4.dp)
                        .offset { IntOffset(x = 0, y = -scrollState.value) },
                    horizontalAlignment = Alignment.End,
                ) {
                    repeat(lineCount) { i ->
                        Text(
                            text = "${i + 1}",
                            style = TextStyle(
                                fontFamily = FontFamily.Monospace,
                                fontSize = 12.sp,
                                lineHeight = 19.sp,
                                color = TextMuted,
                            ),
                        )
                    }
                }
            }
            // Gutter separator
            Box(modifier = Modifier.width(1.dp).fillMaxHeight().background(BorderColor))
            // Scrollable text area
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .verticalScroll(scrollState),
            ) {
                BasicTextField(
                    value = text,
                    onValueChange = onTextChange,
                    modifier = Modifier
                        .fillMaxWidth()
                        .defaultMinSize(minHeight = 100.dp)
                        .padding(editorPaddingDp),
                    textStyle = TextStyle(
                        fontFamily = FontFamily.Monospace,
                        fontSize = 12.sp,
                        color = TextPrimary,
                        lineHeight = 19.sp,
                    ),
                    cursorBrush = SolidColor(PrimaryBlue),
                )
            }
        }
        // Placeholder shown when the editor is empty
        if (text.isEmpty()) {
            Text(
                text = placeholder,
                modifier = Modifier.padding(
                    start = lineNumberWidthDp + 1.dp + editorPaddingDp,
                    top = editorPaddingDp,
                ),
                style = TextStyle(
                    fontFamily = FontFamily.Monospace,
                    fontSize = 12.sp,
                    color = TextMuted,
                    lineHeight = 19.sp,
                ),
            )
        }
    }
}

// ── Main composable ───────────────────────────────────────────────────────────

@Composable
actual fun RuleEditor() {
    var schemaText       by remember { mutableStateOf("") }
    var actionSchemaText by remember { mutableStateOf("") }
    var manifestText     by remember { mutableStateOf("") }
    var ruleText         by remember { mutableStateOf("") }
    var status           by remember { mutableStateOf("Ready") }
    var statusKind       by remember { mutableStateOf(StatusKind.IDLE) }
    var schemaExpanded   by remember { mutableStateOf(false) }
    var actionsExpanded  by remember { mutableStateOf(false) }
    var showManifestYaml by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    fun setStatus(msg: String, kind: StatusKind) {
        status = msg
        statusKind = kind
    }

    Column(modifier = Modifier.fillMaxSize().background(Bg)) {

        // ── Top bar ───────────────────────────────────────────────────────────
        Surface(color = BgSurface, elevation = 0.dp) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .drawBottomLine(w = 1.dp, color = BorderColor)
                    .padding(horizontal = 20.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Text(text = "⚙", fontSize = 18.sp)
                Text(
                    text = "Rule Engine",
                    style = MaterialTheme.typography.h6,
                    color = TextPrimary,
                )
                Chip(label = "Editor", bg = BgElevated, textColor = PrimaryBlue)
                Spacer(Modifier.weight(1f))
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
                .weight(1f)
                .fillMaxWidth()
                .padding(12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {

            // ── Left panel ────────────────────────────────────────────────────
            Column(
                modifier = Modifier
                    .width(380.dp)
                    .fillMaxHeight()
                    .clip(RoundedCornerShape(8.dp))
                    .background(BgSurface)
                    .border(1.dp, BorderColor, RoundedCornerShape(8.dp))
                    .padding(14.dp),
            ) {
                Text(
                    text = "Schema",
                    style = MaterialTheme.typography.h6,
                    color = TextPrimary,
                )
                PanelDivider()

                LazyColumn(modifier = Modifier.weight(1f).fillMaxWidth()) {

                    // ── Field Schema YAML ─────────────────────────────────────
                    item {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(bottom = 4.dp),
                        ) {
                            SectionHeader(title = "Field Schema YAML")
                            Spacer(Modifier.weight(1f))
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(3.dp))
                                    .background(BgHover)
                                    .border(1.dp, BorderColor, RoundedCornerShape(3.dp))
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
                        Spacer(Modifier.height(6.dp))
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
                        Spacer(Modifier.height(8.dp))
                    }

                    item { PanelDivider() }

                    // ── Action Schema YAML ────────────────────────────────────
                    item {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(bottom = 4.dp),
                        ) {
                            SectionHeader(title = "Action Schema YAML")
                            Spacer(Modifier.weight(1f))
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(3.dp))
                                    .background(BgHover)
                                    .border(1.dp, BorderColor, RoundedCornerShape(3.dp))
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
                                .height(if (actionsExpanded) 280.dp else 110.dp),
                        )
                        Spacer(Modifier.height(6.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
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
                                    .clip(RoundedCornerShape(3.dp))
                                    .background(BgHover)
                                    .border(1.dp, BorderColor, RoundedCornerShape(3.dp))
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
                                    .height(200.dp),
                            )
                            Spacer(Modifier.height(6.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
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
                            Spacer(Modifier.height(8.dp))
                        }

                        if (manifestText.isEmpty()) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(Bg)
                                    .border(1.dp, BorderColor, RoundedCornerShape(6.dp))
                                    .padding(12.dp),
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
                    .clip(RoundedCornerShape(8.dp))
                    .background(BgSurface)
                    .border(1.dp, BorderColor, RoundedCornerShape(8.dp))
                    .padding(14.dp),
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
                        .weight(1f),
                )

                Spacer(Modifier.height(10.dp))

                // Diagnostics section
                Text(
                    text = "Diagnostics",
                    style = MaterialTheme.typography.h6,
                    color = TextPrimary,
                )
                Spacer(Modifier.height(6.dp))

                // Validation is JVM-only; show informational message
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(100.dp)
                        .clip(RoundedCornerShape(6.dp))
                        .background(Bg)
                        .border(1.dp, BorderColor, RoundedCornerShape(6.dp))
                        .padding(14.dp),
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
                    StatusKind.ERROR   -> AccentRed
                    StatusKind.IDLE    -> TextMuted
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
