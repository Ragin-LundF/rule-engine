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
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isShiftPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
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
import ruleengine.core.domain.ActionArgType
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.IntOffset
import kotlinx.coroutines.delay

/**
 * Returns true when [trimmedLine] opens a DSL block that warrants an extra indent level
 * on the following line.
 */
private fun dslLineOpensBlock(trimmedLine: String): Boolean {
    return trimmedLine.endsWith('{') || trimmedLine == "when" || trimmedLine == "then"
}

/**
 * If [text] has `}` at [bracePos] preceded only by whitespace on the same line,
 * removes up to 4 leading spaces from that line to auto-dedent the brace.
 * Returns the modified text and the number of spaces removed.
 */
private fun autoClosingBraceDedent(text: String, bracePos: Int): Pair<String, Int> {
    val lineStart = text.lastIndexOf('\n', bracePos - 1) + 1
    val lineContent = text.substring(startIndex = lineStart, endIndex = bracePos)
    if (lineContent.isEmpty() || !lineContent.all { it == ' ' }) {
        return Pair(text, 0)
    }
    val spacesToRemove = lineContent.length.coerceAtMost(4)
    val newText = text.substring(0, lineStart) +
            lineContent.drop(spacesToRemove) +
            text.substring(bracePos)
    return Pair(newText, spacesToRemove)
}

/**
 * Returns true when the DSL context strongly implies an immediate next token
 * (e.g., operator after field, or action name at start of THEN line).
 * Used to show completions without a typed prefix.
 */
private fun isContextuallyImmediate(context: DslCursorContext): Boolean {
    val expectsOperator = context.section == DslSection.WHEN &&
            context.precedingField != null &&
            context.precedingOperator == null
    val expectsAction = context.section == DslSection.THEN && context.afterAction == null
    return expectsOperator || expectsAction
}

// ── Smart indentation helpers ─────────────────────────────────────────────────
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

// ── Main composable ───────────────────────────────────────────────────────────

