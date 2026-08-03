package ruleengine.compiler

import ruleengine.compiler.operators.DateOperator
import ruleengine.compiler.operators.OperatorUtils
import ruleengine.compiler.support.FieldPathMessages
import ruleengine.compiler.support.LiteralValidation
import ruleengine.compiler.support.OperatorSupport
import ruleengine.compiler.support.Suggestions
import ruleengine.compiler.support.VariableScopeValidator
import ruleengine.compiler.value.ValueExpressionValidator
import ruleengine.core.domain.FieldPathResolution
import ruleengine.core.domain.FieldPathResolver
import ruleengine.core.domain.OperatorNames
import ruleengine.core.domain.dto.action.ActionArgType
import ruleengine.core.domain.dto.action.ActionSchema
import ruleengine.core.domain.dto.field.FieldDefinition
import ruleengine.core.domain.dto.field.FieldId
import ruleengine.core.domain.dto.field.FieldSchema
import ruleengine.core.domain.dto.field.FieldType
import ruleengine.core.errors.Severity
import ruleengine.core.errors.ValidationDiagnostic
import ruleengine.dsl.ast.ActionAst
import ruleengine.dsl.ast.AndAst
import ruleengine.dsl.ast.BetweenLiteral
import ruleengine.dsl.ast.BooleanLiteral
import ruleengine.dsl.ast.ComparisonExpressionAst
import ruleengine.dsl.ast.ConditionAst
import ruleengine.dsl.ast.ExpressionAst
import ruleengine.dsl.ast.ExtractionAst
import ruleengine.dsl.ast.ExtractionRefLiteral
import ruleengine.dsl.ast.ListLiteral
import ruleengine.dsl.ast.NotAst
import ruleengine.dsl.ast.NumberLiteral
import ruleengine.dsl.ast.OrAst
import ruleengine.dsl.ast.RuleAst
import ruleengine.dsl.ast.StringLiteral
import ruleengine.dsl.ast.VariableAssignmentAst
import ruleengine.dsl.ast.VariableRefLiteral

object Validator {

    /** Keyword an action may not be named, mapped to the rename suggested in the diagnostic. */
    private val RESERVED_ACTION_NAMES = mapOf(
        "else" to "otherwise",
        "stop" to "halt",
        "add" to "append",
    )

    fun validate(asts: List<RuleAst>, schema: FieldSchema, actions: ActionSchema? = null): ValidationResult {
        val diagnostics = mutableListOf<ValidationDiagnostic>()
        val ids = mutableSetOf<String>()

        validateAliasUniqueness(schema = schema, diagnostics = diagnostics)
        validateActionNamesAreNotKeywords(actionSchema = actions, diagnostics = diagnostics)

        for (rule in asts) {
            if (!ids.add(rule.id)) {
                diagnostics += ValidationDiagnostic(
                    severity = Severity.ERROR,
                    message = "Duplicate rule id: ${rule.id}",
                    line = rule.line,
                    column = rule.column,
                )
            }
            validateRule(rule = rule, schema = schema, actions = actions, diagnostics = diagnostics)
        }

        // Runs over the whole entry rather than per rule: "an earlier rule assigns it" only has a
        // meaning once every rule of the entry is known, in evaluation order.
        VariableScopeValidator.validate(asts = asts, schema = schema, diagnostics = diagnostics)

        return ValidationResult(isValid = diagnostics.none { it.severity == Severity.ERROR }, diagnostics = diagnostics)
    }

    /**
     * An action may not be named `else`, `stop` or `add`, which the parser reads as rule structure.
     *
     * Checked on the schema rather than on each use, so the report names the declaration that has to
     * change instead of every rule that writes it.
     *
     * `else` and `stop` were free to reserve: the other structural words had been unusable as action
     * names since before they were introduced, so no rule set using one could exist to be broken.
     * `add` is not in that position — it became a keyword after actions could already be called
     * anything, so a rule set with an `add` action does exist and this check is what tells its author
     * to rename it rather than leaving `add "x"` to fail as a malformed accumulator clause.
     */
    private fun validateActionNamesAreNotKeywords(
        actionSchema: ActionSchema?,
        diagnostics: MutableList<ValidationDiagnostic>
    ) {
        if (actionSchema == null) {
            return
        }
        for ((keyword, alternative) in RESERVED_ACTION_NAMES) {
            if (keyword !in actionSchema.actions) {
                continue
            }
            diagnostics += ValidationDiagnostic(
                severity = Severity.ERROR,
                message = "Action '$keyword' is declared in the action schema, " +
                        "but '$keyword' is a rule keyword",
                suggestion = "Rename the action, for example to '$alternative'",
            )
        }
    }

