package ruleengine.compiler

import ruleengine.compiler.operators.DateOperator
import ruleengine.compiler.operators.DecimalOperator
import ruleengine.compiler.operators.IntegerOperator
import ruleengine.compiler.operators.OperatorUtils
import ruleengine.compiler.operators.TextComparisonOperators
import ruleengine.compiler.operators.TextInOperator
import ruleengine.compiler.operators.TextRegexOperator
import ruleengine.core.domain.FieldPathResolution
import ruleengine.core.domain.FieldPathResolver
import ruleengine.core.domain.OperatorNames
import ruleengine.core.domain.dto.FieldDefinition
import ruleengine.core.domain.dto.FieldId
import ruleengine.core.domain.dto.FieldSchema
import ruleengine.core.domain.dto.FieldType.BOOLEAN
import ruleengine.core.domain.dto.FieldType.COLLECTION
import ruleengine.core.domain.dto.FieldType.DATE
import ruleengine.core.domain.dto.FieldType.DATE_TIME
import ruleengine.core.domain.dto.FieldType.DECIMAL
import ruleengine.core.domain.dto.FieldType.INTEGER
import ruleengine.core.domain.dto.FieldType.OBJECT
import ruleengine.core.domain.dto.FieldType.STRING_SET
import ruleengine.core.domain.dto.FieldType.TEXT
import ruleengine.core.domain.dto.NormalizerId
import ruleengine.core.errors.CompilationException
import ruleengine.core.normalizer.NormalizerRegistry
import ruleengine.dsl.ast.ActionAst
import ruleengine.dsl.ast.AndAst
import ruleengine.dsl.ast.BooleanLiteral
import ruleengine.dsl.ast.ComparisonExpressionAst
import ruleengine.dsl.ast.ComparisonOperatorAst
import ruleengine.dsl.ast.ConditionAst
import ruleengine.dsl.ast.ExpressionAst
import ruleengine.dsl.ast.ExtractionAst
import ruleengine.dsl.ast.ExtractionRefLiteral
import ruleengine.dsl.ast.ListLiteral
import ruleengine.dsl.ast.LiteralAst
import ruleengine.dsl.ast.NotAst
import ruleengine.dsl.ast.NumberLiteral
import ruleengine.dsl.ast.OrAst
import ruleengine.dsl.ast.RuleAst
import ruleengine.dsl.ast.StringLiteral
import ruleengine.dsl.ast.ValueExpressionRenderer
import ruleengine.evaluator.CompiledAction
import ruleengine.evaluator.CompiledRule
import ruleengine.evaluator.compiled.AndExpression
import ruleengine.evaluator.compiled.BooleanEqualsExpression
import ruleengine.evaluator.compiled.ComparisonCompiledExpression
import ruleengine.evaluator.compiled.CompiledActionArgument
import ruleengine.evaluator.compiled.CompiledExpression
import ruleengine.evaluator.compiled.CompiledFieldSegment
import ruleengine.evaluator.compiled.CompiledValueExpression
import ruleengine.evaluator.compiled.EvaluationCost
import ruleengine.evaluator.compiled.FieldAccessCompiledValueExpression
import ruleengine.evaluator.compiled.LiteralCompiledValueExpression
import ruleengine.evaluator.compiled.NotExpression
import ruleengine.evaluator.compiled.NumberExpressionValue
import ruleengine.evaluator.compiled.OrExpression
import ruleengine.evaluator.compiled.RegexExtractExpression
import ruleengine.evaluator.compiled.StringSetContainsAllExpression
import ruleengine.evaluator.compiled.StringSetContainsAnyExpression
import ruleengine.evaluator.compiled.TextExpressionValue
import java.math.BigDecimal

/**
 * Turns validated [RuleAst]s into [CompiledRule]s.
 *
 * Every function that can fail takes the id of the rule being compiled and hands it to
 * [CompilationException], so a failure names the offending rule instead of `<unknown>`.
 */
