package ruleengine.compiler

import ruleengine.dsl.ast.*
import ruleengine.core.domain.FieldSchema
import ruleengine.core.domain.FieldId
import ruleengine.core.errors.ValidationDiagnostic
import ruleengine.core.errors.Severity
import ruleengine.core.domain.FieldType
import ruleengine.core.domain.ActionSchema

data class ValidationResult(val isValid: Boolean, val diagnostics: List<ValidationDiagnostic>)

object Validator {
    private fun normalizeOperator(op: String): String = when (op.lowercase()) {
        "==", "equals" -> "equals"
        "=", "eq" -> "equals"
        ">=", "gte" -> "gte"
        ">" , "gt" -> "gt"
        "<=", "lte" -> "lte"
        "<", "lt" -> "lt"
        "contains" -> "contains"
        "startswith" -> "startsWith"
        "endswith" -> "endsWith"
        "in" -> "in"
        "containsany" -> "containsAny"
        "containsall" -> "containsAll"
        "regex", "matches", "regexp" -> "regex"
        "between" -> "between"
        else -> op
    }

    fun validate(asts: List<RuleAst>, schema: FieldSchema, actions: ActionSchema? = null): ValidationResult {
        val diagnostics = mutableListOf<ValidationDiagnostic>()
        val ids = mutableSetOf<String>()

        for (rule in asts) {
            if (!ids.add(rule.id)) {
                diagnostics += ValidationDiagnostic(severity = Severity.ERROR, message = "Duplicate rule id: ${rule.id}")
            }

            validateExpression(rule.condition, schema, diagnostics)
            if (actions != null) validateActions(rule.actions, actions, diagnostics)
        }

        return ValidationResult(isValid = diagnostics.none { it.severity == Severity.ERROR }, diagnostics = diagnostics)
    }

    private fun validateExpression(expr: ExpressionAst, schema: FieldSchema, diagnostics: MutableList<ValidationDiagnostic>) {
        when (expr) {
            is AndAst -> expr.children.forEach { validateExpression(it, schema, diagnostics) }
            is OrAst -> expr.children.forEach { validateExpression(it, schema, diagnostics) }
            is NotAst -> validateExpression(expr.child, schema, diagnostics)
            is ConditionAst -> validateCondition(expr, schema, diagnostics)
            else -> diagnostics += ValidationDiagnostic(severity = Severity.ERROR, message = "Unknown expression type: ${expr.javaClass}")
        }
    }

    private fun validateCondition(cond: ConditionAst, schema: FieldSchema, diagnostics: MutableList<ValidationDiagnostic>) {
        val fieldId = FieldId(cond.field)
        val def = schema.fields[fieldId]
        if (def == null) {
            val suggestion = suggestClosest(cond.field, schema.fields.keys.map { it.value })
            diagnostics += ValidationDiagnostic(severity = Severity.ERROR, message = "Unknown field '${cond.field}' in condition", suggestion = suggestion)
            return
        }

        val op = normalizeOperator(cond.operator)
        if (def.operators.isNotEmpty() && def.operators.none { it.value.equals(op, ignoreCase = true) }) {
            val allowed = def.operators.map { it.value }
            val suggestion = suggestClosest(op, allowed)
            diagnostics += ValidationDiagnostic(severity = Severity.ERROR, message = "Operator '$op' is not allowed for field '${cond.field}'. Allowed: $allowed", suggestion = suggestion)
        }

        // type check literal
        when (def.type) {
            FieldType.TEXT -> when (op) {
                "in" -> if (cond.value !is ListLiteral && cond.value !is StringLiteral)
                    diagnostics += ValidationDiagnostic(severity = Severity.ERROR, message = "Field '${cond.field}' with 'in' expects list or string literal")
                "regex" -> {
                    if (cond.value !is StringLiteral)
                        diagnostics += ValidationDiagnostic(severity = Severity.ERROR, message = "Field '${cond.field}' with 'regex' expects string literal pattern")
                    else {
                        try { Regex((cond.value as StringLiteral).value) }
                        catch (ex: Exception) { diagnostics += ValidationDiagnostic(severity = Severity.ERROR, message = "Invalid regex pattern for field '${cond.field}': ${ex.message}") }
                    }
                }
                "between" -> diagnostics += ValidationDiagnostic(severity = Severity.ERROR, message = "Operator 'between' is not applicable to text field '${cond.field}'; use a numeric field")
                else -> if (cond.value !is StringLiteral)
                    diagnostics += ValidationDiagnostic(severity = Severity.ERROR, message = "Field '${cond.field}' expects text literal")
            }
            FieldType.DECIMAL -> when (op) {
                "between" -> if (cond.value !is BetweenLiteral)
                    diagnostics += ValidationDiagnostic(severity = Severity.ERROR, message = "Field '${cond.field}' with 'between' expects two numeric bounds")
                else -> if (cond.value !is NumberLiteral)
                    diagnostics += ValidationDiagnostic(severity = Severity.ERROR, message = "Field '${cond.field}' expects numeric literal")
            }
            FieldType.INTEGER -> when (op) {
                "between" -> if (cond.value !is BetweenLiteral)
                    diagnostics += ValidationDiagnostic(severity = Severity.ERROR, message = "Field '${cond.field}' with 'between' expects two integer bounds")
                else -> if (cond.value !is NumberLiteral)
                    diagnostics += ValidationDiagnostic(severity = Severity.ERROR, message = "Field '${cond.field}' expects integer literal")
            }
            FieldType.STRING_SET -> if (cond.value !is ListLiteral && cond.value !is StringLiteral)
                diagnostics += ValidationDiagnostic(severity = Severity.ERROR, message = "Field '${cond.field}' expects list or string literal")
            else -> {}
        }
    }

    private fun validateActions(actions: List<ActionAst>, schema: ActionSchema, diagnostics: MutableList<ValidationDiagnostic>) {
        for (a in actions) {
            val def = schema.actions[a.name]
            if (def == null) {
                diagnostics += ValidationDiagnostic(severity = Severity.ERROR, message = "Unknown action '${a.name}'")
                continue
            }
            if (def.argTypes.size != a.arguments.size) {
                diagnostics += ValidationDiagnostic(severity = Severity.ERROR, message = "Action '${a.name}' expects ${def.argTypes.size} arguments but got ${a.arguments.size}")
                continue
            }
            for ((idx, expectedType) in def.argTypes.withIndex()) {
                val lit = a.arguments.getOrNull(idx)
                val ok = when (expectedType) {
                    ruleengine.core.domain.ActionArgType.STRING -> lit is StringLiteral
                    ruleengine.core.domain.ActionArgType.INTEGER -> lit is NumberLiteral
                    ruleengine.core.domain.ActionArgType.DECIMAL -> lit is NumberLiteral
                }
                if (!ok) diagnostics += ValidationDiagnostic(severity = Severity.ERROR, message = "Action '${a.name}' argument ${idx} expects ${expectedType}")
            }
        }
    }

    private fun suggestClosest(input: String, candidates: List<String>, maxDistance: Int = 3): String? {
        var best: String? = null
        var bestDist = Int.MAX_VALUE
        for (c in candidates) {
            val d = levenshtein(input.lowercase(), c.lowercase())
            if (d < bestDist) { bestDist = d; best = c }
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

}