    /** Two fields sharing an alias make every rule that uses it ambiguous, at any nesting depth. */
    private fun validateAliasUniqueness(schema: FieldSchema, diagnostics: MutableList<ValidationDiagnostic>) {
        val declared = FieldPathResolver.scalarPaths(schema = schema).keys.map { it.value }.toSet() +
            schema.fields.keys.map { it.value }
        val seen = mutableMapOf<String, FieldId>()
        for (target in schema.aliasTargets) {
            val existing = seen[target.alias]
            if (existing != null) {
                diagnostics += ValidationDiagnostic(
                    severity = Severity.ERROR,
                    message = "Duplicate alias '${target.alias}' found in fields " +
                            "'${existing.value}' and '${target.path.value}'",
                    suggestion = "Give each field its own alias, or drop one of them",
                )
                continue
            }
            seen[target.alias] = target.path
            if (target.alias in declared) {
                diagnostics += ValidationDiagnostic(
                    severity = Severity.WARNING,
                    message = "Alias '${target.alias}' on field '${target.path.value}' is also a declared " +
                            "field name; the declared field wins, so the alias can never be used on its own",
                    suggestion = "Rename the alias to something no field declares",
                )
            }
        }
    }

    /** Everything checkable about one rule on its own; cross-rule checks run over the whole list. */
    private fun validateRule(
        rule: RuleAst,
        schema: FieldSchema,
        actions: ActionSchema?,
        diagnostics: MutableList<ValidationDiagnostic>,
    ) {
        // A missing description never blocks execution — it only degrades the exported rule
        // overview, where the id and the raw condition would be all a reader gets.
        if (rule.description.isNullOrBlank()) {
            diagnostics += ValidationDiagnostic(
                severity = Severity.WARNING,
                message = "Rule '${rule.id}' has no description",
                suggestion = "Add a description \"...\" clause so the rule can be explained " +
                        "in an exported overview",
                line = rule.line,
                column = rule.column,
            )
        }

        validateExpression(expr = rule.condition, schema = schema, diagnostics = diagnostics)

        // Both branches carry the same kinds of clause, so both go through the same checks.
        validateBranch(
            assignments = rule.assignments,
            branchActions = rule.actions,
            schema = schema,
            actions = actions,
            diagnostics = diagnostics
        )
        validateBranch(
            assignments = rule.elseAssignments,
            branchActions = rule.elseActions,
            schema = schema,
            actions = actions,
            diagnostics = diagnostics
        )
    }

    /** The `set` clauses and actions of one branch — a rule's `then` block or its `else` block. */
    private fun validateBranch(
        assignments: List<VariableAssignmentAst>,
        branchActions: List<ActionAst>,
        schema: FieldSchema,
        actions: ActionSchema?,
        diagnostics: MutableList<ValidationDiagnostic>,
    ) {
        for (assignment in assignments) {
            ValueExpressionValidator.validateValue(
                expr = assignment.expression,
                schema = schema,
                diagnostics = diagnostics
            )
        }

        // Always validate extraction clauses so invalid patterns / unknown fields are caught
        // even when no action schema is supplied.
        for (a in branchActions) {
            if (a.extraction != null) {
                validateExtraction(
                    extraction = a.extraction,
                    fieldSchema = schema,
                    diagnostics = diagnostics
                )
            }
        }

        if (actions != null) {
            validateActions(
                actions = branchActions,
                actionSchema = actions,
                diagnostics = diagnostics
            )
        }
    }

