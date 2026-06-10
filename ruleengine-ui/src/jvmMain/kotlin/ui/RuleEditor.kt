package ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
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
import androidx.compose.foundation.lazy.items
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isShiftPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.rememberWindowState
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import ruleengine.compiler.Validator
import ruleengine.core.errors.Severity
import ruleengine.dsl.parser.Parser
import ruleengine.manifest.ManifestEntry
import ruleengine.manifest.ManifestLoader
import ruleengine.schema.ActionSchemaLoader
import ruleengine.schema.FieldSchemaLoader
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
import ui.editor.rules.ViewMode
import ui.editor.rules.ViewModeToggle
import ui.editor.rules.autoClosingBraceDedent
import ui.editor.rules.drawTopLine
import ui.editor.rules.dslLineOpensBlock
import ui.editor.rules.isContextuallyImmediate
import ui.editor.rules.sections.TopBarSection
import java.nio.file.Files
import java.nio.file.Path

// ── Main composable ───────────────────────────────────────────────────────────

@Composable
actual fun RuleEditor() {
    val scope = rememberCoroutineScope()
    // Centralized state container for the editor
    val state = remember { RuleEditorState(scope = scope) }

    var schemaText by state.schemaText
    var schemaFieldValue by state.schemaFieldValue
    var ruleValue by state.ruleValue
    var status by state.status
    var statusKind by state.statusKind

    var parsedSchema by state.parsedSchema
    var actionSchemaText by state.actionSchemaText
    var actionFieldValue by state.actionFieldValue
    var parsedActionSchema by state.parsedActionSchema
    var manifestText by state.manifestText
    var manifestBaseDir by state.manifestBaseDir
    var parsedManifest by state.parsedManifest
    var selectedManifestEntry by state.selectedManifestEntry
    var diagnosticsList by state.diagnosticsList
    var diagnosticsText by state.diagnosticsText

    // ── Editor expand/collapse state ──────────────────────────────────────────
    var schemaExpanded by state.schemaExpanded
    var actionsExpanded by state.actionsExpanded
    var showManifestYaml by state.showManifestYaml

    // ── Editor UX state ───────────────────────────────────────────────────────
    val editorScrollState = rememberScrollState()
    var textLayoutResult by remember { mutableStateOf<TextLayoutResult?>(value = null) }
    var cursorRect by state.cursorRect

    var showAutoComplete by state.showAutoComplete
    var autoCompleteIndex by state.autoCompleteIndex
    var autoCompleteWord by state.autoCompleteWord
    var autoCompleteWordStart by state.autoCompleteWordStart
    var dslContext by state.dslContext
    var splitFraction by state.splitFraction
    var viewMode by state.viewMode
    // ── Diagram-specific state ────────────────────────────────────────────────
    val diagramGraphicsLayer = rememberGraphicsLayer()
    var showExpandedDiagram by state.showExpandedDiagram

    // ── Parsed rules for live diagram view ────────────────────────────────────
    val diagramRules = remember(key1 = ruleValue.text) {
        runCatching { Parser(input = ruleValue.text).parseRules() }.getOrElse { emptyList() }
    }

    fun setStatus(msg: String, kind: StatusKind) {
        status = msg; statusKind = kind
    }

    // ── Helper to load a manifest entry ──────────────────────────────────────
    fun loadManifestEntry(entry: ManifestEntry) {
        selectedManifestEntry = entry.id
        val base = manifestBaseDir ?: return
        var loadedRules = 0

        // Load schema if referenced
        entry.schema?.let { sp ->
            runCatching {
                val p = Path.of(base, sp)
                val c = Files.readString(p)
                schemaText = c
                schemaFieldValue = TextFieldValue(c)
                parsedSchema = runCatching { FieldSchemaLoader.loadFromString(c, p.fileName.toString()) }.getOrNull()
            }
        }
        // Load actions if referenced
        entry.actions?.let { ap ->
            runCatching {
                val p = Path.of(base, ap)
                val c = Files.readString(p)
                actionSchemaText = c
                actionFieldValue = TextFieldValue(c)
                parsedActionSchema = runCatching { ActionSchemaLoader.loadFromString(c) }.getOrNull()
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
            if (combined.isNotBlank()) ruleValue = TextFieldValue(text = combined)
        }
        setStatus(
            msg = "Loaded '${entry.id}'" +
                    (if (entry.schema != null) ", schema" else "") +
                    (if (entry.actions != null) ", actions" else "") +
                    (if (loadedRules > 0) ", $loadedRules rule file(s)" else ""),
            kind = StatusKind.SUCCESS,
        )
    }

    // ── Auto-load first manifest entry when manifest is newly set ─────────────
    LaunchedEffect(key1 = parsedManifest) {
        val manifest = parsedManifest ?: run {
            selectedManifestEntry = null
            return@LaunchedEffect
        }
        val first = manifest.entries.firstOrNull() ?: return@LaunchedEffect
        // Only auto-load when no entry is already selected (prevents unwanted override
        // when the same manifest is re-parsed after a text edit).
        if (selectedManifestEntry == null) {
            loadManifestEntry(entry = first)
        }
    }

    // ── Syntax-highlighted display value ──────────────────────────────────────
    // Annotation is cached by text+schema only. The final TextFieldValue is
    // NOT wrapped in remember so its selection always reflects the current cursor
    // position — this fixes arrow-key navigation (stale-selection bug).
    val annotatedRule = remember(ruleValue.text, parsedSchema, parsedActionSchema, diagnosticsList) {
        annotateRule(
            text = ruleValue.text,
            schema = parsedSchema,
            actions = parsedActionSchema,
            diagnostics = diagnosticsList
        )
    }
    val highlightedValue = TextFieldValue(
        annotatedString = annotatedRule,
        selection = ruleValue.selection,
        composition = ruleValue.composition,
    )

    // ── Context-aware autocomplete suggestions ────────────────────────────────
    val filteredSuggestions = remember(autoCompleteWord, dslContext, parsedSchema, parsedActionSchema) {
        val candidates = buildContextualCompletions(
            context = dslContext,
            schema = parsedSchema,
            actionSchema = parsedActionSchema,
        )
        if (autoCompleteWord.isEmpty()) {
            candidates.take(n = 8)
        } else {
            candidates
                .filter {
                    it.label.startsWith(
                        prefix = autoCompleteWord,
                        ignoreCase = true
                    ) && it.label != autoCompleteWord
                }
                .sortedWith(comparator = compareBy({ it.kind.ordinal }, { it.label }))
                .take(n = 8)
        }
    }

    // ── Track word + DSL context on every cursor move ─────────────────────────
    LaunchedEffect(key1 = ruleValue.text, key2 = ruleValue.selection.start) {
        val cursor = ruleValue.selection.start
        val (wordStart, word) = extractCurrentWord(text = ruleValue.text, cursorPos = cursor)
        autoCompleteWordStart = wordStart
        autoCompleteWord = word
        autoCompleteIndex = 0

        val ctx = analyzeDslContext(text = ruleValue.text, cursorPos = cursor, schema = parsedSchema)
        dslContext = ctx

        val lastChar = if (cursor > 0) ruleValue.text.getOrNull(cursor - 1) else null
        val afterSpace = lastChar == ' ' || lastChar == '\n'
        showAutoComplete = word.isNotEmpty() || (afterSpace && isContextuallyImmediate(context = ctx))
    }

    // ── Debounced auto-validation ──────────────────────────────────────────────
    LaunchedEffect(ruleValue.text) {
        if (ruleValue.text.isBlank()) {
            diagnosticsList = emptyList()
            diagnosticsText = ""
            return@LaunchedEffect
        }
        delay(700)
        runCatching {
            if (parsedSchema == null) return@LaunchedEffect
            val asts = Parser(input = ruleValue.text).parseRules()
            val result = Validator.validate(asts = asts, schema = parsedSchema!!, actions = parsedActionSchema)
            diagnosticsList = result.diagnostics
            diagnosticsText = if (result.isValid) "No issues found" else ""
            setStatus(
                msg = if (result.isValid) "✓ Validation passed" else "✗ ${result.diagnostics.size} issue(s)",
                kind = if (result.isValid) StatusKind.SUCCESS else StatusKind.ERROR,
            )
        }
    }

    // ── Accept an autocomplete suggestion ─────────────────────────────────────
    fun acceptSuggestion(item: CompletionItem) {
        val cursor = ruleValue.selection.start
        val newText = ruleValue.text.substring(startIndex = 0, endIndex = autoCompleteWordStart) +
                item.insertText +
                ruleValue.text.substring(startIndex = cursor)
        val newPos = autoCompleteWordStart + item.insertText.length
        ruleValue = TextFieldValue(newText, selection = TextRange(index = newPos))
        showAutoComplete = false
    }

    Column(modifier = Modifier.fillMaxSize().background(color = Bg)) {

        // ── Top Bar ───────────────────────────────────────────────────────────
        TopBarSection(state = state, scope = scope)

        // ── Main layout ───────────────────────────────────────────────────────
        BoxWithConstraints(
            modifier = Modifier.weight(weight = 1f).fillMaxWidth().padding(all = 12.dp),
        ) {
            val leftWidthDp = maxWidth * splitFraction

            Row(modifier = Modifier.fillMaxSize()) {

                // ── Left panel ────────────────────────────────────────────────
                Column(
                    modifier = Modifier
                        .width(width = leftWidthDp)
                        .fillMaxHeight()
                        .clip(shape = RoundedCornerShape(8.dp))
                        .background(color = BgSurface)
                        .border(width = 1.dp, color = BorderColor, shape = RoundedCornerShape(8.dp))
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
                                    setStatus(msg = "Example schema loaded", kind = StatusKind.SUCCESS)
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
                                            setStatus(msg = "Schema loaded", kind = StatusKind.SUCCESS)
                                        }
                                    }
                                },
                                onSave = {
                                    if (schemaText.isNotBlank()) {
                                        saveSchemaToFile(filename = "schema.yaml", content = schemaText)
                                        setStatus(msg = "Schema saved", kind = StatusKind.SUCCESS)
                                    } else {
                                        setStatus(msg = "Nothing to save", kind = StatusKind.IDLE)
                                    }
                                },
                                onClear = {
                                    schemaText = ""
                                    schemaFieldValue = TextFieldValue(text = "")
                                    parsedSchema = null
                                    setStatus(msg = "Schema cleared", kind = StatusKind.IDLE)
                                },
                                onInsertField = { ins ->
                                    val pos = ruleValue.selection.start
                                    val newText = ruleValue.text.substring(0, pos) + ins + ruleValue.text.substring(
                                        startIndex = pos
                                    )
                                    ruleValue = TextFieldValue(
                                        text = newText,
                                        selection = TextRange(index = pos + ins.length)
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
                                    setStatus(msg = "Example action schema loaded", kind = StatusKind.SUCCESS)
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
                                            setStatus(msg = "Actions loaded", kind = StatusKind.SUCCESS)
                                        }
                                    }
                                },
                                onSave = {
                                    if (actionSchemaText.isNotBlank()) {
                                        saveActionsToFile(filename = "actions.yaml", content = actionSchemaText)
                                        setStatus(msg = "Actions saved", kind = StatusKind.SUCCESS)
                                    } else {
                                        setStatus(msg = "Nothing to save", kind = StatusKind.IDLE)
                                    }
                                },
                                onClear = {
                                    actionSchemaText = ""
                                    actionFieldValue = TextFieldValue("")
                                    parsedActionSchema = null
                                    setStatus(msg = "Actions cleared", kind = StatusKind.IDLE)
                                },
                                onInsertAction = { ins ->
                                    val pos = ruleValue.selection.start
                                    val newText = ruleValue.text.substring(0, pos) + ins + ruleValue.text.substring(pos)
                                    ruleValue = TextFieldValue(newText, selection = TextRange(index = pos + ins.length))
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
                                    var manifestFieldValue by remember {
                                        mutableStateOf(value = TextFieldValue(text = manifestText))
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
                                Row(horizontalArrangement = Arrangement.spacedBy(space = 6.dp)) {
                                    AppButton(label = "Example") {
                                        manifestText = MANIFEST_EXAMPLE
                                        parsedManifest =
                                            runCatching {
                                                ManifestLoader.loadFromString(content = MANIFEST_EXAMPLE)
                                            }.getOrNull()
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
                                    AppButton("Clear", danger = true) {
                                        manifestText = ""
                                        parsedManifest = null
                                        selectedManifestEntry = null
                                        setStatus(msg = "Manifest cleared", kind = StatusKind.IDLE)
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
                                        .clickable { loadManifestEntry(entry = entry) }
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

                Spacer(modifier = Modifier.width(12.dp))

                // ── Right panel ───────────────────────────────────────────────
                Column(
                    modifier = Modifier
                        .weight(weight = 0.67f)
                        .fillMaxHeight()
                        .clip(shape = RoundedCornerShape(size = 8.dp))
                        .background(color = BgSurface)
                        .border(width = 1.dp, color = BorderColor, shape = RoundedCornerShape(size = 8.dp))
                        .padding(all = 14.dp),
                ) {
                    // ── Header: title + view-mode toggle + action buttons ─────
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Rule Editor", style = MaterialTheme.typography.h6, color = TextPrimary)
                        Spacer(Modifier.width(width = 14.dp))
                        // ── Code / Diagram tab strip ──────────────────────────
                        ViewModeToggle(
                            current = viewMode,
                            onChange = { viewMode = it },
                        )
                        Spacer(Modifier.weight(1f))
                        // Action buttons — only shown in Code mode
                        if (viewMode == ViewMode.CODE) {
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                AppButton(label = "Load Rule") {
                                    scope.launch {
                                        val c = pickRuleFile()
                                        if (c != null) {
                                            ruleValue = TextFieldValue(text = c)
                                            setStatus(msg = "Rule loaded", kind = StatusKind.SUCCESS)
                                        } else {
                                            setStatus(msg = "Load cancelled", kind = StatusKind.IDLE)
                                        }
                                    }
                                }
                                AppButton("Save Rule") {
                                    if (ruleValue.text.isNotBlank()) {
                                        saveRuleToFile(filename = "rule.rule", content = ruleValue.text)
                                        setStatus(msg = "Rule saved", kind = StatusKind.SUCCESS)
                                    } else {
                                        setStatus(msg = "Nothing to save", kind = StatusKind.IDLE)
                                    }
                                }
                                AppButton("Copy Rule") {
                                    if (ruleValue.text.isNotBlank()) {
                                        copyToClipboard(ruleValue.text)
                                        setStatus(msg = "Rule copied to clipboard", kind = StatusKind.SUCCESS)
                                    } else {
                                        setStatus(msg = "Nothing to copy", kind = StatusKind.IDLE)
                                    }
                                }
                                AppButton("Validate", primary = true) {
                                    scope.launch {
                                        runCatching {
                                            if (parsedSchema == null) {
                                                setStatus(
                                                    msg = "No schema loaded",
                                                    kind = StatusKind.ERROR
                                                ); return@launch
                                            }
                                            if (ruleValue.text.isBlank()) {
                                                setStatus(msg = "Rule is empty", kind = StatusKind.IDLE); return@launch
                                            }
                                            val asts = Parser(input = ruleValue.text).parseRules()
                                            val result = Validator.validate(
                                                asts = asts,
                                                schema = parsedSchema!!,
                                                actions = parsedActionSchema
                                            )
                                            if (result.isValid) {
                                                setStatus(msg = "✓ Validation passed", kind = StatusKind.SUCCESS)
                                                diagnosticsText = "No issues found"
                                                diagnosticsList = emptyList()
                                            } else {
                                                setStatus(
                                                    msg = "✗ ${result.diagnostics.size} issue(s) found",
                                                    kind = StatusKind.ERROR
                                                )
                                                diagnosticsList = result.diagnostics
                                                diagnosticsText = result.diagnostics.joinToString(
                                                    separator = "\n"
                                                ) { d ->
                                                    "[${d.severity}] ${d.message}${
                                                        d.suggestion?.let {
                                                            " → $it"
                                                        } ?: ""
                                                    }"
                                                }
                                            }
                                        }.onFailure { e ->
                                            setStatus(msg = "Parse error: ${e.message}", kind = StatusKind.ERROR)
                                            diagnosticsText = e.toString()
                                            diagnosticsList = emptyList()
                                        }
                                    }
                                }
                            }
                        } // end if CODE
                        // ── Diagram-mode toolbar ──────────────────────────────────────────────
                        if (viewMode == ViewMode.DIAGRAM) {
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                AppButton(label = "Export PNG") {
                                    scope.launch {
                                        runCatching {
                                            val bitmap = diagramGraphicsLayer.toImageBitmap()
                                            saveDiagramAsPng(bitmap = bitmap)
                                            setStatus(msg = "Diagram exported as PNG", kind = StatusKind.SUCCESS)
                                        }.onFailure {
                                            setStatus(msg = "Export failed: ${it.message}", kind = StatusKind.ERROR)
                                        }
                                    }
                                }
                                AppButton(label = "⤢ Expand") {
                                    showExpandedDiagram = true
                                }
                            }
                        } // end if DIAGRAM
                    }

                    PanelDivider()

                    // ── Code Editor or Diagram view ───────────────────────────
                    if (viewMode == ViewMode.DIAGRAM) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f)
                                .clip(shape = RoundedCornerShape(size = 6.dp))
                                .border(width = 1.dp, color = BorderColor, shape = RoundedCornerShape(size = 6.dp)),
                        ) {
                            // Pass the capture layer down so recording happens on the
                            // full-height content column, not on this clipped viewport box.
                            RuleDiagramView(
                                rules = diagramRules,
                                captureLayer = diagramGraphicsLayer,
                            )
                        }
                    } else {

                        val lineNumberWidthDp = 48.dp
                        val editorPaddingDp = 14.dp
                        val lineCount = remember(ruleValue.text) {
                            ruleValue.text.lines().size.coerceAtLeast(minimumValue = 1)
                        }

                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f)
                                .clip(shape = RoundedCornerShape(size = 6.dp))
                                .background(color = Bg)
                                .border(width = 1.dp, color = BorderColor, shape = RoundedCornerShape(size = 6.dp)),
                        ) {
                            Row(modifier = Modifier.fillMaxSize()) {
                                // ── Line-number gutter ─────────────────────────────────
                                Box(
                                    modifier = Modifier
                                        .width(lineNumberWidthDp)
                                        .fillMaxHeight()
                                        .background(color = BgSurface)
                                        .drawTopLine(w = 0.dp, color = BorderColor),
                                ) {
                                    Column(
                                        modifier = Modifier
                                            .padding(top = editorPaddingDp, end = 8.dp, start = 4.dp)
                                            .offset { IntOffset(x = 0, y = -editorScrollState.value) },
                                        horizontalAlignment = Alignment.End,
                                    ) {
                                        val currentLine = remember(
                                            key1 = ruleValue.text,
                                            key2 = ruleValue.selection.start
                                        ) {
                                            ruleValue.text.take(
                                                n = ruleValue.selection.start.coerceIn(0, ruleValue.text.length)
                                            ).count { it == '\n' } + 1
                                        }
                                        repeat(times = lineCount) { i ->
                                            Text(
                                                text = "${i + 1}",
                                                style = TextStyle(
                                                    fontFamily = FontFamily.Monospace,
                                                    fontSize = 13.sp,
                                                    lineHeight = 20.sp,
                                                    color = if (i + 1 == currentLine) {
                                                        PrimaryBlue.copy(alpha = 0.7f)
                                                    } else TextMuted,
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
                                        value = highlightedValue,
                                        onValueChange = { newVal ->
                                            val isNewChar = newVal.text.length == ruleValue.text.length + 1
                                            val cursorPos = newVal.selection.start
                                            // Auto-dedent `}` when typed on an otherwise-whitespace line.
                                            if (isNewChar && cursorPos > 0 &&
                                                newVal.text.getOrNull(index = cursorPos - 1) == '}'
                                            ) {
                                                val (dedentedText, removed) = autoClosingBraceDedent(
                                                    text = newVal.text, bracePos = cursorPos - 1,
                                                )
                                                ruleValue = TextFieldValue(
                                                    text = dedentedText,
                                                    selection = TextRange(
                                                        index = (cursorPos - removed).coerceAtLeast(
                                                            minimumValue = 0
                                                        )
                                                    ),
                                                    composition = newVal.composition,
                                                )
                                            } else {
                                                ruleValue =
                                                    TextFieldValue(newVal.text, newVal.selection, newVal.composition)
                                            }
                                        },
                                        onTextLayout = { result ->
                                            textLayoutResult = result
                                            val cursor = ruleValue.selection.start
                                                .coerceIn(0, ruleValue.text.length.coerceAtLeast(minimumValue = 0))
                                            runCatching { cursorRect = result.getCursorRect(offset = cursor) }
                                        },
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .defaultMinSize(minHeight = 200.dp)
                                            .padding(all = editorPaddingDp)
                                            .onPreviewKeyEvent { event ->
                                                if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                                                when {
                                                    // ── Autocomplete navigation ───────────────────────
                                                    // Only consume direction keys when suggestions are available.
                                                    event.key == Key.Escape && showAutoComplete -> {
                                                        showAutoComplete = false; true
                                                    }

                                                    event.key == Key.DirectionDown && showAutoComplete &&
                                                            filteredSuggestions.isNotEmpty() -> {
                                                        autoCompleteIndex = (autoCompleteIndex + 1)
                                                            .coerceAtMost(maximumValue = filteredSuggestions.size - 1)
                                                        true
                                                    }

                                                    event.key == Key.DirectionUp && showAutoComplete &&
                                                            filteredSuggestions.isNotEmpty() -> {
                                                        autoCompleteIndex = (autoCompleteIndex - 1).coerceAtLeast(
                                                            minimumValue = 0
                                                        )
                                                        true
                                                    }

                                                    event.key == Key.Tab && showAutoComplete &&
                                                            filteredSuggestions.isNotEmpty() -> {
                                                        acceptSuggestion(item = filteredSuggestions[autoCompleteIndex])
                                                        true
                                                    }

                                                    // ── Enter: smart DSL indentation ──────────────────────
                                                    event.key == Key.Enter -> {
                                                        if (showAutoComplete) showAutoComplete = false
                                                        val text = ruleValue.text
                                                        val selStart = ruleValue.selection.start
                                                        val selEnd = ruleValue.selection.end
                                                        val lineStart = text.lastIndexOf(
                                                            char = '\n',
                                                            startIndex = selStart - 1
                                                        ) + 1
                                                        val currentLine = text.substring(lineStart, selStart)
                                                        val indent = currentLine.takeWhile { it == ' ' || it == '\t' }
                                                        // Add one extra indent level after block-opening lines.
                                                        val extra = if (
                                                            dslLineOpensBlock(trimmedLine = currentLine.trim())
                                                        ) {
                                                            "    "
                                                        } else ""
                                                        val newText =
                                                            text.substring(0, selStart) + "\n" + indent + extra +
                                                                    text.substring(selEnd)
                                                        ruleValue = TextFieldValue(
                                                            newText,
                                                            selection = TextRange(
                                                                index = selStart + 1 + indent.length + extra.length
                                                            )
                                                        )
                                                        true
                                                    }
                                                    // ── Tab: indent / dedent ──────────────────────────
                                                    event.key == Key.Tab -> {
                                                        val text = ruleValue.text
                                                        val selStart = ruleValue.selection.start
                                                        val selEnd = ruleValue.selection.end
                                                        if (event.isShiftPressed) {
                                                            val lineStart = text.lastIndexOf(
                                                                char = '\n',
                                                                startIndex = selStart - 1
                                                            ) + 1
                                                            val spaces = text.substring(startIndex = lineStart)
                                                                .takeWhile { it == ' ' }.length.coerceAtMost(
                                                                    maximumValue = 4
                                                                )
                                                            if (spaces > 0) {
                                                                val newText = text.substring(0, lineStart) +
                                                                        text.substring(startIndex = lineStart + spaces)
                                                                ruleValue = TextFieldValue(
                                                                    newText,
                                                                    selection = TextRange(
                                                                        index = (selStart - spaces).coerceAtLeast(
                                                                            minimumValue = lineStart
                                                                        )
                                                                    )
                                                                )
                                                            }
                                                        } else {
                                                            val newText = text.substring(0, selStart) + "    " +
                                                                    text.substring(startIndex = selEnd)
                                                            ruleValue = TextFieldValue(
                                                                newText,
                                                                selection = TextRange(index = selStart + 4)
                                                            )
                                                        }
                                                        true
                                                    }

                                                    else -> false
                                                }
                                            },
                                        textStyle = TextStyle(
                                            fontFamily = FontFamily.Monospace,
                                            fontSize = 13.sp,
                                            color = TextPrimary,
                                            lineHeight = 20.sp,
                                        ),
                                        cursorBrush = SolidColor(value = PrimaryBlue),
                                    )
                                }
                            }

                            // ── Placeholder (when editor is empty) ─────────────────────
                            if (ruleValue.text.isEmpty()) {
                                Text(
                                    text = "# Write your rules here…\nrule \"example\" {\n" +
                                            "    when field > value\n    then action \"result\"\n}",
                                    modifier = Modifier.padding(
                                        start = lineNumberWidthDp + 1.dp + editorPaddingDp,
                                        top = editorPaddingDp,
                                    ),
                                    style = TextStyle(
                                        fontFamily = FontFamily.Monospace,
                                        fontSize = 13.sp,
                                        color = TextMuted,
                                        lineHeight = 20.sp,
                                    ),
                                )
                            }

                            // ── Autocomplete popup overlay ─────────────────────────────
                            if (showAutoComplete && filteredSuggestions.isNotEmpty()) {
                                val density = LocalDensity.current
                                val xPos = with(receiver = density) {
                                    lineNumberWidthDp + 1.dp + editorPaddingDp + cursorRect.left.toDp()
                                }
                                val yPos = with(receiver = density) {
                                    editorPaddingDp + (cursorRect.bottom - editorScrollState.value).toDp()
                                }
                                AutoCompleteDropdown(
                                    modifier = Modifier.offset(x = xPos, y = yPos),
                                    suggestions = filteredSuggestions,
                                    selectedIndex = autoCompleteIndex,
                                    onSelect = { acceptSuggestion(item = it) },
                                    onDismiss = { showAutoComplete = false },
                                )
                            }
                        }
                    } // end CODE view

                    Spacer(Modifier.height(10.dp))

                    // ── Diagnostics ───────────────────────────────────────────
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text("Diagnostics", style = MaterialTheme.typography.h6, color = TextPrimary)
                        Spacer(Modifier.weight(1f))
                        if (diagnosticsList.isNotEmpty()) {
                            val errors = diagnosticsList.count { it.severity == Severity.ERROR }
                            val warnings = diagnosticsList.count { it.severity == Severity.WARNING }
                            if (errors > 0) {
                                Chip(
                                    label = "$errors error${if (errors > 1) "s" else ""}",
                                    bg = AccentRed.copy(alpha = 0.15f),
                                    textColor = AccentRed
                                )
                            }
                            if (warnings > 0) {
                                Chip(
                                    label = "$warnings warning${if (warnings > 1) "s" else ""}",
                                    bg = AccentOrange.copy(alpha = 0.15f),
                                    textColor = AccentOrange
                                )
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(6.dp))

                    if (diagnosticsList.isEmpty()) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(height = 130.dp)
                                .clip(shape = RoundedCornerShape(size = 6.dp))
                                .background(color = Bg)
                                .border(width = 1.dp, color = BorderColor, shape = RoundedCornerShape(size = 6.dp))
                                .padding(all = 14.dp),
                        ) {
                            Text(
                                text = diagnosticsText.ifBlank {
                                    "No diagnostics — press Validate to check your rule."
                                },
                                style = TextStyle(
                                    fontFamily = FontFamily.Monospace, fontSize = 12.sp,
                                    color = if (diagnosticsText.isBlank()) TextMuted else AccentGreen
                                ),
                            )
                        }
                    } else {
                        LazyColumn(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(height = 160.dp)
                                .clip(shape = RoundedCornerShape(size = 6.dp))
                                .background(color = Bg)
                                .border(width = 1.dp, color = BorderColor, shape = RoundedCornerShape(size = 6.dp)),
                        ) {
                            items(diagnosticsList) { d ->
                                val rowBg = when (d.severity) {
                                    Severity.ERROR -> AccentRed.copy(alpha = 0.07f)
                                    Severity.WARNING -> AccentOrange.copy(alpha = 0.07f)
                                    else -> Color.Transparent
                                }
                                val dotColor = when (d.severity) {
                                    Severity.ERROR -> AccentRed
                                    Severity.WARNING -> AccentOrange
                                    else -> PrimaryBlue
                                }
                                val lineLabel = d.line?.let { "L${it}${d.column?.let { c -> ":$c" } ?: ""}" }

                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .background(color = rowBg)
                                        .clickable {
                                            runCatching {
                                                val line = d.line ?: -1
                                                val col = d.column ?: -1
                                                if (line > 0) {
                                                    val lines = ruleValue.text.lines()
                                                    var offset = 0
                                                    for (i in 0 until minOf(
                                                        a = line - 1,
                                                        b = lines.size - 1
                                                    )) {
                                                        offset += lines[i].length + 1
                                                    }
                                                    if (col > 0) offset += (col - 1)
                                                    ruleValue = TextFieldValue(
                                                        ruleValue.text,
                                                        selection = TextRange(
                                                            index = offset.coerceIn(0, ruleValue.text.length)
                                                        )
                                                    )
                                                }
                                            }
                                        }
                                        .padding(horizontal = 12.dp, vertical = 7.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                ) {
                                    Box(Modifier.size(7.dp).background(dotColor, CircleShape))
                                    Text(
                                        text = d.message,
                                        style = TextStyle(
                                            fontSize = 12.sp,
                                            fontFamily = FontFamily.Monospace,
                                            color = TextPrimary
                                        ),
                                        modifier = Modifier.weight(1f),
                                        maxLines = 2,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                    lineLabel?.let { Chip(label = it) }
                                    d.suggestion?.let {
                                        Text(
                                            "→ $it", style = MaterialTheme.typography.caption, color = AccentGreen,
                                            maxLines = 1, overflow = TextOverflow.Ellipsis
                                        )
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
                Box(Modifier.size(7.dp).background(color = dot, shape = CircleShape))
                Text(status, style = MaterialTheme.typography.caption, color = TextSecondary)
                Spacer(Modifier.weight(1f))
                parsedSchema?.let { Text("Schema: ${it.fields.size} fields", style = MaterialTheme.typography.caption) }
            }
        }
    }

    // ── Expanded diagram window ───────────────────────────────────────────────
    // Opened via the "⤢ Expand" button in diagram mode.
    // Shares the same diagramRules state so it updates live while editing.
    if (showExpandedDiagram) {
        Window(
            onCloseRequest = { showExpandedDiagram = false },
            title = "Rule Diagram — Full View",
            state = rememberWindowState(size = DpSize(width = 1400.dp, height = 900.dp)),
        ) {
            AppTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = Bg,
                ) {
                    RuleDiagramView(rules = diagramRules)
                }
            }
        }
    }
}






