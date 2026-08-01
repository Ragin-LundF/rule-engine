package ui.workbench

import ruleengine.core.domain.dto.ActionSchema
import ruleengine.core.domain.dto.FieldSchema
import ruleengine.core.errors.ValidationDiagnostic
import ruleengine.dsl.ast.RuleAst
import ui.builder.CatalogActionInfo
import ui.builder.CatalogFieldInfo
import ui.builder.toCatalogFieldInfo
import ui.workbench.model.CatalogField
import ui.workbench.model.CatalogRule
import ui.workbench.model.CatalogRuleStatus
import ui.workbench.model.UiDiagnostic

/**
 * The lists the workbench derives from a parsed schema, action schema and rule set.
 *
 * Pure functions, called from `remember` blocks in the editor screen. They are extracted so the
 * screen reads as layout rather than as data preparation — the `remember` keys stay at the call
 * site, because that is where staleness is decided.
 */

/** Schema fields as the inspector shows them: normalizers and alias included. */
internal fun catalogFieldsFrom(schema: FieldSchema?): List<CatalogField> {
    return schema?.fields?.values?.map { def ->
        CatalogField(
            id = def.id.value,
            type = def.type.name.lowercase(),
            operators = def.operators.map { it.value },
            normalizers = def.normalizers.map { it.value },
            alias = def.alias,
        )
    } ?: emptyList()
}

/** Schema fields as the builder's path picker needs them: recursive, with a format hint. */
internal fun builderCatalogFieldsFrom(schema: FieldSchema?): List<CatalogFieldInfo> {
    return schema?.fields?.values?.map { def -> def.toCatalogFieldInfo() } ?: emptyList()
}

/**
 * Actions for the inspector, whose `argType` lists *every* declared type.
 *
 * Deliberately different from [builderCatalogActionsFrom], which shows only the first. The inspector
 * is describing the action; the builder is filling in one argument.
 */
internal fun catalogActionsFrom(actions: ActionSchema?): List<CatalogActionInfo> {
    return actions?.actions?.values?.map { def ->
        CatalogActionInfo(
            name = def.name,
            argType = def.argTypes.joinToString { it.name.lowercase() },
        )
    } ?: emptyList()
}

/** Actions for the builder: the first argument type only, defaulting to `string`. */
internal fun builderCatalogActionsFrom(actions: ActionSchema?): List<CatalogActionInfo> {
    return actions?.actions?.values?.map { def ->
        CatalogActionInfo(
            name = def.name,
            argType = def.argTypes.firstOrNull()?.name?.lowercase() ?: "string",
        )
    } ?: emptyList()
}

/** Engine diagnostics as the UI carries them; severity is core's own enum. */
internal fun uiDiagnosticsFrom(diagnostics: List<ValidationDiagnostic>): List<UiDiagnostic> {
    return diagnostics.map { diagnostic ->
        UiDiagnostic(
            severity = diagnostic.severity,
            message = diagnostic.message,
            line = diagnostic.line,
            column = diagnostic.column,
        )
    }
}

/**
 * The rule roster with a status per rule.
 *
 * [diagnosticsEmpty] and [ruleTextNotBlank] are passed in rather than read here because the caller's
 * `remember` does *not* key on them: the roster deliberately does not refresh when diagnostics
 * change without [hasErrors] flipping. Taking them as arguments keeps that decision at the call
 * site, where the keys are.
 */
internal fun catalogRulesFrom(
    rules: List<RuleAst>,
    hasErrors: Boolean,
    diagnosticsEmpty: Boolean,
    ruleTextNotBlank: Boolean,
): List<CatalogRule> {
    return rules.map { ast ->
        CatalogRule(
            id = ast.id,
            status = when {
                hasErrors -> CatalogRuleStatus.INVALID
                diagnosticsEmpty && ruleTextNotBlank -> CatalogRuleStatus.VALID
                else -> CatalogRuleStatus.DRAFT
            },
        )
    }
}
