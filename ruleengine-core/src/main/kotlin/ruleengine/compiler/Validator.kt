package ruleengine.compiler

import ruleengine.compiler.operators.OperatorUtils
import ruleengine.core.domain.ActionArgType
import ruleengine.core.domain.ActionSchema
import ruleengine.core.domain.FieldDefinition
import ruleengine.core.domain.FieldId
import ruleengine.core.domain.FieldSchema
import ruleengine.core.domain.FieldType
import ruleengine.core.errors.Severity
import ruleengine.core.errors.ValidationDiagnostic
import ruleengine.dsl.ast.ActionAst
import ruleengine.dsl.ast.AndAst
import ruleengine.dsl.ast.BetweenLiteral
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
import java.math.BigDecimal

data class ValidationResult(val isValid: Boolean, val diagnostics: List<ValidationDiagnostic>)

object Validator {

    fun validate(asts: List<RuleAst>, schema: FieldSchema, actions: ActionSchema? = null): ValidationResult {
        val diagnostics = mutableListOf<ValidationDiagnostic>()
        val ids = mutableSetOf<String>()

        // Check for duplicate aliases in the schema
        val aliasToFieldId = mutableMapOf<String, FieldId>()
        schema.fields.forEach { (fieldId, definition) ->
            definition.alias?.let { alias ->
                val existingFieldId = aliasToFieldId[alias]
                if (existingFieldId != null) {
                    diagnostics += ValidationDiagnostic(
                        severity = Severity.ERROR,
                        message = "Duplicate alias '$alias' found in fields '${existingFieldId.value}' and '${fieldId.value}'"
                    )
                } else {
                    aliasToFieldId[alias] = fieldId
                }
            }
        }

        for (rule in asts) {
            if (!ids.add(rule.id)) {
                diagnostics += ValidationDiagnostic(
                    severity = Severity.ERROR,
                    message = "Duplicate rule id: ${rule.id}"
                )
            }

            validateExpression(expr = rule.condition, schema = schema, diagnostics = diagnostics)

            // Always validate extraction clauses so invalid patterns / unknown fields are caught
            // even when no action schema is supplied.
            for (a in rule.actions) {
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
                    actions = rule.actions,
                    actionSchema = actions,
                    diagnostics = diagnostics
                )
            }
        }

