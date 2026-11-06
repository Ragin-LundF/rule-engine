package ruleengine.compiler.value

import ruleengine.core.domain.FieldPathResolver
import ruleengine.core.domain.dto.field.FieldDefinition
import ruleengine.core.domain.dto.field.FieldId
import ruleengine.core.domain.dto.field.FieldSchema
import ruleengine.core.domain.dto.field.FieldType
import ruleengine.core.errors.Severity
import ruleengine.core.errors.ValidationDiagnostic
import ruleengine.dsl.ast.literal.StringLiteral
import ruleengine.dsl.ast.path.FieldSegmentAst
import ruleengine.dsl.ast.path.FilterSegmentAst
import ruleengine.dsl.ast.path.PathSegmentAst
import ruleengine.dsl.ast.value.FieldAccessAst
import ruleengine.dsl.ast.value.FunctionCallValueAst
import ruleengine.dsl.ast.value.LiteralValueAst
import ruleengine.dsl.ast.value.ValueExpressionAst
import ruleengine.evaluator.compiled.AggregateFunctionName
import ruleengine.evaluator.compiled.CollectionFunctionName
import ruleengine.evaluator.compiled.DslFunctions
import java.time.LocalDate

/**
 * Semantic checks for a function call: that the name is known, that the arguments are the right
 * number and the right kind, and — for the functions that read the shape of a collection rather
 * than its values — that the collection is named the way each of them requires.
 *
 * Split out of [ValueExpressionValidator], which owns comparisons and paths. The two call into each
 * other: an argument is an ordinary value expression, and a function call is an ordinary operand.
 */
internal object FunctionCallValidator {

    /** A key name plus the two sources that make a join worth writing. */
    private const val MIN_KEYED_SUM_ARGUMENTS = 3

    fun validate(
        expr: FunctionCallValueAst,
        schema: FieldSchema,
        diagnostics: MutableList<ValidationDiagnostic>
    ): ValueKind {
        CollectionFunctionName.fromName(name = expr.name)?.let { collectionFunction ->
            return when (collectionFunction) {
                CollectionFunctionName.SUM_BY_KEY ->
                    validateKeyedSum(expr = expr, schema = schema, diagnostics = diagnostics)

                CollectionFunctionName.IS_EMPTY ->
                    validateCollectionEmpty(expr = expr, schema = schema, diagnostics = diagnostics)

                CollectionFunctionName.IS_MISSING, CollectionFunctionName.IS_PRESENT -> validatePresence(
                    function = collectionFunction,
                    expr = expr,
                    schema = schema,
                    diagnostics = diagnostics
                )

                CollectionFunctionName.EVERY, CollectionFunctionName.ANY -> validateCollectionPredicate(
                    function = collectionFunction,
                    expr = expr,
                    schema = schema,
                    diagnostics = diagnostics
                )
            }
        }
        val function = AggregateFunctionName.fromName(name = expr.name)
        if (function == null) {
            diagnostics += ValidationDiagnostic(
                severity = Severity.ERROR,
                message = "Unknown function '${expr.name}'; supported functions are: " +
                        DslFunctions.allNames().joinToString()
            )
            return ValueKind.UNKNOWN
        }
        if (expr.arguments.size !in function.arity) {
            diagnostics += ValidationDiagnostic(
                severity = Severity.ERROR,
                message = "Function '${expr.name}' requires " +
                        "${ValueExpressionValidator.arityText(arity = function.arity)}, " +
                        "but got ${expr.arguments.size}"
            )
            return ValueKind.UNKNOWN
        }
        val argKinds = expr.arguments.map { argument ->
            ValueExpressionValidator.validateValueExpression(
                expr = argument,
                schema = schema,
                diagnostics = diagnostics
            )
        }
        if (function == AggregateFunctionName.DEFAULT) {
            return validateDefault(expr = expr, argKinds = argKinds, schema = schema, diagnostics = diagnostics)
        }
        validateArgumentKinds(function = function, expr = expr, argKinds = argKinds, diagnostics = diagnostics)
        return ValueExpressionValidator.valueKindOf(resultKind = function.resultKind)
    }

