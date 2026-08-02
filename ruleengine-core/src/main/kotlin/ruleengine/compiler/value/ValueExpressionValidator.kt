package ruleengine.compiler.value

import ruleengine.compiler.operators.OperatorUtils
import ruleengine.compiler.support.FieldPathMessages
import ruleengine.compiler.support.Suggestions
import ruleengine.core.domain.FieldPathResolution
import ruleengine.core.domain.FieldPathResolver
import ruleengine.core.domain.OperatorNames
import ruleengine.core.domain.dto.field.FieldDefinition
import ruleengine.core.domain.dto.field.FieldId
import ruleengine.core.domain.dto.field.FieldSchema
import ruleengine.core.domain.dto.field.FieldType
import ruleengine.core.domain.dto.field.isStructure
import ruleengine.core.errors.Severity
import ruleengine.core.errors.ValidationDiagnostic
import ruleengine.dsl.ast.AndAst
import ruleengine.dsl.ast.ArithmeticValueAst
import ruleengine.dsl.ast.BooleanLiteral
import ruleengine.dsl.ast.ComparisonExpressionAst
import ruleengine.dsl.ast.ComparisonOperatorAst
import ruleengine.dsl.ast.ConditionAst
import ruleengine.dsl.ast.ExpressionAst
import ruleengine.dsl.ast.FieldAccessAst
import ruleengine.dsl.ast.FieldSegmentAst
import ruleengine.dsl.ast.FilterSegmentAst
import ruleengine.dsl.ast.FunctionCallValueAst
import ruleengine.dsl.ast.LiteralValueAst
import ruleengine.dsl.ast.NotAst
import ruleengine.dsl.ast.NumberLiteral
import ruleengine.dsl.ast.OrAst
import ruleengine.dsl.ast.PathSegmentAst
import ruleengine.dsl.ast.SliceSegmentAst
import ruleengine.dsl.ast.StringLiteral
import ruleengine.dsl.ast.ValueExpressionAst
import ruleengine.dsl.ast.VariableRefAst
import ruleengine.evaluator.compiled.FunctionResultKind

/**
 * Semantic checks for comparisons and for the operands they compare.
 *
 * A handful of members are visible to [FunctionCallValidator] rather than private: the two validate
 * different halves of the same grammar and each is reachable from the other, since an argument is an
 * operand and a call is an operand too.
 */
internal object ValueExpressionValidator {

    /** Kinds that support only equality comparisons. */
    private val EQUALITY_ONLY_KINDS = setOf(ValueKind.TEXT, ValueKind.BOOLEAN, ValueKind.ARRAY)

    /**
     * The canonical operators a legacy filter predicate may use, i.e. the ones
     * `Compiler.compileFilterCondition` can map to a [ComparisonOperatorAst]. `!=` has no canonical
     * form and passes normalisation through unchanged, which is why it is listed as the symbol.
     */
    private val FILTER_CONDITION_OPERATORS = setOf(
        OperatorNames.EQUALS,
        OperatorNames.SYMBOL_NOT_EQUALS,
        OperatorNames.GT,
        OperatorNames.GTE,
        OperatorNames.LT,
        OperatorNames.LTE,
        OperatorNames.IN,
        OperatorNames.CONTAINS,
    )

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

        // `contains` compares a list against one of its elements, or text against a substring, so its
        // two sides are deliberately of different kinds and the pair check below does not apply.
        //
        // Skipping it entirely rather than checking ARRAY-against-element is the deliberate choice:
        // a projection off an undeclared member types as NUMERIC, so a real
        // `orders[status == "paid"].tag contains "a"` would be rejected as NUMERIC-versus-TEXT. The
        // cost is that a nonsense pairing such as `count(orders) contains 5` validates and then
        // never matches.
        if (expr.operator == ComparisonOperatorAst.CONTAINS) {
            return
        }

        if (expr.operator == ComparisonOperatorAst.IN) {
            validateMembership(expr = expr, leftKind = leftKind, rightKind = rightKind, diagnostics = diagnostics)
            return
        }

        if (!compatible(leftKind = leftKind, rightKind = rightKind)) {
            diagnostics += ValidationDiagnostic(
                severity = Severity.ERROR,
                message = "Comparison operands have incompatible types: left is $leftKind, right is $rightKind"
            )
            return
        }