        return ValidationResult(isValid = diagnostics.none { it.severity == Severity.ERROR }, diagnostics = diagnostics)
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
        }
    }

    @Suppress("CyclomaticComplexMethod", "LongMethod")
    private fun validateCondition(
        cond: ConditionAst,
        schema: FieldSchema,
        diagnostics: MutableList<ValidationDiagnostic>
    ) {
        val resolvedFieldId = resolveIdentifier(identifier = cond.field, schema = schema)
        val fieldId = FieldId(value = resolvedFieldId)
        val def = schema.fields[fieldId]
        if (def == null) {
            val suggestion = suggestClosest(input = cond.field, candidates = schema.fields.keys.map { it.value })
            diagnostics += ValidationDiagnostic(
                severity = Severity.ERROR,
                message = "Unknown field '${cond.field}' in condition",
                suggestion = suggestion
            )
            return
        }

        if (def.type == FieldType.BOOLEAN || def.type == FieldType.DATE)
            diagnostics += ValidationDiagnostic(
                severity = Severity.ERROR,
                message = "Field '${cond.field}' uses unsupported field type ${def.type}; " +
                        "BOOLEAN and DATE are not supported yet"
            )
            return

        val op = OperatorUtils.normalizeOperator(op = cond.operator)
        val allowedOperators = allowedOperatorsFor(def = def)
        if (op !in allowedOperators) {
            val allowed = allowedOperators.sorted()
            val suggestion = suggestClosest(input = op, candidates = allowed)
            diagnostics += ValidationDiagnostic(
                severity = Severity.ERROR,
                message = "Operator '$op' is not allowed for field '${cond.field}'. Allowed: $allowed",
                suggestion = suggestion
            )
            return
        }

        when (def.type) {
            FieldType.TEXT -> when (op) {
                "in" -> if (cond.value !is ListLiteral && cond.value !is StringLiteral)
                    diagnostics += ValidationDiagnostic(
                        severity = Severity.ERROR,
                        message = "Field '${cond.field}' with 'in' expects list or string literal"
                    )

                "regex" -> {
                    if (cond.value !is StringLiteral)
                        diagnostics += ValidationDiagnostic(
                            severity = Severity.ERROR,
                            message = "Field '${cond.field}' with 'regex' expects string literal pattern"
                        )
                    else {
                        runCatching {
                            Regex(pattern = cond.value.value)
                        }.onFailure { cause ->
                            diagnostics += ValidationDiagnostic(
                                severity = Severity.ERROR,
                                message = "Invalid regex pattern for field '${cond.field}': ${cause.message}"
                            )
                        }
                    }
                }

                "between" -> diagnostics += ValidationDiagnostic(
                    severity = Severity.ERROR,
                    message = "Operator 'between' is not applicable to text field '${cond.field}'; use a numeric field"
                )

                else -> if (cond.value !is StringLiteral)
                    diagnostics += ValidationDiagnostic(
                        severity = Severity.ERROR,
                        message = "Field '${cond.field}' expects text literal"
                    )
            }

            FieldType.DECIMAL -> when (op) {
                "between" -> validateDecimalBounds(cond = cond, diagnostics = diagnostics)
                else -> validateDecimalLiteral(cond = cond, diagnostics = diagnostics)
            }

            FieldType.INTEGER -> when (op) {
                "between" -> validateIntegerBounds(cond = cond, diagnostics = diagnostics)
                else -> validateIntegerLiteral(cond = cond, diagnostics = diagnostics)
            }

            FieldType.STRING_SET -> if (cond.value !is ListLiteral && cond.value !is StringLiteral)
                diagnostics += ValidationDiagnostic(
                    severity = Severity.ERROR,
                    message = "Field '${cond.field}' expects list or string literal"
                )
            
            else -> diagnostics += ValidationDiagnostic(
                severity = Severity.ERROR,
                message = "Unsupported field type for validation: ${def.type}"
            )
        }
    }

    private fun resolveIdentifier(identifier: String, schema: FieldSchema): String {
        val fieldId = FieldId(value = identifier)
        if (schema.fields.containsKey(fieldId)) {
            return identifier
        }

        for ((id, definition) in schema.fields) {
            if (definition.alias == identifier) {
                return id.value
            }
        }

        return identifier
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
                val fieldId = FieldId(value = extraction.sourceField)
                val def = fieldSchema.fields[fieldId]
                if (def == null) {
                    val suggestion = suggestClosest(
                        input = extraction.sourceField,
                        candidates = fieldSchema.fields.keys.map { it.value }
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

private fun allowedOperatorsFor(def: FieldDefinition): Set<String> {
    return if (def.operators.isNotEmpty()) {
        def.operators.mapTo(mutableSetOf()) { operator ->
            OperatorUtils.normalizeOperator(op = operator.value)
        }
    } else {
        supportedOperatorsFor(type = def.type)
    }
}

private fun supportedOperatorsFor(type: FieldType): Set<String> {
    return when (type) {
        FieldType.TEXT -> setOf("equals", "contains", "startsWith", "endsWith", "in", "regex")
        FieldType.DECIMAL, FieldType.INTEGER -> setOf("equals", "gt", "gte", "lt", "lte", "between")
        FieldType.STRING_SET -> setOf("containsAny", "containsAll")
        else -> emptySet()
    }
}

private fun validateDecimalLiteral(cond: ConditionAst, diagnostics: MutableList<ValidationDiagnostic>) {
    val literal = cond.value as? NumberLiteral ?: run {
        diagnostics += ValidationDiagnostic(
            severity = Severity.ERROR,
            message = "Field '${cond.field}' expects numeric literal"
        )
        return
    }
    runCatching {
        BigDecimal(literal.value)
    }.onFailure {
        diagnostics += ValidationDiagnostic(
            severity = Severity.ERROR,
            message = "Invalid decimal literal: ${literal.value}"
        )
    }
}

private fun validateDecimalBounds(cond: ConditionAst, diagnostics: MutableList<ValidationDiagnostic>) {
    val between = cond.value as? BetweenLiteral ?: run {
        diagnostics += ValidationDiagnostic(
            severity = Severity.ERROR,
            message = "Field '${cond.field}' with 'between' expects two numeric bounds"
        )
        return
    }
    validateDecimalBound(
        value = between.low,
        diagnostics = diagnostics,
        message = "Invalid lower bound: ${between.low}"
    )
    validateDecimalBound(
        value = between.high,
        diagnostics = diagnostics,
        message = "Invalid upper bound: ${between.high}"
    )
}

private fun validateDecimalBound(
    value: String,
    diagnostics: MutableList<ValidationDiagnostic>,
    message: String
) {
    runCatching {
        BigDecimal(value)
    }.onFailure {
        diagnostics += ValidationDiagnostic(severity = Severity.ERROR, message = message)
    }
}

private fun validateIntegerLiteral(cond: ConditionAst, diagnostics: MutableList<ValidationDiagnostic>) {
    val literal = cond.value as? NumberLiteral ?: run {
        diagnostics += ValidationDiagnostic(
            severity = Severity.ERROR,
            message = "Field '${cond.field}' expects integer literal"
        )
        return
    }
    runCatching {
        literal.value.toLong()
    }.onFailure {
        diagnostics += ValidationDiagnostic(
            severity = Severity.ERROR,
            message = "Invalid integer literal: ${literal.value}"
        )
    }
}

private fun validateIntegerBounds(cond: ConditionAst, diagnostics: MutableList<ValidationDiagnostic>) {
    val between = cond.value as? BetweenLiteral ?: run {
        diagnostics += ValidationDiagnostic(
            severity = Severity.ERROR,
            message = "Field '${cond.field}' with 'between' expects two integer bounds"
        )
        return
    }
    validateIntegerBound(
        value = between.low,
        diagnostics = diagnostics,
        message = "Invalid lower bound: ${between.low}"
    )
    validateIntegerBound(
        value = between.high,
        diagnostics = diagnostics,
        message = "Invalid upper bound: ${between.high}"
    )
}

private fun validateIntegerBound(
    value: String,
    diagnostics: MutableList<ValidationDiagnostic>,
    message: String
) {
    runCatching {
        value.toLong()
    }.onFailure {
        diagnostics += ValidationDiagnostic(severity = Severity.ERROR, message = message)
    }
}

private fun suggestClosest(input: String, candidates: List<String>, maxDistance: Int = 3): String? {
    var best: String? = null
    var bestDist = Int.MAX_VALUE
    for (c in candidates) {
        val d = levenshtein(a = input.lowercase(), b = c.lowercase())
        if (d < bestDist) {
            bestDist = d
            best = c
        }
    }
    return if (bestDist <= maxDistance) best else null
}

private fun levenshtein(a: String, b: String): Int {
    if (a == b) return 0
    if (a.isEmpty()) return b.length
    if (b.isEmpty()) return a.length
    val aLen = a.length
    val bLen = b.length
    val dp = Array(aLen + 1) { IntArray(bLen + 1) }
    for (i in 0..aLen) dp[i][0] = i
    for (j in 0..bLen) dp[0][j] = j
    for (i in 1..aLen) {
        for (j in 1..bLen) {
            val cost = if (a[i - 1] == b[j - 1]) 0 else 1
            dp[i][j] = minOf(
                dp[i - 1][j] + 1,
                dp[i][j - 1] + 1,
                dp[i - 1][j - 1] + cost
            )
        }
    }
    return dp[aLen][bLen]
}
