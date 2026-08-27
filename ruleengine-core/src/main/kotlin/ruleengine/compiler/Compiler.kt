package ruleengine.compiler

import ruleengine.compiler.operators.DateOperator
import ruleengine.compiler.operators.DecimalOperator
import ruleengine.compiler.operators.IntegerOperator
import ruleengine.compiler.operators.OperatorUtils
import ruleengine.compiler.operators.TextComparisonOperators
import ruleengine.compiler.operators.TextInOperator
import ruleengine.compiler.operators.TextRegexOperator
import ruleengine.compiler.support.FieldPathMessages
import ruleengine.compiler.value.ValueExpressionCompiler
import ruleengine.core.domain.FieldPathResolution
import ruleengine.core.domain.FieldPathResolver
import ruleengine.core.domain.OperatorNames
import ruleengine.core.domain.dto.NormalizerId
import ruleengine.core.domain.dto.field.FieldDefinition
import ruleengine.core.domain.dto.field.FieldId
import ruleengine.core.domain.dto.field.FieldSchema
import ruleengine.core.domain.dto.field.FieldType.BOOLEAN
import ruleengine.core.domain.dto.field.FieldType.COLLECTION
import ruleengine.core.domain.dto.field.FieldType.DATE
import ruleengine.core.domain.dto.field.FieldType.DATE_TIME
import ruleengine.core.domain.dto.field.FieldType.DECIMAL
import ruleengine.core.domain.dto.field.FieldType.INTEGER
import ruleengine.core.domain.dto.field.FieldType.OBJECT
import ruleengine.core.domain.dto.field.FieldType.STRING_SET
import ruleengine.core.domain.dto.field.FieldType.TEXT
import ruleengine.core.domain.dto.field.isMultiValued
import ruleengine.core.errors.CompilationException
import ruleengine.core.normalizer.NormalizerRegistry
import ruleengine.dsl.ast.ActionAst
import ruleengine.dsl.ast.AndAst
import ruleengine.dsl.ast.AssignmentKindAst
import ruleengine.dsl.ast.BooleanLiteral
import ruleengine.dsl.ast.ComparisonExpressionAst
import ruleengine.dsl.ast.ComparisonOperatorAst
import ruleengine.dsl.ast.ConditionAst
import ruleengine.dsl.ast.ExpressionAst
import ruleengine.dsl.ast.ExtractionAst
import ruleengine.dsl.ast.ExtractionRefLiteral
import ruleengine.dsl.ast.ListLiteral
import ruleengine.dsl.ast.LiteralAst
import ruleengine.dsl.ast.LiteralValueAst
import ruleengine.dsl.ast.NotAst
import ruleengine.dsl.ast.NumberLiteral
import ruleengine.dsl.ast.OrAst
import ruleengine.dsl.ast.RuleAst
import ruleengine.dsl.ast.StringLiteral
import ruleengine.dsl.ast.ValueExpressionAst
import ruleengine.dsl.ast.ValueExpressionRenderer
import ruleengine.dsl.ast.VariableAssignmentAst
import ruleengine.dsl.ast.VariableRefLiteral
import ruleengine.evaluator.CompiledAction
import ruleengine.evaluator.CompiledAddAssignment
import ruleengine.evaluator.CompiledAssignment
import ruleengine.evaluator.CompiledRule
import ruleengine.evaluator.CompiledSetAssignment
import ruleengine.evaluator.compiled.CompiledActionArgument
import ruleengine.evaluator.compiled.CompiledExpression
import ruleengine.evaluator.compiled.EvaluationCost
import ruleengine.evaluator.compiled.bool.BooleanEqualsExpression
import ruleengine.evaluator.compiled.logic.AndExpression
import ruleengine.evaluator.compiled.logic.NotExpression
import ruleengine.evaluator.compiled.logic.OrExpression
import ruleengine.evaluator.compiled.stringset.StringSetContainsAllExpression
import ruleengine.evaluator.compiled.stringset.StringSetContainsAnyExpression
import ruleengine.evaluator.compiled.text.RegexExtractExpression
import ruleengine.evaluator.compiled.value.ComparisonCompiledExpression
import ruleengine.evaluator.compiled.value.CompiledValueExpression
import ruleengine.evaluator.compiled.value.FieldAccessCompiledValueExpression
import ruleengine.evaluator.compiled.value.LiteralCompiledValueExpression
import ruleengine.evaluator.compiled.value.path.CompiledFieldSegment
import ruleengine.evaluator.compiled.value.result.ArrayExpressionValue
import ruleengine.evaluator.compiled.value.result.ExpressionValue
import ruleengine.evaluator.compiled.value.result.NumberExpressionValue
import ruleengine.evaluator.compiled.value.result.TextExpressionValue
import java.math.BigDecimal

