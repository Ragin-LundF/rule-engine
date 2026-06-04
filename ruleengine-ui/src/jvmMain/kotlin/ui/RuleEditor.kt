package ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import ruleengine.compiler.Validator
import ruleengine.core.domain.ActionSchema
import ruleengine.core.domain.FieldDefinition
import ruleengine.core.domain.FieldId
import ruleengine.core.domain.FieldSchema
import ruleengine.core.domain.FieldType
import ruleengine.core.errors.Severity
import ruleengine.core.errors.ValidationDiagnostic
import ruleengine.dsl.parser.Parser
import ruleengine.manifest.ManifestLoader
import ruleengine.manifest.ProjectManifest
import ruleengine.schema.ActionSchemaLoader
import ruleengine.schema.FieldSchemaLoader
import java.nio.file.Files
import java.nio.file.Path

// ── Status kind ───────────────────────────────────────────────────────────────
enum class StatusKind { IDLE, SUCCESS, ERROR }

// ── Helpers ───────────────────────────────────────────────────────────────────

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
    Divider(color = BorderColor, thickness = 1.dp, modifier = Modifier.padding(vertical = 8.dp))
}

@Composable
private fun AppButton(
    label: String,
    primary: Boolean = false,
    danger: Boolean = false,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    val bg     = when { primary -> PrimaryBlue;                           danger -> AccentRed.copy(alpha = 0.12f); else -> Color.Transparent }
    val border = when { primary -> PrimaryBlue;                           danger -> AccentRed;                     else -> BorderColor }
    val text   = when { primary -> Color(0xFF0D1117);                     danger -> AccentRed;                     else -> TextSecondary }
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(5.dp))
            .background(bg)
            .border(1.dp, border, RoundedCornerShape(5.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 5.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(label, style = MaterialTheme.typography.button, color = text)
    }
}

@Composable
private fun Chip(label: String, bg: Color = BgElevated, textColor: Color = TextSecondary) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(3.dp))
            .background(bg)
            .padding(horizontal = 6.dp, vertical = 2.dp),
    ) {
        Text(label, style = MaterialTheme.typography.caption, color = textColor)
    }
}

private fun fieldTypeColor(type: FieldType): Color = when (type) {
    FieldType.INTEGER    -> Color(0xFF58A6FF)
    FieldType.DECIMAL    -> Color(0xFF58A6FF)
    FieldType.TEXT       -> Color(0xFF79C0FF)
    FieldType.BOOLEAN    -> AccentPurple
    FieldType.STRING_SET -> AccentGreen
    else                 -> TextSecondary
}

private fun Modifier.drawBottomLine(w: Dp, color: Color): Modifier = this.drawWithContent {
    drawContent()
    drawLine(color, Offset(0f, size.height), Offset(size.width, size.height), w.toPx())
}
private fun Modifier.drawTopLine(w: Dp, color: Color): Modifier = this.drawWithContent {
    drawContent()
    drawLine(color, Offset(0f, 0f), Offset(size.width, 0f), w.toPx())
}

@Composable
private fun codeFieldColors() = TextFieldDefaults.outlinedTextFieldColors(
    textColor            = TextPrimary,
    backgroundColor      = Bg,
    cursorColor          = PrimaryBlue,
    focusedBorderColor   = PrimaryBlue,
    unfocusedBorderColor = BorderColor,
    placeholderColor     = TextMuted,
)

// ── Main composable ───────────────────────────────────────────────────────────

