package ruleengine.compiler.value

import ruleengine.core.domain.FieldPathResolver
import ruleengine.core.domain.dto.field.FieldDefinition
import ruleengine.core.domain.dto.field.FieldId
import ruleengine.core.domain.dto.field.FieldSchema
import ruleengine.core.domain.dto.field.FieldType
import ruleengine.core.domain.dto.field.isStructure
import ruleengine.core.errors.Severity
import ruleengine.core.errors.ValidationDiagnostic
import ruleengine.dsl.ast.ArithmeticValueAst
import ruleengine.dsl.ast.BooleanLiteral
import ruleengine.dsl.ast.ComparisonExpressionAst
import ruleengine.dsl.ast.ComparisonOperatorAst
import ruleengine.dsl.ast.ExpressionAst
import ruleengine.dsl.ast.FieldAccessAst
import ruleengine.dsl.ast.FieldSegmentAst
import ruleengine.dsl.ast.FilterSegmentAst
import ruleengine.dsl.ast.FunctionCallValueAst
import ruleengine.dsl.ast.LiteralValueAst
import ruleengine.dsl.ast.NumberLiteral
import ruleengine.dsl.ast.PathSegmentAst
import ruleengine.dsl.ast.StringLiteral
import ruleengine.dsl.ast.ValueExpressionAst
import ruleengine.evaluator.compiled.AggregateFunctionName

internal object ValueExpressionValidator {

    private enum class ValueKind { NUMERIC, TEXT, BOOLEAN, UNKNOWN }

    /** Kinds that support only equality comparisons. */
    private val EQUALITY_ONLY_KINDS = setOf(ValueKind.TEXT, ValueKind.BOOLEAN)

    private val EQUALITY_OPERATORS = setOf(ComparisonOperatorAst.EQ, ComparisonOperatorAst.NEQ)

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