/**
 * Turns validated [RuleAst]s into [CompiledRule]s.
 *
 * Every function that can fail takes the id of the rule being compiled and hands it to
 * [CompilationException], so a failure names the offending rule instead of `<unknown>`.
 */
object Compiler {
    fun compileRules(
        asts: List<RuleAst>,
        schema: FieldSchema,
        normalizerRegistry: NormalizerRegistry = NormalizerRegistry.default
    ): List<CompiledRule> {
        return asts.map { compileRule(ast = it, schema = schema, normalizerRegistry = normalizerRegistry) }
    }

    fun compileRule(
        ast: RuleAst,
        schema: FieldSchema,
        normalizerRegistry: NormalizerRegistry = NormalizerRegistry.default
    ): CompiledRule {
        val expr = compileExpression(
            expr = ast.condition,
            schema = schema,
            normalizerRegistry = normalizerRegistry,
            ruleId = ast.id,
            // Only a rule that declares the branch asks its condition to distinguish "no" from
            // "cannot say". Without it every `not` keeps reading missing data as false, which is what
            // every rule written before the branch existed depends on.
            unknownAware = ast.hasNotExistsBranch,
        )
        return CompiledRule(
            id = ast.id,
            expression = expr,
            actions = compileActions(actions = ast.actions, schema = schema, ruleId = ast.id),
            assignments = compileAssignments(assignments = ast.assignments, schema = schema, ruleId = ast.id),
            elseActions = compileActions(actions = ast.elseActions, schema = schema, ruleId = ast.id),
            elseAssignments = compileAssignments(assignments = ast.elseAssignments, schema = schema, ruleId = ast.id),
            notExistsActions = compileActions(actions = ast.notExistsActions, schema = schema, ruleId = ast.id),
            notExistsAssignments = compileAssignments(
                assignments = ast.notExistsAssignments,
                schema = schema,
                ruleId = ast.id,
            ),
            stopOnThen = ast.stopOnThen,
            stopOnElse = ast.stopOnElse,
            stopOnNotExists = ast.stopOnNotExists,
        )
    }

    private fun compileActions(
        actions: List<ActionAst>,
        schema: FieldSchema,
        ruleId: String?
    ): List<CompiledAction> {
        return actions.map { action -> compileAction(action = action, schema = schema, ruleId = ruleId) }
    }

    private fun compileAssignments(
        assignments: List<VariableAssignmentAst>,
        schema: FieldSchema,
        ruleId: String?
    ): List<CompiledAssignment> {
        return assignments.map { assignment ->
            compileAssignment(assignment = assignment, schema = schema, ruleId = ruleId)
        }
    }

    private fun compileAssignment(
        assignment: VariableAssignmentAst,
        schema: FieldSchema,
        ruleId: String?
    ): CompiledAssignment {
        val filterCompiler = { filterExpr: ExpressionAst, filterSchema: FieldSchema ->
            compileFilterExpression(expr = filterExpr, schema = filterSchema, ruleId = ruleId)
        }
        val expression = ValueExpressionCompiler.compile(
            expr = assignment.expression,
            schema = schema,
            ruleId = ruleId,
            filterCompiler = filterCompiler
        )
        return when (assignment.kind) {
            AssignmentKindAst.SET -> CompiledSetAssignment(name = assignment.name, expression = expression)
            AssignmentKindAst.ADD -> CompiledAddAssignment(name = assignment.name, expression = expression)
        }
    }

