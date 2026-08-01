package ui.workbench

import ruleengine.core.domain.dto.action.ActionSchema
import ruleengine.core.domain.dto.field.FieldSchema
import ruleengine.core.errors.ValidationDiagnostic
import ruleengine.dsl.ast.ArithmeticValueAst
import ruleengine.dsl.ast.BooleanLiteral
import ruleengine.dsl.ast.FieldAccessAst
import ruleengine.dsl.ast.FunctionCallValueAst
import ruleengine.dsl.ast.LiteralValueAst
import ruleengine.dsl.ast.NumberLiteral
import ruleengine.dsl.ast.RuleAst
import ruleengine.dsl.ast.StringLiteral
import ruleengine.dsl.ast.ValueExpressionAst
import ruleengine.dsl.ast.VariableRefAst
import ui.builder.OperatorOptions
import ui.builder.model.catalog.CatalogActionInfo
import ui.builder.model.catalog.CatalogFieldInfo
import ui.builder.toCatalogFieldInfo
import ui.diagrams.model.RuleSource
import ui.workbench.model.UiDiagnostic
import ui.workbench.model.catalog.CatalogField
import ui.workbench.model.catalog.CatalogRule
import ui.workbench.model.catalog.CatalogRuleStatus

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
 * Rule output variables in scope at [uptoRuleId], as extra entries for the builder's operand picker.
 *
 * Scope follows the engine's own rule: a `$name` only resolves when an *earlier* rule of the entry
 * assigns it, where "earlier" means manifest file order and then in-file source order — exactly the
 * order [files] arrives in. Passing `null` for [uptoRuleId] returns every variable of the entry.
 *
 * Ids carry the `$` prefix so the picker writes the DSL spelling straight through, and so
 * `scalarPaths` can keep them out of plain condition rows, where the parser would read `$total` as
 * a field name.
 */
internal fun builderCatalogVariablesFrom(
    files: List<RuleSource>,
    uptoRuleId: String?,
): List<CatalogFieldInfo> {
    val variables = LinkedHashMap<String, CatalogFieldInfo>()
    for (file in files) {
        for (rule in file.rules) {
            if (rule.id == uptoRuleId) {
                return variables.values.toList()
            }
            for (assignment in rule.assignments) {
                variables[assignment.name] = CatalogFieldInfo(
                    id = "\$${assignment.name}",
                    type = inferredVariableType(expr = assignment.expression),
                )
            }
        }
    }
    return variables.values.toList()
}

/**
 * Best-effort value type of a `set` expression, used only to pick the operator list.
 *
 * An aggregate or a calculation is always numeric and a literal types itself. A field path or
 * another variable is left as [OperatorOptions.VARIABLE_TYPE]: resolving a path here would duplicate
 * the schema walk the engine already does, and the engine puts no type restriction on a variable
 * anyway, so the wider operator list is the honest answer rather than a guess.
 */
private fun inferredVariableType(expr: ValueExpressionAst): String = when (expr) {
    is FunctionCallValueAst, is ArithmeticValueAst -> "decimal"
    is LiteralValueAst -> when (expr.literal) {
        is NumberLiteral -> "decimal"
        is StringLiteral -> "text"
        is BooleanLiteral -> "boolean"
        else -> OperatorOptions.VARIABLE_TYPE
    }

    is FieldAccessAst, is VariableRefAst -> OperatorOptions.VARIABLE_TYPE
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
