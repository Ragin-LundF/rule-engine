package ruleengine.compiler

import ruleengine.compiler.operators.TextComparisonOperators
import ruleengine.compiler.operators.TextInOperator
import ruleengine.compiler.operators.TextRegexOperator
import ruleengine.core.domain.FieldId
import ruleengine.core.domain.FieldSchema
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

object Compiler {

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
        return asts.map { compileRule(it, schema, normalizerRegistry) }
    }

    fun compileRule(
        ast: RuleAst,
        schema: FieldSchema,
        normalizerRegistry: NormalizerRegistry = NormalizerRegistry.default
    ): CompiledRule {
        val expr = compileExpression(ast.condition, schema, normalizerRegistry)
        val actions = ast.actions.map {
            ruleengine.core.domain.RuleAction(name = it.name, arguments = it.arguments.map { lit ->
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
            is AndAst -> AndExpression(expr.children.map { compileExpression(it, schema, normalizerRegistry) })
            is OrAst -> OrExpression(expr.children.map { compileExpression(it, schema, normalizerRegistry) })
            is NotAst -> NotExpression(compileExpression(expr.child, schema, normalizerRegistry))
            is ConditionAst -> compileCondition(expr, schema, normalizerRegistry)
            else -> throw CompilationException(astIdOrNull(expr), "Unknown expression type: ${expr.javaClass}")
        }
    }

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
        val fieldId = FieldId(cond.field)
        val def = schema.fields[fieldId]
        if (def == null) {
            throw CompilationException(ruleIdOrNull(cond), "Unknown field '${cond.field}'")
        }

        val op = normalizeOperator(cond.operator)

        return when (def.type) {
            ruleengine.core.domain.FieldType.TEXT -> compileTextCondition(
                cond = cond,
                fieldId = fieldId,
                def = def,
                op = op,
                normalizerRegistry = normalizerRegistry
            )

            ruleengine.core.domain.FieldType.DECIMAL -> compileDecimalCondition(
                cond = cond,
                fieldId = fieldId,
                op = op
            )

            ruleengine.core.domain.FieldType.INTEGER -> compileIntegerCondition(
                cond = cond,
                fieldId = fieldId,
                op = op
            )

            ruleengine.core.domain.FieldType.STRING_SET -> compileStringSetCondition(
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
        val ruleId = ruleIdOrNull(cond)

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

    private fun compileDecimalCondition(cond: ConditionAst, fieldId: FieldId, op: String): CompiledExpression {
        return ruleengine.compiler.operators.DecimalOperator.compile(ruleIdOrNull(cond), cond, fieldId)
    }

    private fun compileIntegerCondition(cond: ConditionAst, fieldId: FieldId, op: String): CompiledExpression {
        return ruleengine.compiler.operators.IntegerOperator.compile(ruleIdOrNull(cond), cond, fieldId)
    }

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
                val normalized = set.map { s -> applyNormalizers(s, def.normalizers, normalizerRegistry) }.toSet()
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
                        ruleIdOrNull(cond),
                        "Unsupported operator '$op' for string set field"
                    )
                }
            }

            is StringLiteral -> {
                val normalized = applyNormalizers(v.value, def.normalizers, normalizerRegistry)
                StringSetContainsAnyExpression(
                    field = fieldId,
                    expectedNormalized = setOf(normalized),
                    ignoreCase = cond.ignoreCase
                )
            }

            else -> throw CompilationException(
                ruleIdOrNull(cond),
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
        for (n in normalizers) v = registry.get(n).normalize(v)
        return v
    }

    private fun ruleIdOrNull(cond: ConditionAst): String? {
        return null
    }

}