    /**
     * `default(value, fallback)` is for one value: a missing collection reads as empty through
     * `orEmpty`, which keeps the empty-collection answers of `count` and `sum`. The fallback must be
     * comparable with the value, or a missing value would silently change the comparison's kind. The
     * call has the kind of its value.
     */
    private fun validateDefault(
        expr: FunctionCallValueAst,
        argKinds: List<ValueKind>,
        schema: FieldSchema,
        diagnostics: MutableList<ValidationDiagnostic>
    ): ValueKind {
        val (valueKind, fallbackKind) = argKinds
        val value = expr.arguments.first()
        val collectionValued = valueKind == ValueKind.ARRAY ||
            (value is FieldAccessAst && PathShape.isCollectionValued(path = value.path, schema = schema))
        if (collectionValued) {
            diagnostics += ValidationDiagnostic(
                severity = Severity.ERROR,
                message = "default() expects a single value, but its first argument is a collection; " +
                        "read a missing collection as empty with orEmpty(...) instead"
            )
            return ValueKind.UNKNOWN
        }
        val mismatch = fallbackKind == ValueKind.ARRAY || (
            valueKind != ValueKind.UNKNOWN && fallbackKind != ValueKind.UNKNOWN &&
                !ValueExpressionValidator.compatible(leftKind = valueKind, rightKind = fallbackKind)
            )
        if (mismatch) {
            diagnostics += ValidationDiagnostic(
                severity = Severity.ERROR,
                message = "default() needs a fallback of the same kind as its value, but the value is " +
                        "${valueKind.name.lowercase()} and the fallback ${fallbackKind.name.lowercase()}"
            )
        }
        return valueKind
    }

    /**
     * `isMissing` / `isPresent` accept whatever `isAvailable` accepts: the question is whether the
     * record carries the argument, which is worth asking of any value.
     */
    private fun validatePresence(
        function: CollectionFunctionName,
        expr: FunctionCallValueAst,
        schema: FieldSchema,
        diagnostics: MutableList<ValidationDiagnostic>
    ): ValueKind {
        if (expr.arguments.size !in function.arity) {
            diagnostics += ValidationDiagnostic(
                severity = Severity.ERROR,
                message = "Function '${expr.name}' requires " +
                        "${ValueExpressionValidator.arityText(arity = function.arity)}, " +
                        "but got ${expr.arguments.size}"
            )
            return ValueKind.UNKNOWN
        }
        ValueExpressionValidator.validateValueExpression(
            expr = expr.arguments.single(),
            schema = schema,
            diagnostics = diagnostics
        )
        return ValueExpressionValidator.valueKindOf(resultKind = function.resultKind)
    }

    /**
     * `every` / `any` take one collection carrying the condition to test, written as a filter.
     *
     * Everything before the trailing filter is an ordinary path, so validating the argument whole
     * covers the collection, any earlier filters, a slice, and the predicate itself — the predicate
     * is checked against the element's members like any other filter.
     */
    private fun validateCollectionPredicate(
        function: CollectionFunctionName,
        expr: FunctionCallValueAst,
        schema: FieldSchema,
        diagnostics: MutableList<ValidationDiagnostic>
    ): ValueKind {
        if (expr.arguments.size !in function.arity) {
            diagnostics += ValidationDiagnostic(
                severity = Severity.ERROR,
                message = "Function '${expr.name}' requires " +
                        "${ValueExpressionValidator.arityText(arity = function.arity)}, " +
                        "but got ${expr.arguments.size}"
            )
            return ValueKind.UNKNOWN
        }
        val argument = expr.arguments.single()
        if (argument !is FieldAccessAst || argument.path.lastOrNull() !is FilterSegmentAst) {
            diagnostics += ValidationDiagnostic(
                severity = Severity.ERROR,
                message = "${expr.name}() expects a collection with a condition, " +
                        "such as ${expr.name}(orders[total > 0])"
            )
            return ValueKind.UNKNOWN
        }
        ValueExpressionValidator.validateFieldAccess(expr = argument, schema = schema, diagnostics = diagnostics)
        return ValueExpressionValidator.valueKindOf(resultKind = function.resultKind)
    }