    private fun validateExpression(
        expr: ExpressionAst,
        schema: FieldSchema,
        diagnostics: MutableList<ValidationDiagnostic>
    ) {
        when (expr) {
            is AndAst -> expr.children.forEach {
                validateExpression(expr = it, schema = schema, diagnostics = diagnostics)
            }

            is OrAst -> expr.children.forEach {
                validateExpression(expr = it, schema = schema, diagnostics = diagnostics)
            }

            is NotAst -> validateExpression(expr = expr.child, schema = schema, diagnostics = diagnostics)
            is ConditionAst -> validateCondition(cond = expr, schema = schema, diagnostics = diagnostics)

            is ComparisonExpressionAst -> ValueExpressionValidator.validate(
                expr = expr,
                schema = schema,
                diagnostics = diagnostics
            )
        }
    }

    @Suppress("CyclomaticComplexMethod", "LongMethod")
    private fun validateCondition(
        cond: ConditionAst,
        schema: FieldSchema,
        diagnostics: MutableList<ValidationDiagnostic>
    ) {
        val def = when (val resolution = FieldPathResolver.resolve(identifier = cond.field, schema = schema)) {
            is FieldPathResolution.Resolved -> resolution.definition

            is FieldPathResolution.CrossesCollection -> {
                diagnostics += ValidationDiagnostic(
                    severity = Severity.ERROR,
                    message = FieldPathMessages.crossesCollection(
                        field = cond.field,
                        collectionPath = resolution.collectionPath
                    ),
                    line = cond.line,
                    column = cond.column,
                )
                return
            }

            is FieldPathResolution.Unknown -> {
                diagnostics += ValidationDiagnostic(
                    severity = Severity.ERROR,
                    message = "Unknown field '${cond.field}' in condition",
                    suggestion = Suggestions.suggestClosest(
                        input = cond.field,
                        candidates = fieldCandidates(schema = schema)
                    ),
                    line = cond.line,
                    column = cond.column,
                )
                return
            }
        }

        if (def.type == FieldType.COLLECTION || def.type == FieldType.OBJECT) {
            diagnostics += ValidationDiagnostic(
                severity = Severity.ERROR,
                message = "Field '${cond.field}' is a ${def.type.name.lowercase()} and cannot be compared " +
                        "directly; navigate into it (e.g. '${cond.field}.someField') or use an aggregate " +
                        "function such as count(${cond.field})",
                line = cond.line,
                column = cond.column,
            )
            return
        }

        val op = OperatorUtils.normalizeOperator(op = cond.operator)
        val allowedOperators = OperatorSupport.allowedOperatorsFor(def = def)
        if (op !in allowedOperators) {
            val allowed = allowedOperators.sorted()
            val suggestion = Suggestions.suggestClosest(input = op, candidates = allowed)
            diagnostics += ValidationDiagnostic(
                severity = Severity.ERROR,
                message = "Operator '$op' is not allowed for field '${cond.field}'. Allowed: $allowed",
                suggestion = suggestion,
                line = cond.line,
                column = cond.column,
            )
            return
        }

        when (def.type) {
            FieldType.TEXT -> when (op) {
                OperatorNames.IN -> if (cond.value !is ListLiteral && cond.value !is StringLiteral)
                    diagnostics += ValidationDiagnostic(
                        severity = Severity.ERROR,
                        message = "Field '${cond.field}' with 'in' expects list or string literal",
                        line = cond.line,
                        column = cond.column,
                    )

                OperatorNames.REGEX -> {
                    if (cond.value !is StringLiteral)
                        diagnostics += ValidationDiagnostic(
                            severity = Severity.ERROR,
                            message = "Field '${cond.field}' with 'regex' expects string literal pattern",
                            line = cond.line,
                            column = cond.column,
                        )
                    else {
                        runCatching {
                            Regex(pattern = cond.value.value)
                        }.onFailure { cause ->
                            diagnostics += ValidationDiagnostic(
                                severity = Severity.ERROR,
                                message = "Invalid regex pattern for field '${cond.field}': ${cause.message}",
                                line = cond.line,
                                column = cond.column,
                            )
                        }
                    }
                }

                OperatorNames.BETWEEN -> diagnostics += ValidationDiagnostic(
                    severity = Severity.ERROR,
                    message = "Operator 'between' is not applicable to text field '${cond.field}'; use a numeric field",
                    line = cond.line,
                    column = cond.column,
                )

                else -> if (cond.value !is StringLiteral)
                    diagnostics += ValidationDiagnostic(
                        severity = Severity.ERROR,
                        message = "Field '${cond.field}' expects text literal",
                        line = cond.line,
                        column = cond.column,
                    )
            }

            FieldType.DECIMAL -> when (op) {
                OperatorNames.BETWEEN -> LiteralValidation.validateDecimalBounds(cond = cond, diagnostics = diagnostics)
                else -> LiteralValidation.validateDecimalLiteral(cond = cond, diagnostics = diagnostics)
            }

            FieldType.INTEGER -> when (op) {
                OperatorNames.BETWEEN -> LiteralValidation.validateIntegerBounds(cond = cond, diagnostics = diagnostics)
                else -> LiteralValidation.validateIntegerLiteral(cond = cond, diagnostics = diagnostics)
            }

            FieldType.STRING_SET -> if (cond.value !is ListLiteral && cond.value !is StringLiteral)
                diagnostics += ValidationDiagnostic(
                    severity = Severity.ERROR,
                    message = "Field '${cond.field}' expects list or string literal",
                    line = cond.line,
                    column = cond.column,
                )

            FieldType.BOOLEAN -> if (cond.value !is BooleanLiteral)
                diagnostics += ValidationDiagnostic(
                    severity = Severity.ERROR,
                    message = "Field '${cond.field}' expects 'true' or 'false'",
                    line = cond.line,
                    column = cond.column,
                )

            FieldType.DATE, FieldType.DATE_TIME ->
                validateDateLiteral(cond = cond, def = def, op = op, diagnostics = diagnostics)

            // Structure types carry no literal of their own — a condition addresses one of their
            // members, which is resolved and validated as that member's scalar type before we get
            // here. Spelled out rather than left to fall through so that adding a FieldType makes
            // the compiler ask what its literal rule is.
            FieldType.COLLECTION, FieldType.OBJECT -> Unit
        }
    }