@Composable
actual fun RuleEditor() {
    var schemaText       by remember { mutableStateOf("") }
    var ruleValue        by remember { mutableStateOf(TextFieldValue("")) }
    var status           by remember { mutableStateOf("Ready") }
    var statusKind       by remember { mutableStateOf(StatusKind.IDLE) }
    val scope = rememberCoroutineScope()

    var parsedSchema          by remember { mutableStateOf<FieldSchema?>(null) }
    var actionSchemaText      by remember { mutableStateOf("") }
    var parsedActionSchema    by remember { mutableStateOf<ActionSchema?>(null) }
    var manifestText          by remember { mutableStateOf("") }
    var manifestBaseDir       by remember { mutableStateOf<String?>(null) }
    var parsedManifest        by remember { mutableStateOf<ProjectManifest?>(null) }
    var selectedManifestEntry by remember { mutableStateOf<String?>(null) }
    var diagnosticsList       by remember { mutableStateOf<List<ValidationDiagnostic>>(emptyList()) }
    var diagnosticsText       by remember { mutableStateOf("") }

    fun setStatus(msg: String, kind: StatusKind) { status = msg; statusKind = kind }

    Column(modifier = Modifier.fillMaxSize().background(Bg)) {

        // ── Top Bar ───────────────────────────────────────────────────────────
        Surface(color = BgSurface, elevation = 0.dp) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .drawBottomLine(1.dp, BorderColor)
                    .padding(horizontal = 20.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Text("⚙", fontSize = 18.sp)
                Text("Rule Engine", style = MaterialTheme.typography.h6, color = TextPrimary)
                Chip("Editor", BgElevated, PrimaryBlue)
                Spacer(Modifier.weight(1f))
                AppButton("Load Manifest") {
                    scope.launch {
                        val m = pickManifestFile()
                        if (m != null) {
                            manifestText = m.first
                            manifestBaseDir = m.second
                            parsedManifest = try { ManifestLoader.loadFromString(manifestText) } catch (_: Exception) { null }
                            setStatus("Manifest loaded", StatusKind.SUCCESS)
                        } else setStatus("Manifest load cancelled", StatusKind.IDLE)
                    }
                }
                AppButton("Load Schema") {
                    scope.launch {
                        val c = pickSchemaFile()
                        if (c != null) {
                            schemaText = c
                            parsedSchema = try { FieldSchemaLoader.loadFromString(c, "ui-schema") } catch (_: Exception) { null }
                            setStatus("Schema loaded — ${parsedSchema?.fields?.size ?: 0} fields", StatusKind.SUCCESS)
                        } else setStatus("Schema load cancelled", StatusKind.IDLE)
                    }
                }
            }
        }

        // ── Main layout ───────────────────────────────────────────────────────
        Row(
            modifier = Modifier.weight(1f).fillMaxWidth().padding(12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {

            // ── Left panel ────────────────────────────────────────────────────
            Column(
                modifier = Modifier
                    .weight(0.33f)
                    .fillMaxHeight()
                    .clip(RoundedCornerShape(8.dp))
                    .background(BgSurface)
                    .border(1.dp, BorderColor, RoundedCornerShape(8.dp))
                    .padding(14.dp),
            ) {
                // Schema header
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Schema", style = MaterialTheme.typography.h6, color = TextPrimary)
                    Spacer(Modifier.weight(1f))
                    parsedSchema?.let { Chip("${it.fields.size} fields", AccentGreen.copy(0.15f), AccentGreen) }
                }
                PanelDivider()

                // Field Schema YAML
                SectionHeader("Field Schema YAML")
                OutlinedTextField(
                    value = schemaText,
                    onValueChange = {
                        schemaText = it
                        parsedSchema = try { FieldSchemaLoader.loadFromString(it, "ui-schema") } catch (_: Exception) { null }
                    },
                    modifier = Modifier.fillMaxWidth().height(120.dp),
                    textStyle = TextStyle(fontFamily = FontFamily.Monospace, fontSize = 11.sp, color = TextPrimary),
                    colors = codeFieldColors(),
                    placeholder = { Text("# Paste schema YAML here…", style = MaterialTheme.typography.caption) },
                )
                Spacer(Modifier.height(6.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    AppButton("Load") {
                        scope.launch {
                            val c = pickSchemaFile()
                            if (c != null) {
                                schemaText = c
                                parsedSchema = try { FieldSchemaLoader.loadFromString(c, "ui-schema") } catch (_: Exception) { null }
                                setStatus("Schema loaded", StatusKind.SUCCESS)
                            }
                        }
                    }
                    AppButton("Clear", danger = true) { schemaText = ""; parsedSchema = null; setStatus("Schema cleared", StatusKind.IDLE) }
                }

                PanelDivider()

                // Action Schema YAML
                SectionHeader("Action Schema YAML")
                OutlinedTextField(
                    value = actionSchemaText,
                    onValueChange = {
                        actionSchemaText = it
                        parsedActionSchema = try { ActionSchemaLoader.loadFromString(it) } catch (_: Exception) { null }
                    },
                    modifier = Modifier.fillMaxWidth().height(90.dp),
                    textStyle = TextStyle(fontFamily = FontFamily.Monospace, fontSize = 11.sp, color = TextPrimary),
                    colors = codeFieldColors(),
                    placeholder = { Text("# Paste actions YAML here…", style = MaterialTheme.typography.caption) },
                )
                Spacer(Modifier.height(6.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    AppButton("Load") {
                        scope.launch {
                            val c = pickSchemaFile()
                            if (c != null) {
                                actionSchemaText = c
                                parsedActionSchema = try { ActionSchemaLoader.loadFromString(c) } catch (_: Exception) { null }
                                setStatus("Actions loaded", StatusKind.SUCCESS)
                            }
                        }
                    }
                    AppButton("Clear", danger = true) { actionSchemaText = ""; parsedActionSchema = null; setStatus("Actions cleared", StatusKind.IDLE) }
                }

                PanelDivider()

                // Fields list
                SectionHeader("Fields")
                if (parsedSchema != null) {
                    LazyColumn(modifier = Modifier.weight(1f).fillMaxWidth()) {
                        items(parsedSchema!!.fields.entries.toList()) { (fid, def) ->
                            FieldItem(fid, def) { ins ->
                                val pos     = ruleValue.selection.start
                                val newText = ruleValue.text.substring(0, pos) + ins + ruleValue.text.substring(pos)
                                ruleValue   = TextFieldValue(newText, selection = TextRange(pos + ins.length))
                            }
                        }
                    }
                } else {
                    Box(
                        modifier = Modifier.weight(1f).fillMaxWidth()
                            .clip(RoundedCornerShape(6.dp))
                            .background(Bg)
                            .border(1.dp, BorderColor, RoundedCornerShape(6.dp))
                            .padding(12.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text("Load a schema to see fields", style = MaterialTheme.typography.body2)
                    }
                }

                // Actions list
                parsedActionSchema?.let { aschema ->
                    PanelDivider()
                    SectionHeader("Available Actions")
                    LazyColumn(modifier = Modifier.heightIn(max = 150.dp).fillMaxWidth()) {
                        items(aschema.actions.entries.toList()) { (name, def) ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(4.dp))
                                    .clickable {
                                        val ph = when (def.argTypes.firstOrNull()) {
                                            ruleengine.core.domain.ActionArgType.INTEGER -> " 0"
                                            ruleengine.core.domain.ActionArgType.DECIMAL -> " 0"
                                            else -> " \"arg\""
                                        }
                                        val ins = "$name$ph"
                                        val pos = ruleValue.selection.start
                                        val newText = ruleValue.text.substring(0, pos) + ins + ruleValue.text.substring(pos)
                                        ruleValue = TextFieldValue(newText, selection = TextRange(pos + ins.length))
                                    }
                                    .padding(horizontal = 6.dp, vertical = 5.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(name, modifier = Modifier.weight(1f), style = MaterialTheme.typography.body1, color = AccentPurple)
                                Text(def.argTypes.joinToString(", ") { it.name.lowercase() }, style = MaterialTheme.typography.caption)
                            }
                        }
                    }
                }

                // Manifest entries
                parsedManifest?.let { manifest ->
                    PanelDivider()
                    SectionHeader("Manifest Entries")
                    LazyColumn(modifier = Modifier.heightIn(max = 130.dp).fillMaxWidth()) {
                        items(manifest.entries) { entry ->
                            val sel = selectedManifestEntry == entry.id
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(4.dp))
                                    .background(if (sel) BgElevated else Color.Transparent)
                                    .clickable {
                                        selectedManifestEntry = entry.id
                                        manifestBaseDir?.let { base ->
                                            entry.schema?.let { sp ->
                                                runCatching {
                                                    val p = Path.of(base, sp)
                                                    val c = Files.readString(p)
                                                    schemaText   = c
                                                    parsedSchema = try { FieldSchemaLoader.loadFromString(c, p.fileName.toString()) } catch (_: Exception) { null }
                                                }
                                            }
                                            entry.actions?.let { ap ->
                                                runCatching {
                                                    val p = Path.of(base, ap)
                                                    val c = Files.readString(p)
                                                    actionSchemaText   = c
                                                    parsedActionSchema = try { ActionSchemaLoader.loadFromString(c) } catch (_: Exception) { null }
                                                }
                                            }
                                        }
                                    }
                                    .padding(horizontal = 8.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Box(Modifier.size(6.dp).background(if (sel) PrimaryBlue else Color.Transparent, CircleShape))
                                Spacer(Modifier.width(8.dp))
                                Text(entry.id, modifier = Modifier.weight(1f),
                                    style = MaterialTheme.typography.body1,
                                    color = if (sel) TextPrimary else TextSecondary)
                                Chip("${entry.rules.size} rules")
                            }
                        }
                    }
                }
            }

            // ── Right panel ───────────────────────────────────────────────────
            Column(
                modifier = Modifier
                    .weight(0.67f)
                    .fillMaxHeight()
                    .clip(RoundedCornerShape(8.dp))
                    .background(BgSurface)
                    .border(1.dp, BorderColor, RoundedCornerShape(8.dp))
                    .padding(14.dp),
            ) {
                // Header + action buttons
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Rule Editor", style = MaterialTheme.typography.h6, color = TextPrimary)
                    Spacer(Modifier.weight(1f))
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                        AppButton("Load Rule") {
                            scope.launch {
                                val c = pickRuleFile()
                                if (c != null) { ruleValue = TextFieldValue(c); setStatus("Rule loaded", StatusKind.SUCCESS) }
                                else setStatus("Load cancelled", StatusKind.IDLE)
                            }
                        }
                        AppButton("Save Rule") {
                            if (ruleValue.text.isNotBlank()) { saveRuleToFile("rule.rule", ruleValue.text); setStatus("Rule saved", StatusKind.SUCCESS) }
                            else setStatus("Nothing to save", StatusKind.IDLE)
                        }
                        AppButton("Copy Rule") {
                            if (ruleValue.text.isNotBlank()) { copyToClipboard(ruleValue.text); setStatus("Rule copied to clipboard", StatusKind.SUCCESS) }
                            else setStatus("Nothing to copy", StatusKind.IDLE)
                        }
                        AppButton("Validate", primary = true) {
                            scope.launch {
                                try {
                                    if (parsedSchema == null) { setStatus("No schema loaded", StatusKind.ERROR); return@launch }
                                    if (ruleValue.text.isBlank()) { setStatus("Rule is empty", StatusKind.IDLE); return@launch }
                                    val asts   = Parser(ruleValue.text).parseRules()
                                    val result = Validator.validate(asts = asts, schema = parsedSchema!!, actions = parsedActionSchema)
                                    if (result.isValid) {
                                        setStatus("✓ Validation passed", StatusKind.SUCCESS)
                                        diagnosticsText = "No issues found"
                                        diagnosticsList = emptyList()
                                    } else {
                                        setStatus("✗ ${result.diagnostics.size} issue(s) found", StatusKind.ERROR)
                                        diagnosticsList = result.diagnostics
                                        diagnosticsText = result.diagnostics.joinToString("\n") { d ->
                                            "[${d.severity}] ${d.message}${d.suggestion?.let { " → $it" } ?: ""}"
                                        }
                                    }
                                } catch (e: Exception) {
                                    setStatus("Parse error: ${e.message}", StatusKind.ERROR)
                                    diagnosticsText = e.toString()
                                    diagnosticsList = emptyList()
                                }
                            }
                        }
                    }
                }

                PanelDivider()

                // ── Code Editor ───────────────────────────────────────────────
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .clip(RoundedCornerShape(6.dp))
                        .background(Bg)
                        .border(1.dp, BorderColor, RoundedCornerShape(6.dp)),
                ) {
                    BasicTextField(
                        value = ruleValue,
                        onValueChange = { ruleValue = it },
                        modifier = Modifier.fillMaxSize().padding(14.dp),
                        textStyle = TextStyle(
                            fontFamily = FontFamily.Monospace,
                            fontSize   = 13.sp,
                            color      = TextPrimary,
                            lineHeight  = 20.sp,
                        ),
                        cursorBrush = SolidColor(PrimaryBlue),
                    )
                    if (ruleValue.text.isEmpty()) {
                        Text(
                            text = "# Write your rules here…\nrule \"example\" {\n    when field > value\n    then action \"result\"\n}",
                            modifier = Modifier.padding(14.dp),
                            style = TextStyle(
                                fontFamily = FontFamily.Monospace,
                                fontSize   = 13.sp,
                                color      = TextMuted,
                                lineHeight  = 20.sp,
                            ),
                        )
                    }
                }

                Spacer(Modifier.height(10.dp))

                // ── Diagnostics ───────────────────────────────────────────────
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("Diagnostics", style = MaterialTheme.typography.h6, color = TextPrimary)
                    Spacer(Modifier.weight(1f))
                    if (diagnosticsList.isNotEmpty()) {
                        val errors   = diagnosticsList.count { it.severity == Severity.ERROR }
                        val warnings = diagnosticsList.count { it.severity == Severity.WARNING }
                        if (errors   > 0) Chip("$errors error${if (errors > 1)   "s" else ""}", AccentRed.copy(0.15f),    AccentRed)
                        if (warnings > 0) Chip("$warnings warning${if (warnings > 1) "s" else ""}", AccentOrange.copy(0.15f), AccentOrange)
                    }
                }
                Spacer(Modifier.height(6.dp))

                if (diagnosticsList.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(130.dp)
                            .clip(RoundedCornerShape(6.dp))
                            .background(Bg)
                            .border(1.dp, BorderColor, RoundedCornerShape(6.dp))
                            .padding(14.dp),
                    ) {
                        Text(
                            text  = diagnosticsText.ifBlank { "No diagnostics — press Validate to check your rule." },
                            style = TextStyle(fontFamily = FontFamily.Monospace, fontSize = 12.sp,
                                color = if (diagnosticsText.isBlank()) TextMuted else AccentGreen),
                        )
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(160.dp)
                            .clip(RoundedCornerShape(6.dp))
                            .background(Bg)
                            .border(1.dp, BorderColor, RoundedCornerShape(6.dp)),
                    ) {
                        items(diagnosticsList) { d ->
                            val rowBg    = when (d.severity) {
                                Severity.ERROR   -> AccentRed.copy(alpha = 0.07f)
                                Severity.WARNING -> AccentOrange.copy(alpha = 0.07f)
                                else             -> Color.Transparent
                            }
                            val dotColor = when (d.severity) {
                                Severity.ERROR   -> AccentRed
                                Severity.WARNING -> AccentOrange
                                else             -> PrimaryBlue
                            }
                            val lineLabel = d.line?.let { "L${it}${d.column?.let { c -> ":$c" } ?: ""}" }

                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(rowBg)
                                    .clickable {
                                        try {
                                            val line = d.line ?: -1
                                            val col  = d.column ?: -1
                                            if (line > 0) {
                                                val lines  = ruleValue.text.lines()
                                                var offset = 0
                                                for (i in 0 until minOf(line - 1, lines.size - 1)) offset += lines[i].length + 1
                                                if (col > 0) offset += (col - 1)
                                                ruleValue = TextFieldValue(ruleValue.text, selection = TextRange(offset.coerceIn(0, ruleValue.text.length)))
                                            }
                                        } catch (_: Exception) { }
                                    }
                                    .padding(horizontal = 12.dp, vertical = 7.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                Box(Modifier.size(7.dp).background(dotColor, CircleShape))
                                Text(
                                    text     = d.message,
                                    style    = TextStyle(fontSize = 12.sp, fontFamily = FontFamily.Monospace, color = TextPrimary),
                                    modifier = Modifier.weight(1f),
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis,
                                )
                                lineLabel?.let { Chip(it) }
                                d.suggestion?.let {
                                    Text("→ $it", style = MaterialTheme.typography.caption, color = AccentGreen,
                                        maxLines = 1, overflow = TextOverflow.Ellipsis)
                                }
                            }
                            Divider(color = BorderColor.copy(alpha = 0.4f), thickness = 0.5.dp)
                        }
                    }
                }
            }
        }

        // ── Status Bar ────────────────────────────────────────────────────────
        Surface(color = BgSurface, elevation = 0.dp) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .drawTopLine(1.dp, BorderColor)
                    .padding(horizontal = 18.dp, vertical = 5.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                val dot = when (statusKind) {
                    StatusKind.SUCCESS -> AccentGreen
                    StatusKind.ERROR   -> AccentRed
                    StatusKind.IDLE    -> TextMuted
                }
                Box(Modifier.size(7.dp).background(dot, CircleShape))
                Text(status, style = MaterialTheme.typography.caption, color = TextSecondary)
                Spacer(Modifier.weight(1f))
                parsedSchema?.let { Text("Schema: ${it.fields.size} fields", style = MaterialTheme.typography.caption) }
            }
        }
    }
}