        val comparedKind = comparedKind(leftKind = leftKind, rightKind = rightKind)
        if (comparedKind in EQUALITY_ONLY_KINDS && expr.operator !in EQUALITY_OPERATORS) {
            diagnostics += ValidationDiagnostic(
                severity = Severity.ERROR,
                message = "Operator '${expr.operator}' is not allowed for " +
                        "${comparedKind.name.lowercase()} comparisons; use == or !="
            )
        }
    }

    /**
     * `in` tests one value against a source of many, so its sides are of different kinds by design
     * and the pair check does not apply.
     *
     * What is checked instead: the source has to be something that holds values, and the element has
     * to be a scalar. Both mistakes — testing against a plain number, or asking whether a whole
     * collection is a member — produce a rule that can never match.
     *
     * A projection off an undeclared member types as NUMERIC, the permissive default, so it passes.
     */
    private fun validateMembership(
        expr: ComparisonExpressionAst,
        leftKind: ValueKind,
        rightKind: ValueKind,
        diagnostics: MutableList<ValidationDiagnostic>
    ) {
        if (rightKind == ValueKind.BOOLEAN || rightKind == ValueKind.DATE) {
            diagnostics += ValidationDiagnostic(
                severity = Severity.ERROR,
                message = "'in' expects a collection, string set or list on the right, but got " +
                        rightKind.name.lowercase()
            )
            return
        }
        if (leftKind == ValueKind.ARRAY) {
            diagnostics += ValidationDiagnostic(
                severity = Severity.ERROR,
                message = "'in' tests a single value for membership, but the left side is a collection"
            )
            return
        }
        // A declared source says what it holds, so a text element tested against a numeric source is
        // a mistake worth naming. TEXT on the right is a single value, caught above by kind instead.
        if (rightKind == ValueKind.ARRAY && leftKind == ValueKind.UNKNOWN) {
            return
        }
        if (rightKind == ValueKind.TEXT && leftKind != ValueKind.TEXT) {
            diagnostics += ValidationDiagnostic(
                severity = Severity.ERROR,
                message = "'in' compares ${leftKind.name.lowercase()} against a text value, " +
                        "which can never match"
            )
        }
        if (expr.ignoreCase) {
            diagnostics += ValidationDiagnostic(
                severity = Severity.ERROR,
                message = "'ignoreCase' is not supported for 'in' against a named source; " +
                        "declare a normalizer on the field instead"
            )
        }
    }

    /**
     * Whether two operands may be compared at all.
     *
     * A date paired with text is allowed on purpose. A member of a collection carries no declared
     * type, so `orders[].shippedAt` is text as far as the schema knows, and the comparison reads it
     * as an ISO-8601 date at evaluation time.
     */
    private fun compatible(leftKind: ValueKind, rightKind: ValueKind): Boolean {
        if (leftKind == rightKind) {
            return true
        }
        return setOf(leftKind, rightKind) == setOf(ValueKind.DATE, ValueKind.TEXT)
    }

    /** The kind the comparison is really performed in, which decides whether ordering is allowed. */
    private fun comparedKind(leftKind: ValueKind, rightKind: ValueKind): ValueKind {
        if (leftKind == ValueKind.DATE || rightKind == ValueKind.DATE) {
            return ValueKind.DATE
        }
        return leftKind
    }

    /**
     * Validates a value expression standing on its own rather than as one side of a comparison —
     * the right-hand side of a `set` clause. Only the operand itself is checked; there is no second
     * operand to be type-compatible with.
     */
    fun validateValue(
        expr: ValueExpressionAst,
        schema: FieldSchema,
        diagnostics: MutableList<ValidationDiagnostic>
    ) {
        validateValueExpression(expr = expr, schema = schema, diagnostics = diagnostics)
    }

    fun validateValueExpression(
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
            is FunctionCallValueAst -> FunctionCallValidator.validate(
                expr = expr,
                schema = schema,
                diagnostics = diagnostics
            )
            // A variable carries whatever the assigning expression produced, and which rule assigned
            // it is a runtime question, so it has no static kind. UNKNOWN suppresses the operand-type
            // check rather than guessing; that a variable exists at all is checked by `Validator`.
            is VariableRefAst -> ValueKind.UNKNOWN
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
    fun validateFieldAccess(
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
                    schema = schema,
                    diagnostics = diagnostics
                )

                is SliceSegmentAst -> validateSlice(
                    segment = segment,
                    scope = current,
                    path = expr.path,
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

    /**
     * A slice must name a non-negative whole number of elements, and must narrow a collection.
     *
     * Slicing anything else is a mistake worth reporting: a single value has no source order, so
     * `take(customer, 3)` can only ever mean the author expected a collection there.
     */
    private fun validateSlice(
        segment: SliceSegmentAst,
        scope: FieldDefinition?,
        path: List<PathSegmentAst>,
        diagnostics: MutableList<ValidationDiagnostic>
    ) {
        val call = if (segment.fromEnd) "takeLast" else "take"
        val count = segment.count.toIntOrNull()
        if (count == null || count < 0) {
            diagnostics += ValidationDiagnostic(
                severity = Severity.ERROR,
                message = "$call() expects a non-negative whole number of elements, but got '${segment.count}'"
            )
        }
        if (scope != null && scope.type != FieldType.COLLECTION) {
            diagnostics += ValidationDiagnostic(
                severity = Severity.ERROR,
                message = "$call() expects a collection, but '${pathText(path = path)}' is " +
                        scope.type.name.lowercase()
            )
        }
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
    fun kindOf(definition: FieldDefinition?): ValueKind = when (definition?.type) {
        null -> ValueKind.NUMERIC
        FieldType.INTEGER, FieldType.DECIMAL -> ValueKind.NUMERIC
        FieldType.TEXT -> ValueKind.TEXT
        FieldType.BOOLEAN -> ValueKind.BOOLEAN
        FieldType.DATE, FieldType.DATE_TIME -> ValueKind.DATE
        FieldType.STRING_SET, FieldType.COLLECTION -> ValueKind.ARRAY
        // A structure has no value of its own; comparing one directly is rejected by `Validator`,
        // and as an aggregate argument it has always been treated as numeric.
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
        schema: FieldSchema,
        diagnostics: MutableList<ValidationDiagnostic>
    ) {
        val members = scope?.takeIf { it.type.isStructure }?.fields?.takeIf { it.isNotEmpty() } ?: return
        // The document's fields stay in scope behind the element's, matching what the compiler and
        // `ElementRuleContext` do, so a predicate may compare a member against a document field.
        val elementSchema = FieldSchema(name = scope.id.value, fields = schema.fields + members)
        validateFilterPredicate(expr = expr, elementSchema = elementSchema, diagnostics = diagnostics)
    }

    /**
     * Walks a filter predicate, checking every leaf against the element's scope.
     *
     * Mirrors `Validator.validateExpression` rather than inspecting only the outermost node: an
     * `and` inside `[...]` is a predicate like any other, and stopping at it left *both* halves of
     * `orders[a > 1 and b == 2]` unchecked — including the modern half, which is checked everywhere
     * else.
     */
    private fun validateFilterPredicate(
        expr: ExpressionAst,
        elementSchema: FieldSchema,
        diagnostics: MutableList<ValidationDiagnostic>
    ) {
        when (expr) {
            is AndAst -> expr.children.forEach { child ->
                validateFilterPredicate(expr = child, elementSchema = elementSchema, diagnostics = diagnostics)
            }

            is OrAst -> expr.children.forEach { child ->
                validateFilterPredicate(expr = child, elementSchema = elementSchema, diagnostics = diagnostics)
            }

            is NotAst -> validateFilterPredicate(
                expr = expr.child,
                elementSchema = elementSchema,
                diagnostics = diagnostics
            )

            is ComparisonExpressionAst -> validate(expr = expr, schema = elementSchema, diagnostics = diagnostics)

            is ConditionAst -> validateFilterCondition(
                cond = expr,
                elementSchema = elementSchema,
                diagnostics = diagnostics
            )
        }
    }

    /**
     * Checks a legacy `field op literal` predicate — the form the parser produces inside `[...]` for
     * every operator that does not force the modern path, i.e. everything but `==`, `!=` and a
     * comparison against another field.
     *
     * The member is resolved through [FieldPathResolver.resolve], which is what
     * `Compiler.resolveFilterMember` mirrors: a flat declaration first, then the name walked one
     * segment at a time, so `parcels[origin.hub == "HAM"]` names a real path either way.
     *
     * The operator and `ignoreCase` checks mirror the two `CompilationException`s the compiler throws
     * for the same predicates, so the author reads a diagnostic instead of catching an exception.
     */
    private fun validateFilterCondition(
        cond: ConditionAst,
        elementSchema: FieldSchema,
        diagnostics: MutableList<ValidationDiagnostic>
    ) {
        when (val resolution = FieldPathResolver.resolve(identifier = cond.field, schema = elementSchema)) {
            is FieldPathResolution.Resolved -> Unit

            // The path reads into a collection, so it projects many values where the predicate compares
            // one. The modern form is the only one that can express that.
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
                    message = "Unknown field '${cond.field}' in filter on '${elementSchema.name}'",
                    suggestion = Suggestions.suggestClosest(
                        input = cond.field,
                        candidates = FieldPathResolver.scalarPaths(schema = elementSchema).keys.map { it.value }
                    ),
                    line = cond.line,
                    column = cond.column,
                )
                return
            }
        }

        if (cond.ignoreCase) {
            diagnostics += ValidationDiagnostic(
                severity = Severity.ERROR,
                message = "The 'ignoreCase' modifier is not supported in filter segments",
                line = cond.line,
                column = cond.column,
            )
        }

        val canonical = OperatorUtils.normalizeOperator(op = cond.operator)
        if (canonical !in FILTER_CONDITION_OPERATORS) {
            diagnostics += ValidationDiagnostic(
                severity = Severity.ERROR,
                message = "Operator '$canonical' is not supported in filter segments",
                line = cond.line,
                column = cond.column,
            )
        }
    }

    fun pathText(path: List<PathSegmentAst>): String =
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

    fun valueKindOf(resultKind: FunctionResultKind): ValueKind = when (resultKind) {
        FunctionResultKind.NUMERIC -> ValueKind.NUMERIC
        FunctionResultKind.BOOLEAN -> ValueKind.BOOLEAN
        FunctionResultKind.ARRAY -> ValueKind.ARRAY
        FunctionResultKind.DATE -> ValueKind.DATE
    }

    fun arityText(arity: IntRange): String {
        if (arity.first == arity.last) {
            return if (arity.first == 1) "exactly one argument" else "exactly ${arity.first} arguments"
        }
        return "between ${arity.first} and ${arity.last} arguments"
    }

}