@Composable
actual fun RuleEditor() {
    var schemaText       by remember { mutableStateOf("") }
    var schemaFieldValue by remember { mutableStateOf(TextFieldValue("")) }
    var ruleValue        by remember { mutableStateOf(TextFieldValue("")) }
    var status           by remember { mutableStateOf("Ready") }
    var statusKind       by remember { mutableStateOf(StatusKind.IDLE) }
    val scope = rememberCoroutineScope()

    var parsedSchema          by remember { mutableStateOf<FieldSchema?>(null) }
    var actionSchemaText      by remember { mutableStateOf("") }
    var actionFieldValue      by remember { mutableStateOf(TextFieldValue("")) }
    var parsedActionSchema    by remember { mutableStateOf<ActionSchema?>(null) }
    var manifestText          by remember { mutableStateOf("") }
    var manifestBaseDir       by remember { mutableStateOf<String?>(null) }
    var parsedManifest        by remember { mutableStateOf<ProjectManifest?>(null) }
    var selectedManifestEntry by remember { mutableStateOf<String?>(null) }
    var diagnosticsList       by remember { mutableStateOf<List<ValidationDiagnostic>>(emptyList()) }
    var diagnosticsText       by remember { mutableStateOf("") }

    // ── Editor UX state ───────────────────────────────────────────────────────
    val editorScrollState     = rememberScrollState()
    var textLayoutResult      by remember { mutableStateOf<TextLayoutResult?>(null) }
    var cursorRect            by remember { mutableStateOf(Rect.Zero) }
    var showAutoComplete      by remember { mutableStateOf(false) }
    var autoCompleteIndex     by remember { mutableStateOf(0) }
    var autoCompleteWord      by remember { mutableStateOf("") }
    var autoCompleteWordStart by remember { mutableStateOf(0) }
    var dslContext            by remember { mutableStateOf(DslCursorContext(section = DslSection.TOP_LEVEL)) }
    var splitFraction         by remember { mutableStateOf(0.33f) }

    fun setStatus(msg: String, kind: StatusKind) { status = msg; statusKind = kind }

    // ── Syntax-highlighted display value ──────────────────────────────────────
    val highlightedValue = remember(ruleValue, parsedSchema, parsedActionSchema, diagnosticsList) {
        TextFieldValue(
            annotatedString = annotateRule(ruleValue.text, parsedSchema, parsedActionSchema, diagnosticsList),
            selection       = ruleValue.selection,
            composition     = ruleValue.composition,
        )
    }

    // ── Context-aware autocomplete suggestions ────────────────────────────────
    val filteredSuggestions = remember(autoCompleteWord, dslContext, parsedSchema, parsedActionSchema) {
        val candidates = buildContextualCompletions(
            context = dslContext,
            schema = parsedSchema,
            actionSchema = parsedActionSchema,
        )
        if (autoCompleteWord.isEmpty()) {
            candidates.take(8)
        } else {
            candidates
                .filter { it.label.startsWith(autoCompleteWord, ignoreCase = true) && it.label != autoCompleteWord }
                .sortedWith(compareBy({ it.kind.ordinal }, { it.label }))
                .take(8)
        }
    }

    // ── Track word + DSL context on every cursor move ─────────────────────────
    LaunchedEffect(ruleValue.text, ruleValue.selection.start) {
        val cursor = ruleValue.selection.start
        val (wordStart, word) = extractCurrentWord(ruleValue.text, cursor)
        autoCompleteWordStart = wordStart
        autoCompleteWord      = word
        autoCompleteIndex     = 0

        val ctx = analyzeDslContext(text = ruleValue.text, cursorPos = cursor, schema = parsedSchema)
        dslContext = ctx

        // Show completions when a word prefix is typed OR when context implies an immediate token.
        val lastChar = if (cursor > 0) ruleValue.text.getOrNull(cursor - 1) else null
        val afterSpace = lastChar == ' ' || lastChar == '\n'
        showAutoComplete = word.isNotEmpty() || (afterSpace && isContextuallyImmediate(ctx))
    }

    // ── Debounced auto-validation ──────────────────────────────────────────────
    LaunchedEffect(ruleValue.text) {
        if (ruleValue.text.isBlank()) {
            diagnosticsList = emptyList()
            diagnosticsText = ""
            return@LaunchedEffect
        }
        delay(700)
        try {
            if (parsedSchema == null) return@LaunchedEffect
            val asts   = Parser(ruleValue.text).parseRules()
            val result = Validator.validate(asts = asts, schema = parsedSchema!!, actions = parsedActionSchema)
            diagnosticsList = result.diagnostics
            diagnosticsText = if (result.isValid) "No issues found" else ""
            setStatus(
                if (result.isValid) "✓ Validation passed" else "✗ ${result.diagnostics.size} issue(s)",
                if (result.isValid) StatusKind.SUCCESS else StatusKind.ERROR,
            )
        } catch (_: Exception) { /* show parse errors via manual Validate button */ }
    }

    // ── Accept an autocomplete suggestion ─────────────────────────────────────
    fun acceptSuggestion(item: CompletionItem) {
        val cursor  = ruleValue.selection.start
        val newText = ruleValue.text.substring(0, autoCompleteWordStart) +
                      item.insertText +
                      ruleValue.text.substring(cursor)
        val newPos  = autoCompleteWordStart + item.insertText.length
        ruleValue        = TextFieldValue(newText, selection = TextRange(newPos))
        showAutoComplete = false
    }

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
                            schemaFieldValue = TextFieldValue(c)
                            parsedSchema = try { FieldSchemaLoader.loadFromString(c, "ui-schema") } catch (_: Exception) { null }
                            setStatus("Schema loaded — ${parsedSchema?.fields?.size ?: 0} fields", StatusKind.SUCCESS)
                        } else setStatus("Schema load cancelled", StatusKind.IDLE)
                    }
                }
            }
        }

        // ── Main layout ───────────────────────────────────────────────────────
        BoxWithConstraints(
            modifier = Modifier.weight(1f).fillMaxWidth().padding(12.dp),
        ) {
            val leftWidthDp    = maxWidth * splitFraction

            Row(modifier = Modifier.fillMaxSize()) {

            // ── Left panel ────────────────────────────────────────────────────
            Column(
                modifier = Modifier
                    .width(leftWidthDp)
                    .fillMaxHeight()
                    .clip(RoundedCornerShape(8.dp))
                    .background(BgSurface)
                    .border(1.dp, BorderColor, RoundedCornerShape(8.dp))
                    .padding(14.dp),
            ) {
                // Panel title (fixed, not scrolling)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Schema", style = MaterialTheme.typography.h6, color = TextPrimary)
                    Spacer(Modifier.weight(1f))
                    parsedSchema?.let { Chip("${it.fields.size} fields", AccentGreen.copy(0.15f), AccentGreen) }
                }
                PanelDivider()

                // All sections in a single LazyColumn so every section gets space
                LazyColumn(modifier = Modifier.weight(1f).fillMaxWidth()) {

                    // ── Field Schema YAML ─────────────────────────────────────
                    item {
                        SectionHeader("Field Schema YAML")
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(140.dp)
                                .clip(RoundedCornerShape(4.dp))
                                .background(Bg)
                                .border(1.dp, BorderColor, RoundedCornerShape(4.dp))
                                .padding(8.dp),
                        ) {
                            YamlEditor(
                                value = schemaFieldValue,
                                onValueChange = { newVal ->
                                    schemaFieldValue = newVal
                                    schemaText = newVal.text
                                    parsedSchema = runCatching {
                                        FieldSchemaLoader.loadFromString(newVal.text, "ui-schema")
                                    }.getOrNull()
                                },
                                modifier = Modifier.fillMaxSize(),
                                editorType = YamlEditorType.FIELD_SCHEMA,
                                annotate = { text -> annotateYaml(text = text, editorType = YamlEditorType.FIELD_SCHEMA) },
                                buildCompletions = { ctx -> buildYamlCompletions(context = ctx, editorType = YamlEditorType.FIELD_SCHEMA) },
                                placeholder = "# Paste field schema YAML here…",
                            )
                        }
                        Spacer(Modifier.height(6.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            AppButton("Load") {
                                scope.launch {
                                    val c = pickSchemaFile()
                                    if (c != null) {
                                        schemaText   = c
                                        schemaFieldValue = TextFieldValue(c)
                                        parsedSchema = runCatching { FieldSchemaLoader.loadFromString(c, "ui-schema") }.getOrNull()
                                        setStatus("Schema loaded", StatusKind.SUCCESS)
                                    }
                                }
                            }
                            AppButton("Save") {
                                if (schemaText.isNotBlank()) {
                                    saveSchemaToFile("schema.yaml", schemaText)
                                    setStatus("Schema saved", StatusKind.SUCCESS)
                                } else setStatus("Nothing to save", StatusKind.IDLE)
                            }
                            AppButton("Clear", danger = true) {
                                schemaText = ""; schemaFieldValue = TextFieldValue(""); parsedSchema = null
                                setStatus("Schema cleared", StatusKind.IDLE)
                            }
                        }
                        PanelDivider()
                    }

                    // ── Action Schema YAML ────────────────────────────────────
                    item {
                        SectionHeader("Action Schema YAML")
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(110.dp)
                                .clip(RoundedCornerShape(4.dp))
                                .background(Bg)
                                .border(1.dp, BorderColor, RoundedCornerShape(4.dp))
                                .padding(8.dp),
                        ) {
                            YamlEditor(
                                value = actionFieldValue,
                                onValueChange = { newVal ->
                                    actionFieldValue   = newVal
                                    actionSchemaText   = newVal.text
                                    parsedActionSchema = runCatching {
                                        ActionSchemaLoader.loadFromString(newVal.text)
                                    }.getOrNull()
                                },
                                modifier = Modifier.fillMaxSize(),
                                editorType = YamlEditorType.ACTION_SCHEMA,
                                annotate = { text -> annotateYaml(text = text, editorType = YamlEditorType.ACTION_SCHEMA) },
                                buildCompletions = { ctx -> buildYamlCompletions(context = ctx, editorType = YamlEditorType.ACTION_SCHEMA) },
                                placeholder = "# Paste action schema YAML here…",
                            )
                        }
                        Spacer(Modifier.height(6.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            AppButton("Load") {
                                scope.launch {
                                    val c = pickSchemaFile()
                                    if (c != null) {
                                        actionSchemaText   = c
                                        actionFieldValue   = TextFieldValue(c)
                                        parsedActionSchema = runCatching { ActionSchemaLoader.loadFromString(c) }.getOrNull()
                                        setStatus("Actions loaded", StatusKind.SUCCESS)
                                    }
                                }
                            }
                            AppButton("Save") {
                                if (actionSchemaText.isNotBlank()) {
                                    saveActionsToFile("actions.yaml", actionSchemaText)
                                    setStatus("Actions saved", StatusKind.SUCCESS)
                                } else setStatus("Nothing to save", StatusKind.IDLE)
                            }
                            AppButton("Clear", danger = true) {
                                actionSchemaText = ""; actionFieldValue = TextFieldValue(""); parsedActionSchema = null
                                setStatus("Actions cleared", StatusKind.IDLE)
                            }
                        }
                        PanelDivider()
                    }

                    // ── Fields list ───────────────────────────────────────────
                    item { SectionHeader("Fields") }

                    if (parsedSchema != null) {
                        items(parsedSchema!!.fields.entries.toList()) { (fid, def) ->
                            FieldItem(fid, def) { ins ->
                                val pos     = ruleValue.selection.start
                                val newText = ruleValue.text.substring(0, pos) + ins + ruleValue.text.substring(pos)
                                ruleValue   = TextFieldValue(newText, selection = TextRange(pos + ins.length))
                            }
                        }
                    } else {
                        item {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(Bg)
                                    .border(1.dp, BorderColor, RoundedCornerShape(6.dp))
                                    .padding(12.dp),
                                contentAlignment = Alignment.Center,
                            ) {
                                Text("Load a schema to see fields", style = MaterialTheme.typography.body2)
                            }
                        }
                    }

                    // ── Available Actions ─────────────────────────────────────
                    parsedActionSchema?.let { aschema ->
                        item { PanelDivider(); SectionHeader("Available Actions") }
                        items(aschema.actions.entries.toList()) { (name, def) ->
                            ActionItem(name = name, def = def) { ins ->
                                val pos     = ruleValue.selection.start
                                val newText = ruleValue.text.substring(0, pos) + ins + ruleValue.text.substring(pos)
                                ruleValue   = TextFieldValue(newText, selection = TextRange(pos + ins.length))
                            }
                        }
                    }

                    // ── Manifest Entries ──────────────────────────────────────
                    parsedManifest?.let { manifest ->
                        item { PanelDivider(); SectionHeader("Manifest Entries") }
                        items(manifest.entries) { entry ->
                            val sel = selectedManifestEntry == entry.id
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(4.dp))
                                    .background(if (sel) BgElevated else Color.Transparent)
                                    .clickable {
                                        selectedManifestEntry = entry.id
                                        val base = manifestBaseDir ?: return@clickable
                                        var loadedRules = 0

                                        // Load schema
                                        entry.schema?.let { sp ->
                                            runCatching {
                                                val p = Path.of(base, sp)
                                                val c = Files.readString(p)
                                                schemaText   = c
                                                schemaFieldValue = TextFieldValue(c)
                                                parsedSchema = try { FieldSchemaLoader.loadFromString(c, p.fileName.toString()) } catch (_: Exception) { null }
                                            }
                                        }
                                        // Load actions
                                        entry.actions?.let { ap ->
                                            runCatching {
                                                val p = Path.of(base, ap)
                                                val c = Files.readString(p)
                                                actionSchemaText   = c
                                                actionFieldValue   = TextFieldValue(c)
                                                parsedActionSchema = try { ActionSchemaLoader.loadFromString(c) } catch (_: Exception) { null }
                                            }
                                        }
                                        // Load and concatenate all rule files
                                        if (entry.rules.isNotEmpty()) {
                                            val combined = buildString {
                                                entry.rules.forEachIndexed { idx, rp ->
                                                    runCatching {
                                                        val p = Path.of(base, rp)
                                                        val c = Files.readString(p)
                                                        if (idx > 0) append("\n\n")
                                                        append("# --- ${p.fileName} ---\n")
                                                        append(c)
                                                        loadedRules++
                                                    }
                                                }
                                            }
                                            if (combined.isNotBlank()) {
                                                ruleValue = TextFieldValue(combined)
                                            }
                                        }
                                        setStatus(
                                            "Loaded entry '${entry.id}'" +
                                            (if (entry.schema != null) ", schema" else "") +
                                            (if (entry.actions != null) ", actions" else "") +
                                            (if (loadedRules > 0) ", $loadedRules rule file(s)" else ""),
                                            StatusKind.SUCCESS,
                                        )
                                    }
                                    .padding(horizontal = 8.dp, vertical = 7.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Box(Modifier.size(6.dp).background(if (sel) PrimaryBlue else Color.Transparent, CircleShape))
                                Spacer(Modifier.width(8.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(entry.id, style = MaterialTheme.typography.body1, color = if (sel) TextPrimary else TextSecondary)
                                    if (entry.rules.isNotEmpty()) {
                                        Text("${entry.rules.size} rule file(s)", style = MaterialTheme.typography.caption)
                                    }
                                }
                                Chip("${entry.rules.size} rules")
                            }
                        }
                    }

                    // bottom padding
                    item { Spacer(Modifier.height(8.dp)) }
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
                val lineNumberWidthDp = 48.dp
                val editorPaddingDp   = 14.dp
                val lineCount = remember(ruleValue.text) {
                    ruleValue.text.lines().size.coerceAtLeast(1)
                }

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .clip(RoundedCornerShape(6.dp))
                        .background(Bg)
                        .border(1.dp, BorderColor, RoundedCornerShape(6.dp)),
                ) {
                    Row(modifier = Modifier.fillMaxSize()) {
                        // ── Line-number gutter ─────────────────────────────────
                        Box(
                            modifier = Modifier
                                .width(lineNumberWidthDp)
                                .fillMaxHeight()
                                .background(BgSurface)
                                .drawTopLine(0.dp, BorderColor), // visual baseline
                        ) {
                            Column(
                                modifier = Modifier
                                    .padding(top = editorPaddingDp, end = 8.dp, start = 4.dp)
                                    .offset { IntOffset(0, -editorScrollState.value) },
                                horizontalAlignment = Alignment.End,
                            ) {
                                repeat(lineCount) { i ->
                                    Text(
                                        text  = "${i + 1}",
                                        style = TextStyle(
                                            fontFamily = FontFamily.Monospace,
                                            fontSize   = 13.sp,
                                            lineHeight = 20.sp,
                                            color      = if (i + 1 == ruleValue.text.take(
                                                ruleValue.selection.start.coerceIn(0, ruleValue.text.length)
                                            ).count { it == '\n' } + 1) PrimaryBlue.copy(alpha = 0.7f) else TextMuted,
                                        ),
                                    )
                                }
                            }
                        }
                        // Gutter separator
                        Box(Modifier.width(1.dp).fillMaxHeight().background(BorderColor))

                        // ── Scrollable text area ───────────────────────────────
                        Column(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight()
                                .verticalScroll(editorScrollState),
                        ) {
                            BasicTextField(
                                value    = highlightedValue,
                                onValueChange = { newVal ->
                                    val isNewChar = newVal.text.length == ruleValue.text.length + 1
                                    val cursorPos = newVal.selection.start
                                    // Auto-dedent `}` when typed on an otherwise-whitespace line.
                                    if (isNewChar && cursorPos > 0 &&
                                        newVal.text.getOrNull(cursorPos - 1) == '}') {
                                        val (dedentedText, removed) = autoClosingBraceDedent(
                                            text = newVal.text, bracePos = cursorPos - 1,
                                        )
                                        ruleValue = TextFieldValue(
                                            text = dedentedText,
                                            selection = TextRange((cursorPos - removed).coerceAtLeast(0)),
                                            composition = newVal.composition,
                                        )
                                    } else {
                                        ruleValue = TextFieldValue(newVal.text, newVal.selection, newVal.composition)
                                    }
                                },
                                onTextLayout = { result ->
                                    textLayoutResult = result
                                    val cursor = ruleValue.selection.start
                                        .coerceIn(0, ruleValue.text.length.coerceAtLeast(0))
                                    runCatching { cursorRect = result.getCursorRect(cursor) }
                                },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .defaultMinSize(minHeight = 200.dp)
                                    .padding(editorPaddingDp)
                                    .onPreviewKeyEvent { event ->
                                        if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                                        when {
                                            // ── Autocomplete navigation ───────────────────────
                                            event.key == Key.Escape && showAutoComplete -> {
                                                showAutoComplete = false; true
                                            }
                                            event.key == Key.DirectionDown && showAutoComplete -> {
                                                autoCompleteIndex = (autoCompleteIndex + 1)
                                                    .coerceAtMost(filteredSuggestions.size - 1)
                                                true
                                            }
                                            event.key == Key.DirectionUp && showAutoComplete -> {
                                                autoCompleteIndex = (autoCompleteIndex - 1).coerceAtLeast(0)
                                                true
                                            }
                                            event.key == Key.Tab && showAutoComplete && filteredSuggestions.isNotEmpty() -> {
                                                acceptSuggestion(filteredSuggestions[autoCompleteIndex])
                                                true
                                            }
                                            // ── Enter: smart DSL indentation ──────────────────────
                                            // Always runs (also dismisses autocomplete if open)
                                            event.key == Key.Enter -> {
                                                if (showAutoComplete) showAutoComplete = false
                                                val text     = ruleValue.text
                                                val selStart = ruleValue.selection.start
                                                val selEnd   = ruleValue.selection.end
                                                val lineStart = text.lastIndexOf('\n', selStart - 1) + 1
                                                val currentLine = text.substring(lineStart, selStart)
                                                val indent    = currentLine.takeWhile { it == ' ' || it == '\t' }
                                                // Add one extra indent level after block-opening lines.
                                                val extra = if (dslLineOpensBlock(currentLine.trim())) "    " else ""
                                                val newText = text.substring(0, selStart) + "\n" + indent + extra +
                                                              text.substring(selEnd)
                                                ruleValue = TextFieldValue(newText,
                                                    selection = TextRange(selStart + 1 + indent.length + extra.length))
                                                true
                                            }
                                            // ── Tab: indent / dedent ──────────────────────────
                                            event.key == Key.Tab -> {
                                                val text     = ruleValue.text
                                                val selStart = ruleValue.selection.start
                                                val selEnd   = ruleValue.selection.end
                                                if (event.isShiftPressed) {
                                                    val lineStart = text.lastIndexOf('\n', selStart - 1) + 1
                                                    val spaces = text.substring(lineStart)
                                                        .takeWhile { it == ' ' }.length.coerceAtMost(4)
                                                    if (spaces > 0) {
                                                        val newText = text.substring(0, lineStart) +
                                                                      text.substring(lineStart + spaces)
                                                        ruleValue = TextFieldValue(newText,
                                                            selection = TextRange((selStart - spaces).coerceAtLeast(lineStart)))
                                                    }
                                                } else {
                                                    val newText = text.substring(0, selStart) + "    " +
                                                                  text.substring(selEnd)
                                                    ruleValue = TextFieldValue(newText,
                                                        selection = TextRange(selStart + 4))
                                                }
                                                true
                                            }
                                            else -> false
                                        }
                                    },
                                textStyle = TextStyle(
                                    fontFamily = FontFamily.Monospace,
                                    fontSize   = 13.sp,
                                    color      = TextPrimary,
                                    lineHeight = 20.sp,
                                ),
                                cursorBrush = SolidColor(PrimaryBlue),
                            )
                        }
                    }

                    // ── Placeholder (when editor is empty) ─────────────────────
                    if (ruleValue.text.isEmpty()) {
                        Text(
                            text = "# Write your rules here…\nrule \"example\" {\n    when field > value\n    then action \"result\"\n}",
                            modifier = Modifier.padding(
                                start = lineNumberWidthDp + 1.dp + editorPaddingDp,
                                top   = editorPaddingDp,
                            ),
                            style = TextStyle(
                                fontFamily = FontFamily.Monospace,
                                fontSize   = 13.sp,
                                color      = TextMuted,
                                lineHeight = 20.sp,
                            ),
                        )
                    }

                    // ── Autocomplete popup overlay ─────────────────────────────
                    if (showAutoComplete && filteredSuggestions.isNotEmpty()) {
                        val density = LocalDensity.current
                        val xPos = with(density) {
                            lineNumberWidthDp + 1.dp + editorPaddingDp + cursorRect.left.toDp()
                        }
                        val yPos = with(density) {
                            editorPaddingDp + (cursorRect.bottom - editorScrollState.value).toDp()
                        }
                        AutoCompleteDropdown(
                            modifier       = Modifier.offset(x = xPos, y = yPos),
                            suggestions    = filteredSuggestions,
                            selectedIndex  = autoCompleteIndex,
                            onSelect       = { acceptSuggestion(it) },
                            onDismiss      = { showAutoComplete = false },
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
            }  // Row
        }  // BoxWithConstraints

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
            Text(
                id.value,
                style    = MaterialTheme.typography.body1,
                color    = TextPrimary,
                modifier = Modifier.weight(1f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            // Quick "insert field name at cursor" button
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(3.dp))
                    .background(BgHover)
                    .border(1.dp, BorderColor, RoundedCornerShape(3.dp))
                    .clickable { onInsert(id.value) }
                    .padding(horizontal = 5.dp, vertical = 2.dp),
            ) {
                Text("⤵", style = MaterialTheme.typography.caption, color = TextMuted)
            }
            Spacer(Modifier.width(4.dp))
            // Type badge
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(3.dp))
                    .background(tc.copy(0.15f))
                    .padding(horizontal = 6.dp, vertical = 2.dp),
            ) {
                Text(def.type.name.lowercase(), style = MaterialTheme.typography.caption, color = tc)
            }
        }
        if (def.operators.isNotEmpty()) {
            Spacer(Modifier.height(3.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp), modifier = Modifier.fillMaxWidth()) {
                def.operators.forEach { op ->
                    val opText = op.value
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(3.dp))
                            .background(BgHover)
                            .border(1.dp, BorderColor, RoundedCornerShape(3.dp))
                            .clickable {
                                val ph = when (def.type) {
                                    FieldType.TEXT       -> " \"value\""
                                    FieldType.STRING_SET -> " [\"a\", \"b\"]"
                                    FieldType.INTEGER    -> " 0"
                                    FieldType.DECIMAL    -> " 0.0"
                                    FieldType.BOOLEAN    -> " true"
                                    FieldType.DATE       -> " \"2024-01-01\""
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

// ── ActionItem ────────────────────────────────────────────────────────────────
@Composable
fun ActionItem(name: String, def: ruleengine.core.domain.ActionDefinition, onInsert: (String) -> Unit) {
    val argColor: (ActionArgType) -> Color = { t ->
        when (t) {
            ActionArgType.STRING  -> ColorString
            ActionArgType.INTEGER -> ColorNumber
            ActionArgType.DECIMAL -> ColorNumber
        }
    }
    Column(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                name,
                modifier = Modifier.weight(1f),
                style    = MaterialTheme.typography.body1,
                color    = ColorAction,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            // Quick insert button
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(3.dp))
                    .background(BgHover)
                    .border(1.dp, BorderColor, RoundedCornerShape(3.dp))
                    .clickable {
                        val args = def.argTypes.joinToString(" ") { t ->
                            when (t) {
                                ActionArgType.INTEGER -> "0"
                                ActionArgType.DECIMAL -> "0.0"
                                ActionArgType.STRING  -> "\"value\""
                            }
                        }
                        onInsert(if (args.isNotEmpty()) "$name $args" else name)
                    }
                    .padding(horizontal = 5.dp, vertical = 2.dp),
            ) {
                Text("⤵", style = MaterialTheme.typography.caption, color = TextMuted)
            }
        }
        if (def.argTypes.isNotEmpty()) {
            Spacer(Modifier.height(3.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                def.argTypes.forEachIndexed { idx, argType ->
                    val ac = argColor(argType)
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(3.dp))
                            .background(ac.copy(alpha = 0.15f))
                            .padding(horizontal = 5.dp, vertical = 2.dp),
                    ) {
                        Text(
                            "arg${idx + 1}: ${argType.name.lowercase()}",
                            style = MaterialTheme.typography.caption,
                            color = ac,
                        )
                    }
                }
            }
        }
        Divider(color = BorderColor.copy(alpha = 0.4f), thickness = 0.5.dp, modifier = Modifier.padding(top = 4.dp))
    }
}



