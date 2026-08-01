package ui.workbench

import ruleengine.core.domain.dto.NormalizerId
import ruleengine.core.domain.dto.OperatorId
import ruleengine.core.domain.dto.action.ActionArgType
import ruleengine.core.domain.dto.action.ActionDefinition
import ruleengine.core.domain.dto.action.ActionSchema
import ruleengine.core.domain.dto.field.FieldDefinition
import ruleengine.core.domain.dto.field.FieldId
import ruleengine.core.domain.dto.field.FieldSchema
import ruleengine.core.domain.dto.field.FieldType
import ruleengine.core.errors.Severity
import ruleengine.core.errors.ValidationDiagnostic
import ruleengine.dsl.parser.Parser
import ui.diagrams.model.RuleSource
import ui.workbench.model.catalog.CatalogRuleStatus
import ui.workbench.rules.ruleTreeFilesFrom
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * The lists the workbench derives before rendering.
 *
 * These were `remember` block bodies inside the editor screen and had no coverage. The two
 * deliberate divergences — the two action shapes, and the rule roster's status rule — are pinned
 * here so neither gets "tidied" into the other.
 */
class WorkbenchCatalogsTest {

    private val schema = FieldSchema(
        name = "s",
        fields = mapOf(
            FieldId(value = "amount") to FieldDefinition(
                id = FieldId(value = "amount"),
                type = FieldType.DECIMAL,
                alias = "amt",
                normalizers = listOf(NormalizerId(value = "trim")),
                operators = setOf(OperatorId(value = "equals")),
            ),
        ),
    )

    private val actions = ActionSchema(
        actions = mapOf(
            "flag" to ActionDefinition(
                name = "flag",
                argTypes = listOf(ActionArgType.STRING, ActionArgType.INTEGER),
            ),
        ),
    )

    // ── fields ────────────────────────────────────────────────────────────────

    @Test
    fun `a null schema yields no fields rather than throwing`() {
        assertEquals(expected = emptyList(), actual = catalogFieldsFrom(schema = null))
        assertEquals(expected = emptyList(), actual = builderCatalogFieldsFrom(schema = null))
    }

    @Test
    fun `the inspector's field carries its normalizers, alias and lowercased type`() {
        val field = catalogFieldsFrom(schema = schema).single()
        assertEquals(expected = "amount", actual = field.id)
        assertEquals(expected = "decimal", actual = field.type)
        assertEquals(expected = "amt", actual = field.alias)
        assertEquals(expected = listOf("trim"), actual = field.normalizers)
        assertEquals(expected = listOf("equals"), actual = field.operators)
    }

    // ── actions ───────────────────────────────────────────────────────────────

    /**
     * The two action shapes differ on purpose: the inspector describes the action and lists every
     * declared argument type, the builder is filling one argument in and shows only the first.
     */
    @Test
    fun `the inspector lists every argument type, the builder only the first`() {
        assertEquals(expected = "string, integer", actual = catalogActionsFrom(actions = actions).single().argType)
        assertEquals(expected = "string", actual = builderCatalogActionsFrom(actions = actions).single().argType)
    }

    @Test
    fun `an action with no declared arguments defaults to string for the builder`() {
        val none = ActionSchema(actions = mapOf("go" to ActionDefinition(name = "go", argTypes = emptyList())))
        assertEquals(expected = "string", actual = builderCatalogActionsFrom(actions = none).single().argType)
        assertEquals(expected = "", actual = catalogActionsFrom(actions = none).single().argType)
    }

    // ── diagnostics ───────────────────────────────────────────────────────────

    @Test
    fun `a diagnostic keeps the engine's own severity and its position`() {
        val ui = uiDiagnosticsFrom(
            diagnostics = listOf(
                ValidationDiagnostic(severity = Severity.WARNING, message = "m", line = 3, column = 5),
            ),
        ).single()

        assertEquals(expected = Severity.WARNING, actual = ui.severity)
        assertEquals(expected = 3, actual = ui.line)
        assertEquals(expected = 5, actual = ui.column)
    }

    // ── rule roster ───────────────────────────────────────────────────────────

    private fun rules(text: String) = Parser(input = text).parseRules()

    private val oneRule = rules(
        """
        rule "r" {
          when
            amount equals 1
          then
            flag "x"
        }
        """.trimIndent(),
    )

    @Test
    fun `any error marks every rule in the roster invalid`() {
        val roster = catalogRulesFrom(
            rules = oneRule, hasErrors = true, diagnosticsEmpty = false, ruleTextNotBlank = true,
        )
        assertEquals(expected = CatalogRuleStatus.INVALID, actual = roster.single().status)
    }

    @Test
    fun `a rule is valid only with no diagnostics at all and text in the buffer`() {
        assertEquals(
            expected = CatalogRuleStatus.VALID,
            actual = catalogRulesFrom(
                rules = oneRule, hasErrors = false, diagnosticsEmpty = true, ruleTextNotBlank = true,
            ).single().status,
        )
    }

    /** Warnings leave `hasErrors` false but `diagnosticsEmpty` false too — so the rule reads as draft. */
    @Test
    fun `a warning-only buffer leaves rules as draft, not valid`() {
        assertEquals(
            expected = CatalogRuleStatus.DRAFT,
            actual = catalogRulesFrom(
                rules = oneRule, hasErrors = false, diagnosticsEmpty = false, ruleTextNotBlank = true,
            ).single().status,
        )
    }

    // ── rule tree ─────────────────────────────────────────────────────────────

    @Test
    fun `with no manifest files the tree shows one synthetic current node`() {
        val tree = ruleTreeFilesFrom(
            parsedFiles = emptyList(),
            fallbackRuleIds = setOf("a", "", "b"),
            currentFile = null,
            diagnostics = emptyList(),
        ).single()

        assertEquals(expected = "current", actual = tree.relativePath)
        assertEquals(expected = listOf("a", "b"), actual = tree.rules.map { it.id }, message = "blank ids are dropped")
    }

    @Test
    fun `each manifest file becomes its own node with per-rule status`() {
        val tree = ruleTreeFilesFrom(
            parsedFiles = listOf(RuleSource(relativePath = "rules/a.rule", rules = oneRule)),
            fallbackRuleIds = emptySet(),
            currentFile = "rules/a.rule",
            diagnostics = listOf(ValidationDiagnostic(severity = Severity.ERROR, message = "boom")),
        ).single()

        assertEquals(expected = "rules/a.rule", actual = tree.relativePath)
        assertEquals(
            expected = CatalogRuleStatus.INVALID,
            actual = tree.rules.single().status,
            message = "an error in the open file invalidates its rules even without naming them",
        )
    }
}