@Suppress("TooManyFunctions")
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
            ruleId = ast.id
        )
        val compiledActions = ast.actions.map { action ->
            compileAction(action = action, schema = schema, ruleId = ast.id)
        }
        return CompiledRule(id = ast.id, expression = expr, actions = compiledActions)
    }

    private fun compileAction(action: ActionAst, schema: FieldSchema, ruleId: String?): CompiledAction {
        val compiledExtraction = action.extraction?.let { extraction ->
            compileExtraction(extraction = extraction, schema = schema, ruleId = ruleId)
        }

        val compiledArguments = action.arguments.map { literal ->
            if (literal is ExtractionRefLiteral) {
                val extraction = compiledExtraction ?: throw CompilationException(
                    ruleId = ruleId,
                    details = "Action '${action.name}' uses extraction reference but has no 'extract' clause"
                )
                CompiledActionArgument.ExtractionRef(extraction = extraction)
            } else {
                CompiledActionArgument.Static(
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

    private fun compileExpression(
        expr: ExpressionAst,
        schema: FieldSchema,
        normalizerRegistry: NormalizerRegistry,
        ruleId: String?
    ): CompiledExpression {
        return when (expr) {
            is AndAst -> AndExpression(children = expr.children.map {
                compileExpression(
                    expr = it,
                    schema = schema,
                    normalizerRegistry = normalizerRegistry,
                    ruleId = ruleId
                )
            })

            is OrAst -> OrExpression(children = expr.children.map {
                compileExpression(
                    expr = it,
                    schema = schema,
                    normalizerRegistry = normalizerRegistry,
                    ruleId = ruleId
                )
            })

            is NotAst -> NotExpression(
                child = compileExpression(
                    expr = expr.child,
                    schema = schema,
                    normalizerRegistry = normalizerRegistry,
                    ruleId = ruleId
                )
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

    private fun compileFilterExpression(
        expr: ExpressionAst,
        schema: FieldSchema,
        ruleId: String?
    ): CompiledExpression {
        return when (expr) {
            is ComparisonExpressionAst -> compileComparisonExpression(expr = expr, schema = schema, ruleId = ruleId)
            is ConditionAst -> compileFilterCondition(cond = expr, ruleId = ruleId)
            else -> throw CompilationException(
                ruleId = ruleId,
                details = "Only comparison expressions are supported in filter segments"
            )
        }
    }

    private fun compileFilterCondition(cond: ConditionAst, ruleId: String?): CompiledExpression {
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
            else -> throw CompilationException(
                ruleId = ruleId,
                details = "Operator '$op' is not supported in filter segments"
            )
        }
        val left = FieldAccessCompiledValueExpression(
            segments = listOf(CompiledFieldSegment(name = cond.field))
        )
        val right = compileLiteralValue(literal = cond.value, ruleId = ruleId)
        return ComparisonCompiledExpression(
            left = left,
            operator = comparisonOperator,
            right = right,
            cost = EvaluationCost.CHEAP
        )
    }

    private fun compileLiteralValue(literal: LiteralAst, ruleId: String?): CompiledValueExpression {
        return when (literal) {
            is NumberLiteral -> LiteralCompiledValueExpression(
                value = NumberExpressionValue(value = BigDecimal(literal.value))
            )

            is StringLiteral -> LiteralCompiledValueExpression(value = TextExpressionValue(value = literal.value))
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
        val left = ValueExpressionCompiler.compile(
            expr = expr.left,
            schema = schema,
            ruleId = ruleId,
            filterCompiler = filterCompiler
        )
        val right = ValueExpressionCompiler.compile(
            expr = expr.right,
            schema = schema,
            ruleId = ruleId,
            filterCompiler = filterCompiler
        )
        val cost = maxOf(left.cost, right.cost)
        return ComparisonCompiledExpression(
            left = left,
            operator = expr.operator,
            right = right,
            cost = cost,
            // Rendered from the AST because it is the last point where the author's text can still be
            // reconstructed: the compiled operands have already rewritten path roots alias → canonical.
            label = ValueExpressionRenderer.render(expr = expr.left)
        )
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

            BOOLEAN -> compileBooleanCondition(cond = cond, fieldId = fieldId, ruleId = ruleId)

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

    private fun compileBooleanCondition(
        cond: ConditionAst,
        fieldId: FieldId,
        ruleId: String?
    ): CompiledExpression {
        val literal = cond.value as? BooleanLiteral ?: throw CompilationException(
            ruleId = ruleId,
            details = "Expected 'true' or 'false' for boolean field '${cond.field}'"
        )
        return BooleanEqualsExpression(field = fieldId, expected = literal.value)
    }

    @Suppress("ThrowsCount", "LongParameterList")
    private fun compileStringSetCondition(
        cond: ConditionAst,
        fieldId: FieldId,
        def: FieldDefinition,
        op: String,
        normalizerRegistry: NormalizerRegistry,
        ruleId: String?
    ): CompiledExpression {
        return when (val conditionValue = cond.value) {
            is ListLiteral -> {
                val stringLiteralSet = conditionValue.items.map {
                    (it as? StringLiteral)?.value ?: throw CompilationException(
                        ruleId = ruleId,
                        details = "Expected string items in list"
                    )
                }.toSet()
                val normalized = stringLiteralSet.map { stringLiteral ->
                    applyNormalizers(
                        value = stringLiteral,
                        normalizers = def.normalizers,
                        registry = normalizerRegistry
                    )
                }.toSet()

                when (op) {
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

            is StringLiteral -> {
                val normalized = applyNormalizers(
                    value = conditionValue.value,
                    normalizers = def.normalizers,
                    registry = normalizerRegistry
                )
                StringSetContainsAnyExpression(
                    field = fieldId,
                    expectedNormalized = setOf(normalized),
                    ignoreCase = cond.ignoreCase
                )
            }

            else -> throw CompilationException(
                ruleId = ruleId,
                details = "Expected list or string for string set field '${cond.field}'"
            )
        }
    }

    private fun applyNormalizers(
        value: String,
        normalizers: List<NormalizerId>,
        registry: NormalizerRegistry
    ): String {
        var v = value
        for (n in normalizers) {
            v = registry.get(n).normalize(value = v)
        }
        return v
    }
}