// ── FieldItem ─────────────────────────────────────────────────────────────────
@Composable
fun FieldItem(id: FieldId, def: FieldDefinition, onInsert: (String) -> Unit) {
    val tc = fieldTypeColor(def.type)
    Column(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(id.value, style = MaterialTheme.typography.body1, color = TextPrimary,
                modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
            Box(
                modifier = Modifier.clip(RoundedCornerShape(3.dp)).background(tc.copy(0.15f)).padding(horizontal = 6.dp, vertical = 2.dp)
            ) { Text(def.type.name.lowercase(), style = MaterialTheme.typography.caption, color = tc) }
        }
        if (def.operators.isNotEmpty()) {
            Spacer(Modifier.height(3.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                def.operators.forEach { op ->
                    val opText = op.value
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(3.dp))
                            .background(BgHover)
                            .border(1.dp, BorderColor, RoundedCornerShape(3.dp))
                            .clickable {
                                val ph = when (def.type) {
                                    FieldType.TEXT, FieldType.STRING_SET -> " \"value\""
                                    FieldType.INTEGER, FieldType.DECIMAL -> " 0"
                                    FieldType.BOOLEAN -> " true"
                                    else -> " \"value\""
                                }
                                onInsert("${id.value} $opText$ph")
                            }
                            .padding(horizontal = 5.dp, vertical = 2.dp),
                    ) {
                        Text(opText, style = MaterialTheme.typography.caption, color = TextSecondary)
                    }
                }
            }
        }
        Divider(color = BorderColor.copy(alpha = 0.4f), thickness = 0.5.dp, modifier = Modifier.padding(top = 4.dp))
    }
}