    private fun compileAction(action: ActionAst, schema: FieldSchema, ruleId: String?): CompiledAction {
        val compiledExtraction = action.extraction?.let { extraction ->
            compileExtraction(extraction = extraction, schema = schema, ruleId = ruleId)
        }

        val compiledArguments = action.arguments.map { literal ->
            when (literal) {
                is ExtractionRefLiteral -> {
                    val extraction = compiledExtraction ?: throw CompilationException(
                        ruleId = ruleId,
                        details = "Action '${action.name}' uses extraction reference but has no 'extract' clause"
                    )
                    CompiledActionArgument.ExtractionRef(extraction = extraction)
                }

                is VariableRefLiteral -> CompiledActionArgument.VariableRef(name = literal.name)

                else -> CompiledActionArgument.Static(
                    value = staticArgumentValue(literal = literal, actionName = action.name, ruleId = ruleId)
                )
            }
        }

        return CompiledAction(name = action.name, arguments = compiledArguments)
    }

    /**
     * Compile-time value of an action argument literal.
     *
     * A number keeps its literal text, which is what the action layer already expects for a top-level numeric
     * argument. List items go through the same conversion so a non-string element stays a value instead of
     * becoming the AST node's `toString()`.
     */
    private fun staticArgumentValue(literal: LiteralAst, actionName: String, ruleId: String?): Any {
        return when (literal) {
            is StringLiteral -> literal.value
            is NumberLiteral -> literal.value
            is BooleanLiteral -> literal.value
            is ListLiteral -> literal.items.map { item ->
                staticArgumentValue(literal = item, actionName = actionName, ruleId = ruleId)
            }

            else -> throw CompilationException(
                ruleId = ruleId,
                details = "Action '$actionName' does not support a ${literal::class.simpleName} argument"
            )
        }
    }

    private fun compileExtraction(
        extraction: ExtractionAst,
        schema: FieldSchema,
        ruleId: String?
    ): RegexExtractExpression {
        return when (extraction) {
            is ExtractionAst.RegexExtraction -> {
                val resolution = FieldPathResolver.resolve(identifier = extraction.sourceField, schema = schema)
                val fieldId = (resolution as? FieldPathResolution.Resolved)?.id
                    ?: throw CompilationException(
                        ruleId = ruleId,
                        details = "Extraction references unknown field '${extraction.sourceField}'"
                    )
                val compiledPattern = runCatching {
                    Regex(pattern = extraction.pattern)
                }.getOrElse { cause ->
                    throw CompilationException(
                        ruleId = ruleId,
                        details = "Invalid regex pattern '${extraction.pattern}' in extraction: ${cause.message}"
                    )
                }
                RegexExtractExpression(
                    field = fieldId,
                    pattern = compiledPattern,
                    groupIndex = extraction.groupIndex
                )
            }
        }
    }

    /**
     * @param unknownAware whether this condition's `not` nodes propagate an undecided child instead of
     *   reading it as false. True only for a rule that declares a `not_exists` block — see
     *   [ruleengine.evaluator.compiled.logic.NotExpression]. A filter predicate is always false here:
     *   its verdict is collapsed to a boolean by the segment that selects elements with it.
     */
    private fun compileExpression(
        expr: ExpressionAst,
        schema: FieldSchema,
        normalizerRegistry: NormalizerRegistry,
        ruleId: String?,
        unknownAware: Boolean = false,
    ): CompiledExpression {
        return when (expr) {
            is AndAst -> AndExpression(children = expr.children.map {
                compileExpression(
                    expr = it,
                    schema = schema,
                    normalizerRegistry = normalizerRegistry,
                    ruleId = ruleId,
                    unknownAware = unknownAware,
                )
            })

            is OrAst -> OrExpression(children = expr.children.map {
                compileExpression(
                    expr = it,
                    schema = schema,
                    normalizerRegistry = normalizerRegistry,
                    ruleId = ruleId,
                    unknownAware = unknownAware,
                )
            })

            is NotAst -> NotExpression(
                child = compileExpression(
                    expr = expr.child,
                    schema = schema,
                    normalizerRegistry = normalizerRegistry,
                    ruleId = ruleId,
                    unknownAware = unknownAware,
                ),
                unknownAware = unknownAware,
            )

            is ConditionAst -> compileCondition(
                cond = expr,
                schema = schema,
                normalizerRegistry = normalizerRegistry,
                ruleId = ruleId
            )

            is ComparisonExpressionAst -> compileComparisonExpression(expr = expr, schema = schema, ruleId = ruleId)
        }
    }

