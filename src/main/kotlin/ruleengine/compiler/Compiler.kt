package ruleengine.compiler

import ruleengine.dsl.ast.*
import ruleengine.core.domain.FieldSchema
import ruleengine.core.domain.FieldId
import ruleengine.core.normalizer.NormalizerRegistry
import ruleengine.core.errors.CompilationException
import ruleengine.evaluator.compiled.*
import ruleengine.evaluator.CompiledRule
import java.math.BigDecimal

object Compiler {

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
        else -> op
    }

    fun compileRules(asts: List<RuleAst>, schema: FieldSchema, normalizerRegistry: NormalizerRegistry = NormalizerRegistry.default): List<CompiledRule> {
        return asts.map { compileRule(it, schema, normalizerRegistry) }
    }

    fun compileRule(ast: RuleAst, schema: FieldSchema, normalizerRegistry: NormalizerRegistry = NormalizerRegistry.default): CompiledRule {
        val expr = compileExpression(ast.condition, schema, normalizerRegistry)
        val actions = ast.actions.map { ruleengine.core.domain.RuleAction(name = it.name, arguments = it.arguments.map { lit -> when (lit) {
            is StringLiteral -> lit.value
            is NumberLiteral -> lit.value
            is ListLiteral -> lit.items.map { (it as? StringLiteral)?.value ?: it.toString() }
            else -> null
        } }) }
        return CompiledRule(id = ast.id, expression = expr, actions = actions)
    }

    private fun compileExpression(expr: ExpressionAst, schema: FieldSchema, normalizerRegistry: NormalizerRegistry): CompiledExpression {
        return when (expr) {
            is AndAst -> AndExpression(expr.children.map { compileExpression(it, schema, normalizerRegistry) })
            is OrAst -> OrExpression(expr.children.map { compileExpression(it, schema, normalizerRegistry) })
            is NotAst -> NotExpression(compileExpression(expr.child, schema, normalizerRegistry))
            is ConditionAst -> compileCondition(expr, schema, normalizerRegistry)
            else -> throw CompilationException(astIdOrNull(expr), "Unknown expression type: ${expr.javaClass}")
        }
    }

    private fun astIdOrNull(expr: Any): String? = when (expr) {
        is RuleAst -> expr.id
        else -> null
    }

    private fun compileCondition(cond: ConditionAst, schema: FieldSchema, normalizerRegistry: NormalizerRegistry): CompiledExpression {
        val fieldId = FieldId(cond.field)
        val def = schema.fields[fieldId] ?: throw CompilationException(ruleIdOrNull(cond), "Unknown field '${cond.field}'")
        val op = normalizeOperator(cond.operator)

        when (def.type) {
            ruleengine.core.domain.FieldType.TEXT -> {
                if (op == "in") {
                    when (val lit = cond.value) {
                        is ListLiteral -> {
                            val set = lit.items.map { (it as? StringLiteral)?.value ?: throw CompilationException(ruleIdOrNull(cond), "Expected string items in list") }.toSet()
                            // normalize
                            val normalized = set.map { s ->
                                var n = s
                                for (nn in def.normalizers) n = normalizerRegistry.get(nn).normalize(n)
                                n
                            }.toSet()
                            return TextInExpression(field = fieldId, expectedNormalized = normalized)
                        }
                        is StringLiteral -> {
                            var s = lit.value
                            for (nn in def.normalizers) s = normalizerRegistry.get(nn).normalize(s)
                            return TextInExpression(field = fieldId, expectedNormalized = setOf(s))
                        }
                        else -> throw CompilationException(ruleIdOrNull(cond), "Operator 'in' expects list or string literal for text field")
                    }
                } else {
                    val literal = cond.value as? StringLiteral ?: throw CompilationException(ruleIdOrNull(cond), "Expected string literal for text field '${cond.field}'")
                    var expected = literal.value
                    for (n in def.normalizers) expected = normalizerRegistry.get(n).normalize(expected)
                    return when (op) {
                        "equals" -> TextEqualsExpression(field = fieldId, expectedNormalized = expected)
                        "contains" -> TextContainsExpression(field = fieldId, expectedNormalized = expected)
                        "startsWith" -> TextStartsWithExpression(field = fieldId, expectedNormalized = expected)
                        "endsWith" -> TextEndsWithExpression(field = fieldId, expectedNormalized = expected)
                        else -> throw CompilationException(ruleIdOrNull(cond), "Unsupported operator '$op' for text field")
                    }
                }
            }
            ruleengine.core.domain.FieldType.DECIMAL -> {
                val literal = cond.value as? NumberLiteral ?: throw CompilationException(ruleIdOrNull(cond), "Expected numeric literal for decimal field '${cond.field}'")
                val expected = try { BigDecimal(literal.value) } catch (ex: Exception) { throw CompilationException(ruleIdOrNull(cond), "Invalid decimal literal: ${literal.value}") }
                return when (op) {
                    "equals" -> DecimalComparisonExpression(field = fieldId, expected = expected, op = ComparisonOperator.EQ)
                    "gt" -> DecimalComparisonExpression(field = fieldId, expected = expected, op = ComparisonOperator.GT)
                    "gte" -> DecimalComparisonExpression(field = fieldId, expected = expected, op = ComparisonOperator.GTE)
                    "lt" -> DecimalComparisonExpression(field = fieldId, expected = expected, op = ComparisonOperator.LT)
                    "lte" -> DecimalComparisonExpression(field = fieldId, expected = expected, op = ComparisonOperator.LTE)
                    else -> throw CompilationException(ruleIdOrNull(cond), "Unsupported operator '$op' for decimal field")
                }
            }
            ruleengine.core.domain.FieldType.INTEGER -> {
                val literal = cond.value as? NumberLiteral ?: throw CompilationException(ruleIdOrNull(cond), "Expected numeric literal for integer field '${cond.field}'")
                val expected = try { literal.value.toLong() } catch (ex: Exception) { throw CompilationException(ruleIdOrNull(cond), "Invalid integer literal: ${literal.value}") }
                return when (op) {
                    "equals" -> IntegerComparisonExpression(field = fieldId, expected = expected, op = IntegerComparisonOperator.EQ)
                    "gt" -> IntegerComparisonExpression(field = fieldId, expected = expected, op = IntegerComparisonOperator.GT)
                    "gte" -> IntegerComparisonExpression(field = fieldId, expected = expected, op = IntegerComparisonOperator.GTE)
                    "lt" -> IntegerComparisonExpression(field = fieldId, expected = expected, op = IntegerComparisonOperator.LT)
                    "lte" -> IntegerComparisonExpression(field = fieldId, expected = expected, op = IntegerComparisonOperator.LTE)
                    else -> throw CompilationException(ruleIdOrNull(cond), "Unsupported operator '$op' for integer field")
                }
            }
            ruleengine.core.domain.FieldType.STRING_SET -> {
                // expect list literal or string
                when (val v = cond.value) {
                    is ListLiteral -> {
                        val set = v.items.map { (it as? StringLiteral)?.value ?: throw CompilationException(ruleIdOrNull(cond), "Expected string items in list") }.toSet()
                        val normalized = set.map { s ->
                            var n = s
                            for (nn in def.normalizers) n = normalizerRegistry.get(nn).normalize(n)
                            n
                        }.toSet()
                        return when (op) {
                            "containsAny" -> StringSetContainsAnyExpression(field = fieldId, expectedNormalized = normalized)
                            "containsAll" -> StringSetContainsAllExpression(field = fieldId, expectedNormalized = normalized)
                            else -> throw CompilationException(ruleIdOrNull(cond), "Unsupported operator '$op' for string set field")
                        }
                    }
                    is StringLiteral -> {
                        val s = v.value
                        var n = s
                        for (nn in def.normalizers) n = normalizerRegistry.get(nn).normalize(n)
                        return StringSetContainsAnyExpression(field = fieldId, expectedNormalized = setOf(n))
                    }
                    else -> throw CompilationException(ruleIdOrNull(cond), "Expected list or string for string set field '${cond.field}'")
                }
            }
            else -> throw CompilationException(ruleIdOrNull(cond), "Field type ${def.type} not supported in compiler yet")
        }
    }

    private fun ruleIdOrNull(cond: ConditionAst): String? = null

}

