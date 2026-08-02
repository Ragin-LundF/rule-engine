package ui.workbench

import androidx.compose.ui.text.input.TextFieldValue
import kotlinx.coroutines.CoroutineScope
import ui.editor.rules.RuleEditorState
import ui.editor.rules.model.StatusKind
import ui.samples.model.LoadedSample
import ui.samples.model.SampleCategory
import ui.samples.model.SampleDescriptor
import ui.workbench.areas.applySample
import kotlin.coroutines.EmptyCoroutineContext
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Loading a sample replaces every part of the editor at once.
 *
 * This was an eleven-statement block inlined in a click handler and therefore untestable. The order
 * of those statements is load-bearing — see the KDoc on `applySample` — so these tests pin the
 * resulting state rather than any single write.
 */
class ApplySampleTest {

    private val descriptor = SampleDescriptor(
        id = "demo-v1",
        name = "Demo",
        description = "d",
        category = SampleCategory.entries.first(),
        manifestResPath = "m",
        schemaResPath = "s",
        actionsResPath = "a",
        ruleResPaths = listOf("r"),
    )

    private val loaded = LoadedSample(
        descriptor = descriptor,
        manifestYaml = """
            name: demo
            entries:
              - id: demo-entry
                schema: schema.yaml
                actions: actions.yaml
                rules:
                  - rules/big.rule
        """.trimIndent(),
        schemaYaml = """
            schema: demo-v1
            fields:
              amount:
                type: decimal
                operators: [gte, lte]
        """.trimIndent(),
        actionsYaml = """
            actions:
              label:
                argTypes: [string]
        """.trimIndent(),
        rulesText = RULE_TEXT,
        ruleFiles = listOf("rules/big.rule" to RULE_TEXT),
    )

    private val twoFileSample = LoadedSample(
        descriptor = descriptor,
        manifestYaml = """
            name: demo
            entries:
              - id: demo-entry
                schema: schema.yaml
                actions: actions.yaml
                rules:
                  - rules/a.rule
                  - rules/b.rule
        """.trimIndent(),
        schemaYaml = loaded.schemaYaml,
        actionsYaml = loaded.actionsYaml,
        rulesText = RULE_A + "\n\n" + RULE_B,
        ruleFiles = listOf("rules/a.rule" to RULE_A, "rules/b.rule" to RULE_B),
    )

    private fun state() = RuleEditorState(scope = CoroutineScope(EmptyCoroutineContext))

    @Test
    fun `the sample's three documents all land in the editor`() {
        val state = state()

        state.applySample(descriptor = descriptor, loaded = loaded)

        assertEquals(expected = loaded.schemaYaml, actual = state.schemaText.value)
        assertEquals(expected = loaded.schemaYaml, actual = state.schemaFieldValue.value.text)
        assertEquals(expected = loaded.actionsYaml, actual = state.actionSchemaText.value)
        assertEquals(expected = loaded.actionsYaml, actual = state.actionFieldValue.value.text)
        assertEquals(expected = loaded.rulesText, actual = state.ruleValue.value.text)
    }

    /**
     * Both parses run inside `applySample`, so the rule text never arrives without a schema to
     * validate it against.
     */
    @Test
    fun `both schemas are parsed, not just stored as text`() {
        val state = state()

        state.applySample(descriptor = descriptor, loaded = loaded)

        assertNotNull(actual = state.parsedSchema.value)
        assertNotNull(actual = state.parsedActionSchema.value)
        assertEquals(expected = "demo-v1", actual = state.parsedSchema.value!!.name)
        assertTrue(actual = state.parsedSchema.value!!.fields.isNotEmpty())
    }

    /**
     * The previous sample's diagnostics have to go, or they flash up against rules they were never
     * about until the debounced pass catches up.
     */
    @Test
    fun `the previous sample's diagnostics are cleared`() {
        val state = state()
        state.diagnosticsText.value = "stale"

        state.applySample(descriptor = descriptor, loaded = loaded)

        assertTrue(actual = state.diagnosticsList.value.isEmpty())
        assertEquals(expected = "", actual = state.diagnosticsText.value)
    }

    // ── one buffer behind every view ──────────────────────────────────────────