    /**
     * Compiles a filter predicate.
     *
     * Boolean combinations are compiled like any other expression tree, so `orders[a > 1 and b == 2]`
     * means what it reads as. They used to throw here while validation accepted them, which made a
     * rule that had passed every check fail at compile time — and the documented workaround, chaining
     * `[a > 1][b == 2]`, only ever expressed `and`.
     */
    private fun compileFilterExpression(
        expr: ExpressionAst,
        schema: FieldSchema,
        ruleId: String?
    ): CompiledExpression {
        return when (expr) {
            is ComparisonExpressionAst -> compileComparisonExpression(expr = expr, schema = schema, ruleId = ruleId)
            is ConditionAst -> compileFilterCondition(cond = expr, schema = schema, ruleId = ruleId)

            is AndAst -> AndExpression(
                children = expr.children.map { child ->
                    compileFilterExpression(expr = child, schema = schema, ruleId = ruleId)
                }
            )

            is OrAst -> OrExpression(
                children = expr.children.map { child ->
                    compileFilterExpression(expr = child, schema = schema, ruleId = ruleId)
                }
            )

            is NotAst -> NotExpression(
                child = compileFilterExpression(expr = expr.child, schema = schema, ruleId = ruleId)
            )
        }
    }

    private fun compileFilterCondition(
        cond: ConditionAst,
        schema: FieldSchema,
        ruleId: String?
    ): CompiledExpression {
        // ComparisonCompiledExpression has no case-insensitive mode, so honouring the modifier is impossible
        // here. Rejecting it is the only safe option: ignoring it would silently compare case-sensitively.
        if (cond.ignoreCase) {
            throw CompilationException(
                ruleId = ruleId,
                details = "The 'ignoreCase' modifier is not supported in filter segments"
            )
        }
        // Match the canonical names OperatorUtils produces, not the spellings an author may have written:
        // it maps '==', '=' and 'eq' to "equals", '>' to "gt" and so on, so branching on the raw symbols
        // never fires. '!=' has no canonical form and passes through unchanged.
        val op = OperatorUtils.normalizeOperator(op = cond.operator)
        val comparisonOperator = when (op) {
            OperatorNames.EQUALS -> ComparisonOperatorAst.EQ
            OperatorNames.SYMBOL_NOT_EQUALS -> ComparisonOperatorAst.NEQ
            OperatorNames.GT -> ComparisonOperatorAst.GT
            OperatorNames.GTE -> ComparisonOperatorAst.GTE
            OperatorNames.LT -> ComparisonOperatorAst.LT
            OperatorNames.LTE -> ComparisonOperatorAst.LTE
            OperatorNames.IN -> ComparisonOperatorAst.IN
            OperatorNames.CONTAINS -> ComparisonOperatorAst.CONTAINS
            else -> throw CompilationException(
                ruleId = ruleId,
                details = "Operator '$op' is not supported in filter segments"
            )
        }
        val member = resolveFilterMember(field = cond.field, schema = schema)
        // Both sides carry the member's declared normalizers, so a filter matches the same values the
        // same field would match at the top level, where `PreparedRuleContext` normalises the input
        // and the operator compilers normalise the literal.
        val normalizers = member.definition?.normalizers.orEmpty()
        val left = FieldAccessCompiledValueExpression(
            segments = member.segments,
            normalizers = normalizers,
            yieldsCollection = member.definition?.type?.isMultiValued == true
        )
        val right = compileLiteralValue(literal = cond.value, normalizers = normalizers, ruleId = ruleId)
        return ComparisonCompiledExpression(
            left = left,
            operator = comparisonOperator,
            right = right,
            cost = EvaluationCost.CHEAP,
            leftYieldsCollection = left.yieldsCollection
        )
    }

