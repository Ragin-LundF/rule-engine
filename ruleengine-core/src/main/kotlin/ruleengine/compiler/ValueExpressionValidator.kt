package ruleengine.compiler

import ruleengine.core.domain.FieldId
import ruleengine.core.domain.FieldSchema
import ruleengine.core.domain.FieldType
import ruleengine.core.errors.Severity
import ruleengine.core.errors.ValidationDiagnostic
import ruleengine.dsl.ast.ArithmeticValueAst
import ruleengine.dsl.ast.ComparisonExpressionAst
import ruleengine.dsl.ast.ComparisonOperatorAst
import ruleengine.dsl.ast.ExpressionAst
import ruleengine.dsl.ast.FieldAccessAst
import ruleengine.dsl.ast.FieldSegmentAst
import ruleengine.dsl.ast.FilterSegmentAst
import ruleengine.dsl.ast.FunctionCallValueAst
import ruleengine.dsl.ast.LiteralValueAst
import ruleengine.dsl.ast.NumberLiteral
import ruleengine.dsl.ast.StringLiteral
import ruleengine.dsl.ast.ValueExpressionAst
import ruleengine.evaluator.compiled.AggregateFunctionName

internal object ValueExpressionValidator {

    private enum class ValueKind { NUMERIC, TEXT, UNKNOWN }

    fun validate(
        expr: ComparisonExpressionAst,
        schema: FieldSchema,
        diagnostics: MutableList<ValidationDiagnostic>
    ) {
        val leftKind = validateValueExpression(expr = expr.left, schema = schema, diagnostics = diagnostics)
        val rightKind = validateValueExpression(expr = expr.right, schema = schema, diagnostics = diagnostics)

        if (leftKind == ValueKind.UNKNOWN || rightKind == ValueKind.UNKNOWN) {
            return
        }

        if (leftKind != rightKind) {
            diagnostics += ValidationDiagnostic(
                severity = Severity.ERROR,
                message = "Comparison operands have incompatible types: left is $leftKind, right is $rightKind"
            )
            return
        }

        if (leftKind == ValueKind.TEXT && expr.operator !in setOf(
                ComparisonOperatorAst.EQ,
                ComparisonOperatorAst.NEQ
            )
        ) {
            diagnostics += ValidationDiagnostic(
                severity = Severity.ERROR,
                message = "Operator '${expr.operator}' is not allowed for text comparisons; use == or !="
            )
        }
    }

    private fun validateValueExpression(
        expr: ValueExpressionAst,
        schema: FieldSchema,
        diagnostics: MutableList<ValidationDiagnostic>
    ): ValueKind {
        return when (expr) {
            is LiteralValueAst -> when (expr.literal) {
                is NumberLiteral -> ValueKind.NUMERIC
                is StringLiteral -> ValueKind.TEXT
                else -> ValueKind.UNKNOWN
            }
            is FieldAccessAst -> validateFieldAccess(expr = expr, schema = schema, diagnostics = diagnostics)
            is ArithmeticValueAst -> validateArithmetic(expr = expr, schema = schema, diagnostics = diagnostics)
            is FunctionCallValueAst -> validateFunctionCall(expr = expr, schema = schema, diagnostics = diagnostics)
        }
    }

    private fun validateFieldAccess(
        expr: FieldAccessAst,
        schema: FieldSchema,
        diagnostics: MutableList<ValidationDiagnostic>
    ): ValueKind {
        for (segment in expr.path) {
            if (segment is FilterSegmentAst) {
                validateFilterExpression(expr = segment.expression, schema = schema, diagnostics = diagnostics)
            }
        }
        if (expr.path.any { it is FilterSegmentAst } || expr.path.size > 1) {
            // multi-segment or filtered path — treated as numeric array projection
            return ValueKind.NUMERIC
        }
        if (expr.path[0] !is FieldSegmentAst) {
            return ValueKind.UNKNOWN
        }
        val name = (expr.path[0] as FieldSegmentAst).name
        val resolvedId = resolveIdentifier(identifier = name, schema = schema)
        val fieldId = FieldId(value = resolvedId)
        val def = schema.fields[fieldId]
        if (def == null) {
            diagnostics += ValidationDiagnostic(
                severity = Severity.ERROR,
                message = "Unknown field '$name' in expression"
            )
            return ValueKind.UNKNOWN
        }
        return when (def.type) {
            FieldType.INTEGER, FieldType.DECIMAL -> ValueKind.NUMERIC
            FieldType.TEXT -> ValueKind.TEXT
            // array-like types are valid as aggregate function arguments
            else -> ValueKind.NUMERIC
        }
    }

    private fun validateFilterExpression(
        expr: ExpressionAst,
        schema: FieldSchema,
        diagnostics: MutableList<ValidationDiagnostic>
    ) {
        when (expr) {
            is ComparisonExpressionAst -> validate(expr = expr, schema = schema, diagnostics = diagnostics)
            // ConditionAst (legacy) and other boolean expressions are valid filter expressions
            else -> Unit
        }
    }

    private fun validateArithmetic(
        expr: ArithmeticValueAst,
        schema: FieldSchema,
        diagnostics: MutableList<ValidationDiagnostic>
    ): ValueKind {
        val leftKind = validateValueExpression(expr = expr.left, schema = schema, diagnostics = diagnostics)
        val rightKind = validateValueExpression(expr = expr.right, schema = schema, diagnostics = diagnostics)
        if (leftKind == ValueKind.TEXT || rightKind == ValueKind.TEXT) {
            diagnostics += ValidationDiagnostic(
                severity = Severity.ERROR,
                message = "Arithmetic operator '${expr.operator}' requires numeric operands, but got a text value"
            )
            return ValueKind.UNKNOWN
        }
        return ValueKind.NUMERIC
    }

    private fun validateFunctionCall(
        expr: FunctionCallValueAst,
        schema: FieldSchema,
        diagnostics: MutableList<ValidationDiagnostic>
    ): ValueKind {
        val knownNames = AggregateFunctionName.entries.map { it.name.lowercase() }
        if (!knownNames.contains(expr.name.lowercase())) {
            diagnostics += ValidationDiagnostic(
                severity = Severity.ERROR,
                message = "Unknown function '${expr.name}'; supported functions are: ${knownNames.joinToString()}"
            )
            return ValueKind.UNKNOWN
        }
        if (expr.arguments.size != 1) {
            diagnostics += ValidationDiagnostic(
                severity = Severity.ERROR,
                message = "Function '${expr.name}' requires exactly one argument, but got ${expr.arguments.size}"
            )
            return ValueKind.UNKNOWN
        }
        val argKind = validateValueExpression(expr = expr.arguments[0], schema = schema, diagnostics = diagnostics)
        val functionName = AggregateFunctionName.entries.first { it.name.equals(expr.name, ignoreCase = true) }
        if (functionName == AggregateFunctionName.COUNT) {
            if (argKind == ValueKind.TEXT) {
                diagnostics += ValidationDiagnostic(
                    severity = Severity.ERROR,
                    message = "count() expects an array-like argument, but got a text value"
                )
            }
        } else {
            if (argKind == ValueKind.TEXT) {
                diagnostics += ValidationDiagnostic(
                    severity = Severity.ERROR,
                    message = "${expr.name}() expects an array of numbers, but got a text value"
                )
            }
        }
        return ValueKind.NUMERIC
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
}