    /**
     * `isEmpty` takes a plain collection path — no trailing filter, unlike `every` and `any`.
     *
     * The argument has to be a path: the question is whether the record *carries* elements, and only
     * a path can be walked back to the raw input to tell an empty collection from an absent one. An
     * aggregate, a literal or a variable has already lost that distinction.
     */
    private fun validateCollectionEmpty(
        expr: FunctionCallValueAst,
        schema: FieldSchema,
        diagnostics: MutableList<ValidationDiagnostic>
    ): ValueKind {
        if (expr.arguments.size !in CollectionFunctionName.IS_EMPTY.arity) {
            diagnostics += ValidationDiagnostic(
                severity = Severity.ERROR,
                message = "Function 'isEmpty' requires " +
                        "${ValueExpressionValidator.arityText(arity = CollectionFunctionName.IS_EMPTY.arity)}, " +
                        "but got ${expr.arguments.size}"
            )
            return ValueKind.UNKNOWN
        }
        val argument = expr.arguments.single()
        if (argument !is FieldAccessAst) {
            diagnostics += ValidationDiagnostic(
                severity = Severity.ERROR,
                message = "isEmpty() expects a collection, such as isEmpty(orders)"
            )
            return ValueKind.UNKNOWN
        }
        ValueExpressionValidator.validateFieldAccess(expr = argument, schema = schema, diagnostics = diagnostics)
        return ValueExpressionValidator.valueKindOf(resultKind = CollectionFunctionName.IS_EMPTY.resultKind)
    }

    /**
     * `sumByKey` takes a key member name and two or more `<collection>.<numericMember>` sources.
     *
     * Everything checkable at load time is checked here: the key is a literal, each source names a
     * collection, every joined collection declares the key, the key types agree, and the value
     * member is numeric. A member the schema does not describe is left alone, matching how every
     * other path is treated.
     */
    @Suppress("ReturnCount")
    private fun validateKeyedSum(
        expr: FunctionCallValueAst,
        schema: FieldSchema,
        diagnostics: MutableList<ValidationDiagnostic>
    ): ValueKind {
        if (expr.arguments.size < MIN_KEYED_SUM_ARGUMENTS) {
            diagnostics += ValidationDiagnostic(
                severity = Severity.ERROR,
                message = "sumByKey() expects a key name and at least two sources, " +
                        "but got ${expr.arguments.size} arguments"
            )
            return ValueKind.UNKNOWN
        }
        val key = ((expr.arguments.first() as? LiteralValueAst)?.literal as? StringLiteral)?.value
        if (key == null) {
            diagnostics += ValidationDiagnostic(
                severity = Severity.ERROR,
                message = "sumByKey() expects the key member name as its first argument, such as " +
                        "sumByKey(\"month\", sales.amount, refunds.amount)"
            )
            return ValueKind.UNKNOWN
        }
        var keyType: FieldType? = null
        for (argument in expr.arguments.drop(n = 1)) {
            keyType = validateKeyedSumSource(
                functionName = CollectionFunctionName.SUM_BY_KEY.dslName,
                argument = argument,
                key = key,
                keyType = keyType,
                schema = schema,
                diagnostics = diagnostics
            )
        }
        return ValueKind.ARRAY
    }

    /**
     * Checks one `<collection>.<member>` source and returns the key type agreed on so far.
     *
     * Shared by `sumByKey` and `joinByKey`, which read their sources the same way; [functionName]
     * is what the diagnostics name.
     */
    @Suppress("ReturnCount")
    fun validateKeyedSumSource(
        functionName: String,
        argument: ValueExpressionAst,
        key: String,
        keyType: FieldType?,
        schema: FieldSchema,
        diagnostics: MutableList<ValidationDiagnostic>
    ): FieldType? {
        val path = (argument as? FieldAccessAst)?.path
        val valueMember = (path?.lastOrNull() as? FieldSegmentAst)?.name
        if (path == null || valueMember == null || path.size < 2) {
            diagnostics += ValidationDiagnostic(
                severity = Severity.ERROR,
                message = "$functionName() expects each source to name a collection and a numeric " +
                        "member, such as sales.amount"
            )
            return keyType
        }
        ValueExpressionValidator.validateFieldAccess(expr = argument, schema = schema, diagnostics = diagnostics)

        val members = memberFields(path = path.dropLast(n = 1), schema = schema)
        if (members.isEmpty()) {
            // The collection declares no members, so there is nothing to check the key against.
            return keyType
        }
        val declaredKey = members[FieldId(value = key)]
        if (declaredKey == null) {
            diagnostics += ValidationDiagnostic(
                severity = Severity.ERROR,
                message = "$functionName() joins on '$key', which " +
                        "'${ValueExpressionValidator.pathText(path = path.dropLast(n = 1))}' does not declare"
            )
            return keyType
        }
        if (keyType != null && declaredKey.type != keyType) {
            diagnostics += ValidationDiagnostic(
                severity = Severity.ERROR,
                message = "$functionName() joins on '$key', but the sources declare it as " +
                        "${keyType.name.lowercase()} and ${declaredKey.type.name.lowercase()}"
            )
        }
        val declaredValue = members[FieldId(value = valueMember)]
        if (declaredValue != null && ValueExpressionValidator.kindOf(definition = declaredValue) != ValueKind.NUMERIC) {
            diagnostics += ValidationDiagnostic(
                severity = Severity.ERROR,
                message = "$functionName() sums '$valueMember', which is declared " +
                        "${declaredValue.type.name.lowercase()} rather than a number"
            )
        }
        return declaredKey.type
    }