    /** The compiled path a filter predicate's field name reads, together with the field it lands on. */
    private data class FilterMember(
        val segments: List<CompiledFieldSegment>,
        val definition: FieldDefinition?
    )

    /**
     * Resolves a filter predicate's field name to a path, so `parcels[origin.hub == "HAM"]` reads
     * `hub` inside `origin` rather than a member whose name happens to contain a dot.
     *
     * A flat declaration wins first, matching [FieldPathResolver.resolve]: a schema that declares
     * `origin.hub` as one member keeps naming it that way. Otherwise the name is walked one segment at
     * a time exactly as `ValueExpressionCompiler.compileFieldAccess` walks a modern path — including
     * ending the walk at an undeclared member, so a document field of the same name cannot lend its
     * normalizers to a member that declares none.
     *
     * Every segment resolves aliases against the members it is looked up in, matching
     * `ValueExpressionValidator.resolveMember`, so a member declared with an alias may be written either
     * way inside a predicate.
     */
    private fun resolveFilterMember(field: String, schema: FieldSchema): FilterMember {
        val flat = FieldPathResolver.resolveName(identifier = field, fields = schema.fields)
        val flatDefinition = schema.fields[FieldId(value = flat)]
        if (flatDefinition != null) {
            return FilterMember(segments = listOf(CompiledFieldSegment(name = flat)), definition = flatDefinition)
        }

        val segments = mutableListOf<CompiledFieldSegment>()
        var definition: FieldDefinition? = null
        var fields = schema.fields
        for (name in field.split('.')) {
            val resolved = FieldPathResolver.resolveName(identifier = name, fields = fields)
            segments += CompiledFieldSegment(name = resolved)
            definition = fields[FieldId(value = resolved)]
            fields = definition?.fields.orEmpty()
        }
        return FilterMember(segments = segments, definition = definition)
    }

    private fun compileLiteralValue(
        literal: LiteralAst,
        normalizers: List<NormalizerId>,
        ruleId: String?
    ): CompiledValueExpression {
        return LiteralCompiledValueExpression(
            value = literalValue(literal = literal, normalizers = normalizers, ruleId = ruleId)
        )
    }

    private fun literalValue(
        literal: LiteralAst,
        normalizers: List<NormalizerId>,
        ruleId: String?
    ): ExpressionValue {
        return when (literal) {
            is NumberLiteral -> NumberExpressionValue(value = BigDecimal(literal.value))
            is StringLiteral -> TextExpressionValue(
                value = NormalizerRegistry.default.applyAll(value = literal.value, normalizers = normalizers)
            )

            // `parcels[category in ["fragile", "liquid"]]` — a membership source written out. Each
            // item is normalized like a single literal, so the list matches what the field matches.
            is ListLiteral -> ArrayExpressionValue(
                values = literal.items.map { item ->
                    literalValue(literal = item, normalizers = normalizers, ruleId = ruleId)
                }
            )

            else -> throw CompilationException(
                ruleId = ruleId,
                details = "Unsupported literal type in filter: ${literal::class.simpleName}"
            )
        }
    }

