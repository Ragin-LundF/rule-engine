package ruleengine.compiler

import ruleengine.compiler.operators.DecimalOperator
import ruleengine.compiler.operators.IntegerOperator
import ruleengine.compiler.operators.OperatorUtils
import ruleengine.compiler.operators.TextComparisonOperators
import ruleengine.compiler.operators.TextInOperator
import ruleengine.compiler.operators.TextRegexOperator
import ruleengine.core.domain.FieldDefinition
import ruleengine.core.domain.FieldId
import ruleengine.core.domain.FieldSchema
import ruleengine.core.domain.FieldType.DECIMAL
import ruleengine.core.domain.FieldType.INTEGER
import ruleengine.core.domain.FieldType.STRING_SET
import ruleengine.core.domain.FieldType.TEXT
import ruleengine.core.errors.CompilationException
import ruleengine.core.normalizer.NormalizerRegistry
import ruleengine.dsl.ast.ActionAst
import ruleengine.dsl.ast.AndAst
import ruleengine.dsl.ast.ComparisonExpressionAst
import ruleengine.dsl.ast.ComparisonOperatorAst
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
import ruleengine.evaluator.CompiledAction
import ruleengine.evaluator.CompiledRule
import ruleengine.evaluator.compiled.AndExpression
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
        val expr = compileExpression(expr = ast.condition, schema = schema, normalizerRegistry = normalizerRegistry)
        val compiledActions = ast.actions.map { action ->
            compileAction(action = action, schema = schema)
        }
        return CompiledRule(id = ast.id, expression = expr, actions = compiledActions)
    }

    private fun compileAction(action: ActionAst, schema: FieldSchema): CompiledAction {
        val compiledExtraction = action.extraction?.let { extraction ->
            compileExtraction(extraction = extraction, schema = schema)
        }

        val compiledArguments = action.arguments.map { literal ->
            when {
                literal is ExtractionRefLiteral -> {
                    if (compiledExtraction == null) {
                        throw CompilationException(
                            ruleId = null,
                            details = "Action '${action.name}' uses extraction reference but has no 'extract' clause"
                        )
                    }
                    CompiledActionArgument.ExtractionRef(extraction = compiledExtraction)
                }

                literal is StringLiteral -> CompiledActionArgument.Static(value = literal.value)
                literal is NumberLiteral -> CompiledActionArgument.Static(value = literal.value)
                literal is ListLiteral -> CompiledActionArgument.Static(
                    value = literal.items.map { (it as? StringLiteral)?.value ?: it.toString() }
                )

                else -> CompiledActionArgument.Static(value = null)
            }
        }

        return CompiledAction(name = action.name, arguments = compiledArguments)
    }

    private fun compileExtraction(extraction: ExtractionAst, schema: FieldSchema): RegexExtractExpression {
        return when (extraction) {
            is ExtractionAst.RegexExtraction -> {
                val fieldId = FieldId(value = extraction.sourceField)
                if (schema.fields[fieldId] == null) {
                    throw CompilationException(
                        ruleId = null,
                        details = "Extraction references unknown field '${extraction.sourceField}'"
                    )
                }
                val compiledPattern = runCatching {
                    Regex(pattern = extraction.pattern)
                }.getOrElse { cause ->
                    throw CompilationException(
                        ruleId = null,
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
        normalizerRegistry: NormalizerRegistry
    ): CompiledExpression {
        return when (expr) {
            is AndAst -> AndExpression(children = expr.children.map {
                compileExpression(
                    expr = it,
                    schema = schema,
                    normalizerRegistry = normalizerRegistry
                )
            })

            is OrAst -> OrExpression(children = expr.children.map {
                compileExpression(
                    expr = it,
                    schema = schema,
                    normalizerRegistry = normalizerRegistry
                )
            })

            is NotAst -> NotExpression(
                child = compileExpression(
                    expr = expr.child,
                    schema = schema,
                    normalizerRegistry = normalizerRegistry
                )
            )

            is ConditionAst -> compileCondition(cond = expr, schema = schema, normalizerRegistry = normalizerRegistry)

            is ComparisonExpressionAst -> compileComparisonExpression(expr = expr, schema = schema)
        }
    }

    internal fun compileFilterExpression(
        expr: ExpressionAst,
        schema: FieldSchema
    ): CompiledExpression {
        return when (expr) {
            is ComparisonExpressionAst -> compileComparisonExpression(expr = expr, schema = schema)
            is ConditionAst -> compileFilterCondition(cond = expr)
            else -> throw CompilationException(
                ruleId = null,
                details = "Only comparison expressions are supported in filter segments"
            )
        }
    }

    private fun compileFilterCondition(cond: ConditionAst): CompiledExpression {
        val op = OperatorUtils.normalizeOperator(op = cond.operator)
        val comparisonOperator = when (op) {
            "==" -> ComparisonOperatorAst.EQ
            "!=" -> ComparisonOperatorAst.NEQ
            ">", "gt", "greater_than" -> ComparisonOperatorAst.GT
            ">=", "gte", "greater_than_or_equal" -> ComparisonOperatorAst.GTE
            "<", "lt", "less_than" -> ComparisonOperatorAst.LT
            "<=", "lte", "less_than_or_equal" -> ComparisonOperatorAst.LTE
            else -> throw CompilationException(
                ruleId = null,
                details = "Operator '$op' is not supported in filter segments"
            )
        }
        val left = FieldAccessCompiledValueExpression(
            segments = listOf(CompiledFieldSegment(name = cond.field))
        )
        val right = compileLiteralValue(literal = cond.value)
        return ComparisonCompiledExpression(
            left = left,
            operator = comparisonOperator,
            right = right,
            cost = EvaluationCost.CHEAP
        )
    }

    private fun compileLiteralValue(literal: ruleengine.dsl.ast.LiteralAst): CompiledValueExpression {
        return when (literal) {
            is NumberLiteral -> LiteralCompiledValueExpression(
                value = NumberExpressionValue(
                    value = java.math.BigDecimal(
                        literal.value
                    )
                )
            )

            is StringLiteral -> LiteralCompiledValueExpression(value = TextExpressionValue(value = literal.value))
            else -> throw CompilationException(
                ruleId = null,
                details = "Unsupported literal type in filter: ${literal::class.simpleName}"
            )
        }
    }

    private fun compileComparisonExpression(
        expr: ComparisonExpressionAst,
        schema: FieldSchema
    ): CompiledExpression {
        val left = ValueExpressionCompiler.compile(
            expr = expr.left,
            schema = schema,
            filterCompiler = ::compileFilterExpression
        )
        val right = ValueExpressionCompiler.compile(
            expr = expr.right,
            schema = schema,
            filterCompiler = ::compileFilterExpression
        )
        val cost = maxOf(left.cost, right.cost)
        return ComparisonCompiledExpression(
            left = left,
            operator = expr.operator,
            right = right,
            cost = cost
        )
    }

    private fun compileCondition(
        cond: ConditionAst,
        schema: FieldSchema,
        normalizerRegistry: NormalizerRegistry
    ): CompiledExpression {
        val resolvedId = resolveIdentifier(
            identifier = cond.field,
            schema = schema
        )
        val fieldId = FieldId(value = resolvedId)
        val def = schema.fields[fieldId] ?: throw CompilationException(
            ruleId = ruleIdOrNull(cond = cond),
            details = "Unknown field '${cond.field}'"
        )

        val op = OperatorUtils.normalizeOperator(op = cond.operator)

        return when (def.type) {
            TEXT -> compileTextCondition(
                cond = cond,
                fieldId = fieldId,
                def = def,
                op = op,
                normalizerRegistry = normalizerRegistry
            )

            DECIMAL -> compileDecimalCondition(
                cond = cond,
                fieldId = fieldId,
                op = op
            )

            INTEGER -> compileIntegerCondition(
                cond = cond,
                fieldId = fieldId,
                op = op
            )

            STRING_SET -> compileStringSetCondition(
                cond = cond,
                fieldId = fieldId,
                def = def,
                op = op,
                normalizerRegistry = normalizerRegistry
            )

            else -> throw CompilationException(
                ruleId = ruleIdOrNull(cond = cond),
                details = "Field type ${def.type} not supported in compiler yet"
            )
        }
    }

    private fun compileTextCondition(
        cond: ConditionAst,
        fieldId: FieldId,
        def: FieldDefinition,
        op: String,
        normalizerRegistry: NormalizerRegistry
    ): CompiledExpression {
        val ruleId = ruleIdOrNull(cond = cond)

        return when (op) {
            "regex" -> TextRegexOperator.compile(ruleId = ruleId, cond = cond, fieldId = fieldId)
            "in" -> TextInOperator.compile(
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

    @Suppress("UnusedParameter")
    private fun compileDecimalCondition(cond: ConditionAst, fieldId: FieldId, op: String): CompiledExpression {
        return DecimalOperator.compile(
            ruleId = ruleIdOrNull(cond = cond),
            cond = cond,
            fieldId = fieldId
        )
    }

    @Suppress("UnusedParameter")
    private fun compileIntegerCondition(cond: ConditionAst, fieldId: FieldId, op: String): CompiledExpression {
        return IntegerOperator.compile(ruleId = ruleIdOrNull(cond = cond), cond = cond, fieldId = fieldId)
    }

    @Suppress("ThrowsCount")
    private fun compileStringSetCondition(
        cond: ConditionAst,
        fieldId: FieldId,
        def: FieldDefinition,
        op: String,
        normalizerRegistry: NormalizerRegistry
    ): CompiledExpression {
        return when (val conditionValue = cond.value) {
            is ListLiteral -> {
                val stringLiteralSet = conditionValue.items.map {
                    (it as? StringLiteral)?.value ?: throw CompilationException(
                        ruleId = ruleIdOrNull(cond = cond),
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
                    "containsAny" -> StringSetContainsAnyExpression(
                        field = fieldId,
                        expectedNormalized = normalized,
                        ignoreCase = cond.ignoreCase
                    )

                    "containsAll" -> StringSetContainsAllExpression(
                        field = fieldId,
                        expectedNormalized = normalized,
                        ignoreCase = cond.ignoreCase
                    )

                    else -> throw CompilationException(
                        ruleIdOrNull(cond = cond),
                        "Unsupported operator '$op' for string set field"
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
                ruleIdOrNull(cond = cond),
                "Expected list or string for string set field '${cond.field}'"
            )
        }
    }

    private fun applyNormalizers(
        value: String,
        normalizers: List<ruleengine.core.domain.NormalizerId>,
        registry: NormalizerRegistry
    ): String {
        var v = value
        for (n in normalizers) {
            v = registry.get(n).normalize(value = v)
        }
        return v
    }

    @Suppress("FunctionOnlyReturningConstant", "UnusedParameter")
    private fun ruleIdOrNull(cond: ConditionAst): String? {
        return null
    }

    /**
     * Resolves a field identifier from user input to the canonical field ID.
     * Checks the field ID first, then falls back to alias matching.
     */
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