    /** The declared members of the field a path ends at, or none once the path leaves the schema. */
    fun memberFields(
        path: List<PathSegmentAst>,
        schema: FieldSchema
    ): Map<FieldId, FieldDefinition> {
        var fields = schema.fields
        for (segment in path) {
            if (segment !is FieldSegmentAst) {
                continue
            }
            val name = FieldPathResolver.resolveName(identifier = segment.name, fields = fields)
            fields = fields[FieldId(value = name)]?.fields.orEmpty()
        }
        return fields
    }

    private fun validateArgumentKinds(
        function: AggregateFunctionName,
        expr: FunctionCallValueAst,
        argKinds: List<ValueKind>,
        diagnostics: MutableList<ValidationDiagnostic>
    ) {
        when (function) {
            AggregateFunctionName.COUNT -> rejectKind(
                actual = argKinds.firstOrNull(),
                rejected = ValueKind.TEXT,
                message = "count() expects an array-like argument, but got a text value",
                diagnostics = diagnostics
            )

            AggregateFunctionName.ABS -> rejectKind(
                actual = argKinds.firstOrNull(),
                rejected = ValueKind.TEXT,
                message = "abs() expects a numeric value, but got a text value",
                diagnostics = diagnostics
            )

            AggregateFunctionName.DAYS_BETWEEN -> validateDateOperands(
                expr = expr,
                argKinds = argKinds,
                diagnostics = diagnostics
            )

            // Deliberately unchecked: the question is whether the argument arrived, which is worth
            // asking of a text field, a whole structure or a variable alike. `default` is checked by
            // `validateDefault` before this is reached.
            AggregateFunctionName.IS_AVAILABLE, AggregateFunctionName.DEFAULT -> Unit

            else -> rejectKind(
                actual = argKinds.firstOrNull(),
                rejected = ValueKind.TEXT,
                message = "${expr.name}() expects an array of numbers, but got a text value",
                diagnostics = diagnostics
            )
        }
    }

    /**
     * Both operands of `daysBetween` must be readable as a calendar date.
     *
     * A string *literal* is accepted when it is ISO-8601, because that is how a date is written
     * inline. A declared text field is not: the schema has said it holds text, and silently reading
     * it as a date is how a rule ends up never matching. An undeclared member types as NUMERIC —
     * the permissive default for anything the schema does not describe — and is accepted for the
     * same reason every other function accepts it.
     */
    private fun validateDateOperands(
        expr: FunctionCallValueAst,
        argKinds: List<ValueKind>,
        diagnostics: MutableList<ValidationDiagnostic>
    ) {
        val acceptedKinds = setOf(ValueKind.DATE, ValueKind.NUMERIC, ValueKind.UNKNOWN)
        expr.arguments.forEachIndexed { index, argument ->
            val literal = (argument as? LiteralValueAst)?.literal
            val readable = if (literal is StringLiteral) {
                runCatching { LocalDate.parse(literal.value) }.isSuccess
            } else {
                argKinds.getOrNull(index = index) in acceptedKinds
            }
            if (!readable) {
                diagnostics += ValidationDiagnostic(
                    severity = Severity.ERROR,
                    message = "daysBetween() expects two date values, but argument ${index + 1} " +
                            "is not readable as a date"
                )
            }
        }
    }

    private fun rejectKind(
        actual: ValueKind?,
        rejected: ValueKind,
        message: String,
        diagnostics: MutableList<ValidationDiagnostic>
    ) {
        if (actual == rejected) {
            diagnostics += ValidationDiagnostic(severity = Severity.ERROR, message = message)
        }
    }
}