    /**
     * Date literals are quoted: ISO-8601 by default, or the field's declared `format` when it has one.
     * `between` needs two of them.
     */
    private fun validateDateLiteral(
        cond: ConditionAst,
        def: FieldDefinition,
        op: String,
        diagnostics: MutableList<ValidationDiagnostic>
    ) {
        val expected = DateOperator.expectedFormatText(def = def)
        if (op == OperatorNames.BETWEEN) {
            val between = cond.value as? BetweenLiteral ?: run {
                diagnostics += ValidationDiagnostic(
                    severity = Severity.ERROR,
                    message = "Field '${cond.field}' with 'between' expects two quoted values in $expected",
                    line = cond.line,
                    column = cond.column,
                )
                return
            }
            listOf(between.low, between.high)
                .filterNot { DateOperator.isValidLiteral(text = it, def = def) }
                .forEach { invalid ->
                    diagnostics += ValidationDiagnostic(
                        severity = Severity.ERROR,
                        message = "Invalid date bound '$invalid' for field '${cond.field}'; " +
                                "expected $expected",
                        line = cond.line,
                        column = cond.column,
                    )
                }
            return
        }

        val literal = cond.value as? StringLiteral ?: run {
            diagnostics += ValidationDiagnostic(
                severity = Severity.ERROR,
                message = "Field '${cond.field}' expects a quoted date literal in $expected",
                line = cond.line,
                column = cond.column,
            )
            return
        }
        if (!DateOperator.isValidLiteral(text = literal.value, def = def)) {
            diagnostics += ValidationDiagnostic(
                severity = Severity.ERROR,
                message = "Invalid date '${literal.value}' for field '${cond.field}'; " +
                        "expected $expected",
                line = cond.line,
                column = cond.column,
            )
        }
    }

    /**
     * Names a rule may use for a field: every declared path that resolves to a scalar, plus the aliases.
     *
     * Nested paths are included so a typo deep in a path (`shipment.customer.tir`) can still be pointed at
     * the field the author meant.
     */
    private fun fieldCandidates(schema: FieldSchema): List<String> {
        val scalarPaths = FieldPathResolver.scalarPaths(schema = schema)
        return buildList {
            addAll(scalarPaths.keys.map { it.value })
            addAll(schema.fields.keys.map { it.value })
            for (target in schema.aliasTargets) {
                // Both spellings an author may write: the alias on its own, and the alias in the position
                // of the segment it renames.
                add(target.alias)
                add(aliasPath(path = target.path.value, alias = target.alias))
            }
        }
    }