    private fun compileComparisonExpression(
        expr: ComparisonExpressionAst,
        schema: FieldSchema,
        ruleId: String?
    ): CompiledExpression {
        val filterCompiler = { filterExpr: ExpressionAst, filterSchema: FieldSchema ->
            compileFilterExpression(expr = filterExpr, schema = filterSchema, ruleId = ruleId)
        }
        val compiledLeft = ValueExpressionCompiler.compile(
            expr = expr.left,
            schema = schema,
            ruleId = ruleId,
            filterCompiler = filterCompiler
        )
        val compiledRight = ValueExpressionCompiler.compile(
            expr = expr.right,
            schema = schema,
            ruleId = ruleId,
            filterCompiler = filterCompiler
        )

        // A text literal is matched under the normalizers declared by the field it is compared
        // against. Without it the two spellings of one comparison disagreed: on a field declaring
        // `lowercase`, `status equals "PAID"` matched — the named-operator path has always normalized
        // its literal — while `status == "PAID"` did not.
        val normalizers = normalizersOf(compiled = compiledLeft).ifEmpty { normalizersOf(compiled = compiledRight) }
        val left = normalizedLiteral(expr = expr.left, normalizers = normalizers, ruleId = ruleId) ?: compiledLeft
        val right = normalizedLiteral(expr = expr.right, normalizers = normalizers, ruleId = ruleId) ?: compiledRight

        val cost = maxOf(left.cost, right.cost)
        return ComparisonCompiledExpression(
            left = left,
            operator = expr.operator,
            right = right,
            cost = cost,
            // Rendered from the AST because it is the last point where the author's text can still be
            // reconstructed: the compiled operands have already rewritten path roots alias → canonical.
            label = ValueExpressionRenderer.render(expr = expr.left),
            leftYieldsCollection = (left as? FieldAccessCompiledValueExpression)?.yieldsCollection == true,
            ignoreCase = expr.ignoreCase
        )
    }

    /** The normalizers a compiled operand declares, or none when it does not read a field. */
    private fun normalizersOf(compiled: CompiledValueExpression): List<NormalizerId> =
        (compiled as? FieldAccessCompiledValueExpression)?.normalizers.orEmpty()

    /**
     * Recompiles a **text** literal under [normalizers], or null when this operand is not one.
     *
     * Only text is rerouted, because normalizers only act on text: a number, a boolean, a date or a
     * variable read keeps the ordinary value-expression path, which is the one that knows how to
     * compile them. A written-out list is included — each item is normalized like a single literal, so
     * `status in ["PAID", "SENT"]` matches what the field matches.
     */
    private fun normalizedLiteral(
        expr: ValueExpressionAst,
        normalizers: List<NormalizerId>,
        ruleId: String?
    ): CompiledValueExpression? {
        if (normalizers.isEmpty()) {
            return null
        }
        val literal = (expr as? LiteralValueAst)?.literal
        if (literal !is StringLiteral && literal !is ListLiteral) {
            return null
        }
        return compileLiteralValue(literal = literal, normalizers = normalizers, ruleId = ruleId)
    }

    private fun compileCondition(
        cond: ConditionAst,
        schema: FieldSchema,
        normalizerRegistry: NormalizerRegistry,
        ruleId: String?
    ): CompiledExpression {
        val resolution = FieldPathResolver.resolve(identifier = cond.field, schema = schema)
        val resolved = resolution as? FieldPathResolution.Resolved ?: throw CompilationException(
            ruleId = ruleId,
            details = when (resolution) {
                is FieldPathResolution.CrossesCollection -> FieldPathMessages.crossesCollection(
                    field = cond.field,
                    collectionPath = resolution.collectionPath
                )

                else -> FieldPathMessages.unknownField(field = cond.field)
            }
        )
        val fieldId = resolved.id
        val def = resolved.definition

        val op = OperatorUtils.normalizeOperator(op = cond.operator)

        return when (def.type) {
            TEXT -> compileTextCondition(
                cond = cond,
                fieldId = fieldId,
                def = def,
                op = op,
                normalizerRegistry = normalizerRegistry,
                ruleId = ruleId
            )

            DECIMAL -> DecimalOperator.compile(ruleId = ruleId, cond = cond, fieldId = fieldId, op = op)

            INTEGER -> IntegerOperator.compile(ruleId = ruleId, cond = cond, fieldId = fieldId, op = op)

            STRING_SET -> compileStringSetCondition(
                cond = cond,
                fieldId = fieldId,
                def = def,
                op = op,
                normalizerRegistry = normalizerRegistry,
                ruleId = ruleId
            )

            BOOLEAN -> compileBooleanCondition(cond = cond, fieldId = fieldId, op = op, ruleId = ruleId)

            DATE, DATE_TIME -> DateOperator.compile(
                ruleId = ruleId,
                cond = cond,
                fieldId = fieldId,
                def = def,
                op = op
            )

            COLLECTION, OBJECT -> throw CompilationException(
                ruleId = ruleId,
                details = "Field type ${def.type} not supported in compiler yet"
            )
        }
    }

