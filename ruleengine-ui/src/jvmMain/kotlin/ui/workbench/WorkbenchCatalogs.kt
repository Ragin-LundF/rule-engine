package ui.workbench

import ruleengine.core.analysis.FieldUsage
import ruleengine.core.domain.dto.action.ActionSchema
import ruleengine.core.domain.dto.field.FieldSchema
import ruleengine.core.errors.ValidationDiagnostic
import ruleengine.dsl.ast.ArithmeticValueAst
import ruleengine.dsl.ast.AssignmentKindAst
import ruleengine.dsl.ast.BooleanLiteral
import ruleengine.dsl.ast.FieldAccessAst
import ruleengine.dsl.ast.FunctionCallValueAst
import ruleengine.dsl.ast.LiteralValueAst
import ruleengine.dsl.ast.NumberLiteral
import ruleengine.dsl.ast.RuleAst
import ruleengine.dsl.ast.StringLiteral
import ruleengine.dsl.ast.ValueExpressionAst
import ruleengine.dsl.ast.VariableAssignmentAst
import ruleengine.dsl.ast.VariableRefAst
import ruleengine.evaluator.compiled.DslFunctions
import ruleengine.evaluator.compiled.FunctionResultKind
import ui.builder.OperatorOptions
import ui.builder.model.catalog.BuilderCatalog
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

/**
 * Schema fields as the inspector shows them: normalizers, alias and a usage count included.
 *
 * [rules] is what turns "Usages" into a fact rather than a placeholder. It defaults to empty because
 * a caller with no rules parsed — an unparseable buffer — should still get the field list; the count
 * then reads zero for every field, which is what "no rules loaded" means.
 */
internal fun catalogFieldsFrom(
    schema: FieldSchema?,
    rules: List<RuleAst> = emptyList(),
): List<CatalogField> {
    val readers = fieldReaderCounts(rules = rules)
    return schema?.fields?.values?.map { def ->
        CatalogField(
            id = def.id.value,
            type = def.type.name.lowercase(),
            operators = def.operators.map { it.value },
            normalizers = def.normalizers.map { it.value },
            alias = def.alias,
            usages = readers[def.id.value] ?: 0,
        )
    } ?: emptyList()
}

/**
 * How many rules read each field path.
 *
 * `FieldUsage.fieldsOf` returns a set per rule, so flattening and counting gives rules-per-path
 * rather than reads-per-path: a rule naming the same field in three conditions still counts once,
 * which is what "2 rules" has to mean to be worth showing. Counted once for the whole catalog rather
 * than once per field, so the cost is one walk of the rule set instead of one per field.
 */
private fun fieldReaderCounts(rules: List<RuleAst>): Map<String, Int> {
    return rules.flatMap { rule -> FieldUsage.fieldsOf(rule = rule) }
        .groupingBy { path -> path }
        .eachCount()
}

/**
 * Schema fields as the builder's path picker needs them: recursive, with a format hint — and the
 * bare-alias index beside them.
 *
 * The index is taken from the engine rather than re-derived, because *which* aliases may be used bare
 * is a semantic question the engine has already answered. Two rules narrow `aliasPaths` here, both of
 * them the engine's own:
 *
 *  - an alias declared on a field inside a `collection` can never be used on its own — the engine
 *    answers `FieldPathResolution.CrossesCollection` for one, and `AliasTarget.collectionPath` is how
 *    it records that;
 *  - a declared top-level name always wins over an alias that shares its spelling, because
 *    `FieldPathResolver.resolve` tries a direct hit before it consults the index at all.
 *
 * A duplicate alias is not filtered: `FieldSchema.aliasPaths` already keeps the first declaration, and
 * `Validator` reports the collision as a diagnostic of its own.
 */
internal fun builderCatalogFieldsFrom(schema: FieldSchema?): BuilderCatalog {
    if (schema == null) {
        return BuilderCatalog.Empty
    }
    val declared = schema.fields.keys.map { id -> id.value }.toSet()
    return BuilderCatalog(
        fields = schema.fields.values.map { def -> def.toCatalogFieldInfo() },
        aliasPaths = schema.aliasPaths
            .filterValues { target -> target.collectionPath == null }
            .filterKeys { alias -> alias !in declared }
            .mapValues { (_, target) -> target.path.value.split(FIELD_PATH_SEPARATOR) },
    )
}

