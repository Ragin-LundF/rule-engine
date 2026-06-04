package ruleengine.compiler

import ruleengine.compiler.operators.DecimalOperator
import ruleengine.compiler.operators.IntegerOperator
import ruleengine.compiler.operators.TextComparisonOperators
import ruleengine.compiler.operators.TextInOperator
import ruleengine.compiler.operators.TextRegexOperator
import ruleengine.core.domain.FieldId
import ruleengine.core.domain.FieldSchema
import ruleengine.core.domain.FieldType.DECIMAL
import ruleengine.core.domain.FieldType.INTEGER
import ruleengine.core.domain.FieldType.STRING_SET
import ruleengine.core.domain.FieldType.TEXT
import ruleengine.core.domain.RuleAction
import ruleengine.core.errors.CompilationException
import ruleengine.core.normalizer.NormalizerRegistry
import ruleengine.dsl.ast.AndAst
import ruleengine.dsl.ast.ConditionAst
import ruleengine.dsl.ast.ExpressionAst
import ruleengine.dsl.ast.ListLiteral
import ruleengine.dsl.ast.NotAst
import ruleengine.dsl.ast.NumberLiteral
import ruleengine.dsl.ast.OrAst
import ruleengine.dsl.ast.RuleAst
import ruleengine.dsl.ast.StringLiteral
import ruleengine.evaluator.CompiledRule
import ruleengine.evaluator.compiled.AndExpression
import ruleengine.evaluator.compiled.CompiledExpression
import ruleengine.evaluator.compiled.NotExpression
import ruleengine.evaluator.compiled.OrExpression
import ruleengine.evaluator.compiled.StringSetContainsAllExpression
import ruleengine.evaluator.compiled.StringSetContainsAnyExpression

@Suppress("TooManyFunctions")
object Compiler {

    @Suppress("CyclomaticComplexMethod")
    private fun normalizeOperator(op: String): String {
        return when (op.lowercase()) {
            "==", "equals" -> "equals"
            "=", "eq" -> "equals"
            ">=", "gte" -> "gte"
            ">", "gt" -> "gt"
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
    }

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
        val actions = ast.actions.map { action ->
            RuleAction(name = action.name, arguments = action.arguments.map { lit ->
                when (lit) {
                    is StringLiteral -> lit.value
                    is NumberLiteral -> lit.value
                    is ListLiteral -> lit.items.map { (it as? StringLiteral)?.value ?: it.toString() }
                    else -> null
                }
            })
        }
        return CompiledRule(id = ast.id, expression = expr, actions = actions)
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
        }
    }

    @Suppress("UnusedPrivateMember")
    private fun astIdOrNull(expr: Any): String? {
        return when (expr) {
            is RuleAst -> expr.id
            else -> null
        }
    }

    private fun compileCondition(
        cond: ConditionAst,
        schema: FieldSchema,
        normalizerRegistry: NormalizerRegistry
    ): CompiledExpression {
        val fieldId = FieldId(value = cond.field)
        val def = schema.fields[fieldId] ?: throw CompilationException(
            ruleId = ruleIdOrNull(cond),
            details = "Unknown field '${cond.field}'"
        )

        val op = normalizeOperator(op = cond.operator)

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
                ruleIdOrNull(cond = cond),
                "Field type ${def.type} not supported in compiler yet"
            )
        }
    }

    private fun compileTextCondition(
        cond: ConditionAst,
        fieldId: FieldId,
        def: ruleengine.core.domain.FieldDefinition,
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
        def: ruleengine.core.domain.FieldDefinition,
        op: String,
        normalizerRegistry: NormalizerRegistry
    ): CompiledExpression {
        return when (val v = cond.value) {
            is ListLiteral -> {
                val set = v.items.map {
                    (it as? StringLiteral)?.value ?: throw CompilationException(
                        ruleIdOrNull(cond),
                        "Expected string items in list"
                    )
                }.toSet()
                val normalized = set.map { s ->
                    applyNormalizers(
                        value = s,
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
                    value = v.value,
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
}