    /** The same path with its last segment replaced by [alias]. */
    private fun aliasPath(path: String, alias: String): String {
        val prefix = path.substringBeforeLast(delimiter = '.', missingDelimiterValue = "")
        if (prefix.isEmpty()) {
            return alias
        }
        return "$prefix.$alias"
    }

    @Suppress("LoopWithTooManyJumpStatements", "CyclomaticComplexMethod", "NestedBlockDepth")
    private fun validateActions(
        actions: List<ActionAst>,
        actionSchema: ActionSchema,
        diagnostics: MutableList<ValidationDiagnostic>
    ) {
        for (a in actions) {
            // Extraction validity is checked independently in validate(); skip it here.
            val def = actionSchema.actions[a.name]
            if (def == null) {
                diagnostics += ValidationDiagnostic(severity = Severity.ERROR, message = "Unknown action '${a.name}'")
                continue
            }
            if (def.argTypes.size != a.arguments.size) {
                diagnostics += ValidationDiagnostic(
                    severity = Severity.ERROR,
                    message = "Action '${a.name}' expects ${def.argTypes.size} arguments but got ${a.arguments.size}"
                )
                continue
            }
            for ((idx, expectedType) in def.argTypes.withIndex()) {
                val lit = a.arguments.getOrNull(index = idx)
                // A variable carries whatever the assigning rule produced, so its type is only known
                // at evaluation time; that it exists at all is checked by VariableScopeValidator.
                if (lit is VariableRefLiteral) {
                    continue
                }
                // ExtractionRefLiteral resolves to a String at evaluation time
                if (lit is ExtractionRefLiteral) {
                    if (a.extraction == null) {
                        diagnostics += ValidationDiagnostic(
                            severity = Severity.ERROR,
                            message = "Action '${a.name}' argument $idx uses " +
                                    "extraction reference but no 'extract' clause is present"
                        )
                    } else if (expectedType != ActionArgType.STRING) {
                        diagnostics += ValidationDiagnostic(
                            severity = Severity.ERROR,
                            message = "Action '${a.name}' argument $idx expects $expectedType " +
                                    "but extraction always produces a string"
                        )
                    }
                    continue
                }
                val ok = when (expectedType) {
                    ActionArgType.STRING -> lit is StringLiteral
                    ActionArgType.INTEGER -> lit is NumberLiteral
                    ActionArgType.DECIMAL -> lit is NumberLiteral
                }
                if (!ok) diagnostics += ValidationDiagnostic(
                    severity = Severity.ERROR,
                    message = "Action '${a.name}' argument $idx expects $expectedType"
                )
            }
        }
    }

    private fun validateExtraction(
        extraction: ExtractionAst,
        fieldSchema: FieldSchema,
        diagnostics: MutableList<ValidationDiagnostic>
    ) {
        when (extraction) {
            is ExtractionAst.RegexExtraction -> {
                val resolution = FieldPathResolver.resolve(
                    identifier = extraction.sourceField,
                    schema = fieldSchema
                )
                val def = (resolution as? FieldPathResolution.Resolved)?.definition
                if (def == null) {
                    val suggestion = Suggestions.suggestClosest(
                        input = extraction.sourceField,
                        candidates = fieldCandidates(schema = fieldSchema)
                    )
                    diagnostics += ValidationDiagnostic(
                        severity = Severity.ERROR,
                        message = "Extraction references unknown field '${extraction.sourceField}'",
                        suggestion = suggestion
                    )
                } else if (def.type != FieldType.TEXT) {
                    diagnostics += ValidationDiagnostic(
                        severity = Severity.ERROR,
                        message = "Extraction source field '${extraction.sourceField}' must be of type TEXT " +
                                "but is ${def.type}"
                    )
                }
                runCatching {
                    Regex(pattern = extraction.pattern)
                }.onFailure { cause ->
                    diagnostics += ValidationDiagnostic(
                        severity = Severity.ERROR,
                        message = "Invalid regex pattern '${extraction.pattern}' in extraction: ${cause.message}"
                    )
                }
                if (extraction.groupIndex < 0) {
                    diagnostics += ValidationDiagnostic(
                        severity = Severity.ERROR,
                        message = "Extraction group index must be >= 0 but was ${extraction.groupIndex}"
                    )
                }
            }
        }
    }
}