    @Suppress("LongParameterList")
    private fun compileTextCondition(
        cond: ConditionAst,
        fieldId: FieldId,
        def: FieldDefinition,
        op: String,
        normalizerRegistry: NormalizerRegistry,
        ruleId: String?
    ): CompiledExpression {
        return when (op) {
            OperatorNames.REGEX -> TextRegexOperator.compile(ruleId = ruleId, cond = cond, fieldId = fieldId)
            OperatorNames.IN -> TextInOperator.compile(
                ruleId = ruleId,
                cond = cond,
                fieldId = fieldId,
                def = def,
                registry = normalizerRegistry
            )

            else -> TextComparisonOperators.compile(
                ruleId = ruleId,
                op = op,
                cond = cond,
                fieldId = fieldId,
                def = def,
                registry = normalizerRegistry
            )
        }
    }

    /**
     * [op] is checked rather than assumed. A boolean accepts equality alone, and reading the operator
     * here means a schema that declared another one is refused instead of silently compiling to
     * equality — `Validator.validateDeclaredOperators` reports it first, so reaching this throw means
     * the rule was compiled without being validated.
     */
    private fun compileBooleanCondition(
        cond: ConditionAst,
        fieldId: FieldId,
        op: String,
        ruleId: String?
    ): CompiledExpression {
        if (op != OperatorNames.EQUALS) {
            throw CompilationException(
                ruleId = ruleId,
                details = "Unsupported operator '$op' for boolean field '${cond.field}'"
            )
        }
        val literal = cond.value as? BooleanLiteral ?: throw CompilationException(
            ruleId = ruleId,
            details = "Expected 'true' or 'false' for boolean field '${cond.field}'"
        )
        return BooleanEqualsExpression(field = fieldId, expected = literal.value)
    }

    /**
     * The operator is checked once, above the literal's shape.
     *
     * Both readings — a written-out list and a bare string — mean the same test over a set of one or
     * more expected values, so they must agree on which operators they accept. The check used to sit
     * inside the list arm alone, which let `containsAll "x"` compile as `containsAny`: harmless while
     * a set of one makes the two identical, and a silent wrong answer the moment they diverge.
     */
    @Suppress("ThrowsCount", "LongParameterList")
    private fun compileStringSetCondition(
        cond: ConditionAst,
        fieldId: FieldId,
        def: FieldDefinition,
        op: String,
        normalizerRegistry: NormalizerRegistry,
        ruleId: String?
    ): CompiledExpression {
        val expected = when (val conditionValue = cond.value) {
            is ListLiteral -> conditionValue.items.map {
                (it as? StringLiteral)?.value ?: throw CompilationException(
                    ruleId = ruleId,
                    details = "Expected string items in list"
                )
            }.toSet()

            is StringLiteral -> setOf(conditionValue.value)

            else -> throw CompilationException(
                ruleId = ruleId,
                details = "Expected list or string for string set field '${cond.field}'"
            )
        }
        val normalized = expected.mapTo(mutableSetOf()) { stringLiteral ->
            normalizerRegistry.applyAll(value = stringLiteral, normalizers = def.normalizers)
        }
        return when (op) {
            OperatorNames.CONTAINS_ANY -> StringSetContainsAnyExpression(
                field = fieldId,
                expectedNormalized = normalized,
                ignoreCase = cond.ignoreCase
            )

            OperatorNames.CONTAINS_ALL -> StringSetContainsAllExpression(
                field = fieldId,
                expectedNormalized = normalized,
                ignoreCase = cond.ignoreCase
            )

            else -> throw CompilationException(
                ruleId = ruleId,
                details = "Unsupported operator '$op' for string set field"
            )
        }
    }

}