    /**
     * The Builder, the code editor, the tester and the diagrams all read [RuleEditorState.ruleValue].
     *
     * There used to be a second copy, `allRulesText`, taken when the files were loaded and never
     * updated. In All files — which is how a sample opens — the Builder wrote to one and the tester
     * and diagrams read the other, so a change made in the Builder was simply not there.
     */
    @Test
    fun `a builder edit is visible to every view that reads the buffer`() {
        val state = state()
        state.applySample(descriptor = descriptor, loaded = twoFileSample)

        val edited = state.ruleValue.value.text.replace(
            oldValue = """label "a"""",
            newValue = """label "a"
    add "a" to topics""",
        )
        state.ruleValue.value = TextFieldValue(text = edited)

        // Every view derives from this one value; re-loading All files must not undo it either.
        assertTrue(actual = state.ruleValue.value.text.contains(other = "add \"a\" to topics"))
        state.loadAllRuleFilesForCurrentEntry()
        assertTrue(
            actual = state.ruleValue.value.text.contains(other = "add \"a\" to topics"),
            message = "reloading All files dropped the edit: ${state.ruleValue.value.text}",
        )
    }

    /** All files puts the whole entry in the buffer, so the Builder sees every rule. */
    @Test
    fun `All files holds every rule of the entry in the buffer`() {
        val state = state()
        state.applySample(descriptor = descriptor, loaded = twoFileSample)

        assertTrue(actual = state.showAllRules.value)
        assertTrue(actual = state.ruleValue.value.text.contains(other = """rule "a""""))
        assertTrue(actual = state.ruleValue.value.text.contains(other = """rule "b""""))
    }

    // ── unsaved edits survive navigation ──────────────────────────────────────

    /**
     * A sample has no disk. [RuleEditorState.inMemoryRuleFiles] is its storage, and it was written
     * once at load and never again — so switching to another file handed back the text the sample
     * shipped with and dropped every Builder edit made since.
     */
    @Test
    fun `an edit is stashed in memory before another file replaces the buffer`() {
        val state = state()
        state.applySample(descriptor = descriptor, loaded = twoFileSample)
        state.loadSingleManifestRuleFile(relativePath = "rules/a.rule")

        state.ruleValue.value = TextFieldValue(text = state.ruleValue.value.text + "\n# edited")

        state.loadSingleManifestRuleFile(relativePath = "rules/b.rule")
        state.loadSingleManifestRuleFile(relativePath = "rules/a.rule")

        assertTrue(
            actual = state.ruleValue.value.text.contains(other = "# edited"),
            message = "edit was lost: ${state.ruleValue.value.text}",
        )
    }

    /** And the stash reaches the operand catalog, which reads the same files. */
    @Test
    fun `a variable added in one file is offered while editing another`() {
        val state = state()
        state.applySample(descriptor = descriptor, loaded = twoFileSample)
        state.loadSingleManifestRuleFile(relativePath = "rules/a.rule")

        state.ruleValue.value = TextFieldValue(
            text = state.ruleValue.value.text.replace(
                oldValue = """label "a"""",
                newValue = """label "a"
    add "a" to topics""",
            )
        )
        state.loadSingleManifestRuleFile(relativePath = "rules/b.rule")

        assertEquals(expected = listOf("${'$'}topics"), actual = variableIdsOf(state = state))
    }

    // ── the operand catalog keeps up with the buffer ──────────────────────────

    /**
     * A sample opens with every file concatenated into the buffer and no single file selected, so
     * the per-file view of the entry is the saved text and only the buffer has the edit. Reading the
     * files instead of the buffer is what used to hide a just-added variable from the Builder's
     * dropdowns — and for a sample, which is never written to disk, hide it forever.
     */
    @Test
    fun `a variable typed into the buffer reaches the operand catalog`() {
        val state = state()
        state.applySample(descriptor = descriptor, loaded = loaded)

        assertTrue(
            actual = variableIdsOf(state = state).isEmpty(),
            message = "the sample declares no variable to start with",
        )

        state.ruleValue.value = TextFieldValue(
            text = state.ruleValue.value.text.replace(
                oldValue = """label "big"""",
                newValue = """label "big"
    add "big" to topics""",
            )
        )

        assertEquals(expected = listOf("${'$'}topics"), actual = variableIdsOf(state = state))
    }

    private fun variableIdsOf(state: RuleEditorState): List<String> =
        builderCatalogVariablesFrom(
            files = state.parsedRuleFilesForCurrentEntryWithOpenBuffer(),
            uptoRuleId = null,
        ).map { info -> info.id }

    // ── manifest and file switching ───────────────────────────────────────────

    /** Without the manifest in state the manifest run diagram has no entry to draw for a sample. */
    @Test
    fun `the sample's manifest is loaded and its entry selected`() {
        val state = state()

        state.applySample(descriptor = descriptor, loaded = loaded)

        assertEquals(expected = loaded.manifestYaml, actual = state.manifestText.value)
        assertNotNull(actual = state.parsedManifest.value)
        assertEquals(expected = "demo-entry", actual = state.selectedManifestEntry.value)
    }

    /**
     * The regression this guards: every path that resolves a rule file by manifest-relative path read
     * from disk, and a sample has no directory. All-files came up empty, which the diagrams rendered as
     * "No valid rules to display".
     */
    @Test
    fun `the All-files view is populated for a sample`() {
        val state = state()

        state.applySample(descriptor = descriptor, loaded = loaded)

        assertTrue(actual = state.showAllRules.value)
        assertEquals(expected = RULE_TEXT, actual = state.ruleValue.value.text)
        assertEquals(
            expected = listOf("rules/big.rule"),
            actual = state.entryRuleSources.value.map { source -> source.relativePath },
        )
        assertEquals(
            expected = listOf("big"),
            actual = state.entryRuleSources.value.single().rules.map { rule -> rule.id },
        )
    }

    /** Switching to a single file used to report "Manifest base directory is not set" for a sample. */
    @Test
    fun `switching to a single rule file works for a sample`() {
        val state = state()
        state.applySample(descriptor = descriptor, loaded = loaded)

        state.loadSingleManifestRuleFile(relativePath = "rules/big.rule")

        assertFalse(actual = state.showAllRules.value)
        assertEquals(expected = "rules/big.rule", actual = state.selectedManifestRuleFile.value)
        assertEquals(expected = RULE_TEXT, actual = state.ruleValue.value.text)
        assertEquals(expected = StatusKind.SUCCESS, actual = state.statusKind.value)
    }

    /** And back again — the round trip the rule tree drives. */
    @Test
    fun `switching back to All files restores every rule file`() {
        val state = state()
        state.applySample(descriptor = descriptor, loaded = loaded)
        state.loadSingleManifestRuleFile(relativePath = "rules/big.rule")

        state.loadAllRuleFilesForCurrentEntry()

        assertTrue(actual = state.showAllRules.value)
        assertEquals(expected = RULE_TEXT, actual = state.ruleValue.value.text)
        assertEquals(expected = 1, actual = state.entryRuleSources.value.size)
    }

    /**
     * A project opened after a sample must read from disk again. Leaving the sample's files registered
     * would let a project entry resolve a same-named relative path to the sample's content.
     */
    @Test
    fun `loading a sample and then resetting clears the in-memory rule files`() {
        val state = state()
        state.applySample(descriptor = descriptor, loaded = loaded)
        assertTrue(actual = state.inMemoryRuleFiles.value.isNotEmpty())

        state.reset()

        assertTrue(actual = state.inMemoryRuleFiles.value.isEmpty())
    }

    /**
     * The reported symptom: a sample loaded after a project kept that project's rule sources and
     * All-files mode, so the workbench went on showing the previous rules — against the new schema,
     * which made every field in them read as undeclared.
     */
    @Test
    fun `a sample loaded after a project does not inherit the project's rule files`() {
        val state = state()
        state.manifestBaseDir.value = "/somewhere/else"
        state.inMemoryRuleFiles.value = mapOf("rules/stale.rule" to "rule \"stale\" {}")
        state.entryRuleSources.value = state.parseRuleSources(loaded = listOf("rules/stale.rule" to "rule \"x\" {}"))

        state.applySample(descriptor = descriptor, loaded = loaded)

        assertNull(actual = state.manifestBaseDir.value)
        assertEquals(expected = setOf("rules/big.rule"), actual = state.inMemoryRuleFiles.value.keys)
        assertEquals(
            expected = listOf("rules/big.rule"),
            actual = state.entryRuleSources.value.map { source -> source.relativePath },
        )
    }

    @Test
    fun `the status line names the sample`() {
        val state = state()

        state.applySample(descriptor = descriptor, loaded = loaded)

        assertEquals(expected = "Loaded sample: Demo", actual = state.status.value)
        assertEquals(expected = StatusKind.SUCCESS, actual = state.statusKind.value)
    }

    /** An unparseable sample still loads its text; only the parse comes back null. */
    @Test
    fun `a broken schema leaves the text in place and the parse empty`() {
        val state = state()

        state.applySample(
            descriptor = descriptor,
            loaded = loaded.copy(schemaYaml = "fields: [ this is not a schema"),
        )

        assertEquals(expected = "fields: [ this is not a schema", actual = state.schemaText.value)
        assertNull(actual = state.parsedSchema.value)
        assertEquals(expected = loaded.rulesText, actual = state.ruleValue.value.text, message = "rules still load")
    }

    private companion object {
        const val RULE_TEXT: String = "rule \"big\" {\n  when\n    amount >= 500\n  then\n    label \"big\"\n}"
        const val RULE_A: String = "rule \"a\" {\n  when\n    amount >= 1\n  then\n    label \"a\"\n}"
        const val RULE_B: String = "rule \"b\" {\n  when\n    amount >= 2\n  then\n    label \"b\"\n}"
    }
}