/** How the engine spells a dotted field path, and how `AliasTarget.path` carries one. */
private const val FIELD_PATH_SEPARATOR: Char = '.'

/**
 * Rule output variables in scope at [uptoRuleId], as extra entries for the builder's operand picker.
 *
 * Scope follows the engine's own rule: a `$name` resolves when an *earlier* rule of the entry assigns
 * it, where "earlier" means manifest file order and then in-file source order — exactly the order
 * [files] arrives in. Passing `null` for [uptoRuleId] returns every variable of the entry.
 *
 * With one exception, mirroring `VariableScopeValidator`: the edited rule's **own `add` clauses** are
 * in scope for its own condition, because that is what lets a rule guard on the list it fills in. Its
 * `set` clauses are not — a rule cannot read a plain value it has not published yet.
 *
 * Both branches count. A `set` or an `add` in an `else` block publishes to the following rules exactly
 * like one in `then`; only one branch runs per record, and which one is a runtime question.
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
            val assignments = rule.assignments + rule.elseAssignments + rule.notExistsAssignments
            if (rule.id == uptoRuleId) {
                assignments.filter { assignment -> assignment.kind == AssignmentKindAst.ADD }
                    .forEach { assignment -> variables.putVariable(assignment = assignment) }
                return variables.values.toList()
            }
            assignments.forEach { assignment -> variables.putVariable(assignment = assignment) }
        }
    }
    return variables.values.toList()
}

private fun MutableMap<String, CatalogFieldInfo>.putVariable(assignment: VariableAssignmentAst) {
    this[assignment.name] = CatalogFieldInfo(
        id = "\$${assignment.name}",
        // An `add` builds a list whatever its value expression is, so the value tells us nothing
        // here — the clause does.
        type = if (assignment.kind == AssignmentKindAst.ADD) {
            OperatorOptions.LIST_VARIABLE_TYPE
        } else {
            inferredVariableType(expr = assignment.expression)
        },
    )
}

/**
 * Best-effort value type of a `set` expression, used only to pick the operator list.
 *
 * A calculation is always numeric, a call is typed by what the engine says it returns, and a literal
 * types itself. A field path or
 * another variable is left as [OperatorOptions.VARIABLE_TYPE]: resolving a path here would duplicate
 * the schema walk the engine already does, and the engine puts no type restriction on a variable
 * anyway, so the wider operator list is the honest answer rather than a guess.
 */
private fun inferredVariableType(expr: ValueExpressionAst): String = when (expr) {
    // Not every call is numeric: `every`/`any` answer true or false and `sumByKey` answers a list,
    // and offering ordering comparisons against either would produce rules that never match.
    is FunctionCallValueAst -> when (DslFunctions.resultKindOf(name = expr.name)) {
        FunctionResultKind.BOOLEAN -> "boolean"
        FunctionResultKind.ARRAY -> OperatorOptions.LIST_VARIABLE_TYPE
        else -> "decimal"
    }

    is ArithmeticValueAst -> "decimal"
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
internal fun catalogActionsFrom(
    actions: ActionSchema?,
    rules: List<RuleAst> = emptyList(),
): List<CatalogActionInfo> {
    val emitters = actionEmitterCounts(rules = rules)
    return actions?.actions?.values?.map { def ->
        CatalogActionInfo(
            name = def.name,
            argType = def.argTypes.joinToString { it.name.lowercase() },
            usages = emitters[def.name] ?: 0,
        )
    } ?: emptyList()
}

/**
 * How many rules emit each action name.
 *
 * All three branches count, and a rule emitting the same action from two of them counts once: only
 * one branch of a rule ever runs, so "2 rules emit this" is a claim about rules, not about clauses.
 */
private fun actionEmitterCounts(rules: List<RuleAst>): Map<String, Int> {
    return rules.flatMap { rule ->
        (rule.actions + rule.elseActions + rule.notExistsActions).map { it.name }.distinct()
    }
        .groupingBy { name -> name }
        .eachCount()
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