        if (leftKind in EQUALITY_ONLY_KINDS && expr.operator !in EQUALITY_OPERATORS) {
            diagnostics += ValidationDiagnostic(
                severity = Severity.ERROR,
                message = "Operator '${expr.operator}' is not allowed for " +
                        "${leftKind.name.lowercase()} comparisons; use == or !="
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
                is BooleanLiteral -> ValueKind.BOOLEAN
                else -> ValueKind.UNKNOWN
            }
            is FieldAccessAst -> validateFieldAccess(expr = expr, schema = schema, diagnostics = diagnostics)
            is ArithmeticValueAst -> validateArithmetic(expr = expr, schema = schema, diagnostics = diagnostics)
            is FunctionCallValueAst -> validateFunctionCall(expr = expr, schema = schema, diagnostics = diagnostics)
        }
    }

    /**
     * Walks a field path of arbitrary length, one segment at a time, descending
     * [FieldDefinition.fields] so that `orders[status == "paid"].items[price > 0].price`
     * is typed from its declared leaf rather than assumed numeric.
     *
     * The walk is permissive by design: as soon as a node stops declaring nested members it yields
     * [ValueKind.NUMERIC], which is exactly how every multi-segment path was treated before nested
     * schema declarations existed. Schemas written against the old model therefore validate unchanged.
     */
    private fun validateFieldAccess(
        expr: FieldAccessAst,
        schema: FieldSchema,
        diagnostics: MutableList<ValidationDiagnostic>
    ): ValueKind {
        val rootSegment = expr.path.firstOrNull() as? FieldSegmentAst ?: return ValueKind.UNKNOWN
        val isSingleSegment = expr.path.size == 1
        val resolvedId = FieldPathResolver.resolveName(identifier = rootSegment.name, fields = schema.fields)
        var current: FieldDefinition? = schema.fields[FieldId(value = resolvedId)]

        if (current == null) {
            // A single-segment path must name a declared field. For longer paths the root may be an
            // undeclared structure read straight from the raw context, so this stays a warning to
            // avoid failing rules that were valid before nested declarations existed.
            diagnostics += ValidationDiagnostic(
                severity = if (isSingleSegment) Severity.ERROR else Severity.WARNING,
                message = "Unknown field '${rootSegment.name}' in expression"
            )
            return if (isSingleSegment) ValueKind.UNKNOWN else ValueKind.NUMERIC
        }

        for (segment in expr.path.drop(n = 1)) {
            when (segment) {
                is FilterSegmentAst -> validateFilterExpression(
                    expr = segment.expression,
                    scope = current,
                    diagnostics = diagnostics
                )

                is FieldSegmentAst -> {
                    val step = resolveMember(
                        parent = current,
                        segment = segment,
                        path = expr.path,
                        diagnostics = diagnostics
                    )
                    if (step is MemberStep.Invalid) return ValueKind.UNKNOWN
                    current = (step as? MemberStep.Declared)?.definition
                }
            }
        }

        return kindOf(definition = current)
    }

    /** Outcome of descending one path segment. */
    private sealed interface MemberStep {
        /** The segment resolved to a declared member. */
        data class Declared(val definition: FieldDefinition) : MemberStep

        /** The parent declares no members, so typing stops here and stays permissive. */
        data object Undeclared : MemberStep

        /** The parent declares members but not this one — a real error. */
        data object Invalid : MemberStep
    }

    private fun resolveMember(
        parent: FieldDefinition?,
        segment: FieldSegmentAst,
        path: List<PathSegmentAst>,
        diagnostics: MutableList<ValidationDiagnostic>
    ): MemberStep {
        val members = parent?.takeIf { it.type.isStructure }?.fields?.takeIf { it.isNotEmpty() }
            ?: return MemberStep.Undeclared

        val memberId = FieldId(
            value = FieldPathResolver.resolveName(identifier = segment.name, fields = members)
        )
        val member = members[memberId]
            ?: run {
                diagnostics += ValidationDiagnostic(
                    severity = Severity.ERROR,
                    message = "Unknown field '${segment.name}' in '${pathText(path = path)}'"
                )
                return MemberStep.Invalid
            }
        return MemberStep.Declared(definition = member)
    }

    /**
     * Maps a resolved leaf to its value kind. A null [definition] means the path left declared
     * territory, which keeps the pre-nesting numeric assumption.
     */
    private fun kindOf(definition: FieldDefinition?): ValueKind = when (definition?.type) {
        null -> ValueKind.NUMERIC
        FieldType.INTEGER, FieldType.DECIMAL -> ValueKind.NUMERIC
        FieldType.TEXT -> ValueKind.TEXT
        FieldType.BOOLEAN -> ValueKind.BOOLEAN
        // array-like and structure types are valid as aggregate function arguments
        else -> ValueKind.NUMERIC
    }

    /**
     * Validates a filter expression against the members of the element it filters — the names inside
     * `[...]` refer to element fields, not top-level fields. Skipped when the element's members are
     * not declared, since there is nothing to check them against.
     */
    private fun validateFilterExpression(
        expr: ExpressionAst,
        scope: FieldDefinition?,
        diagnostics: MutableList<ValidationDiagnostic>
    ) {
        val members = scope?.takeIf { it.type.isStructure }?.fields?.takeIf { it.isNotEmpty() } ?: return
        val elementSchema = FieldSchema(name = scope.id.value, fields = members)
        when (expr) {
            is ComparisonExpressionAst -> validate(expr = expr, schema = elementSchema, diagnostics = diagnostics)
            // ConditionAst (legacy) and other boolean expressions are valid filter expressions
            else -> Unit
        }
    }

    private fun pathText(path: List<PathSegmentAst>): String =
        path.filterIsInstance<FieldSegmentAst>().joinToString(separator = ".") { it.name }

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
        val knownNames = AggregateFunctionName.lowercaseNames()
        if (AggregateFunctionName.fromName(name = expr.name) == null) {
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
        val functionName = AggregateFunctionName.fromName(name = expr.name)
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

}
