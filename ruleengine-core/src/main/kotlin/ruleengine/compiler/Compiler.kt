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
        "regex", "matches", "regexp" -> "regex"
        "between" -> "between"
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
                if (op == "regex") {
                    val literal = cond.value as? StringLiteral
                        ?: throw CompilationException(ruleIdOrNull(cond), "Operator 'regex' expects string literal pattern for field '${cond.field}'")
                    val options = if (cond.ignoreCase) setOf(RegexOption.IGNORE_CASE) else emptySet()
                    val pattern = try { Regex(literal.value, options) }
                        catch (ex: Exception) { throw CompilationException(ruleIdOrNull(cond), "Invalid regex pattern '${literal.value}': ${ex.message}") }
                    return TextRegexExpression(field = fieldId, pattern = pattern)
                }

                if (op == "in") {
                    when (val lit = cond.value) {
                        is ListLiteral -> {
                            val set = lit.items.map { (it as? StringLiteral)?.value ?: throw CompilationException(ruleIdOrNull(cond), "Expected string items in list") }.toSet()
                            val normalized = set.map { s -> applyNormalizers(s, def.normalizers, normalizerRegistry) }.toSet()
                            return TextInExpression(field = fieldId, expectedNormalized = normalized, ignoreCase = cond.ignoreCase)
                        }
                        is StringLiteral -> {
                            val normalized = applyNormalizers(lit.value, def.normalizers, normalizerRegistry)
                            return TextInExpression(field = fieldId, expectedNormalized = setOf(normalized), ignoreCase = cond.ignoreCase)
                        }
                        else -> throw CompilationException(ruleIdOrNull(cond), "Operator 'in' expects list or string literal for text field")
                    }
                }

                val literal = cond.value as? StringLiteral
                    ?: throw CompilationException(ruleIdOrNull(cond), "Expected string literal for text field '${cond.field}'")
                val expected = applyNormalizers(literal.value, def.normalizers, normalizerRegistry)
                return when (op) {
                    "equals"     -> TextEqualsExpression(field = fieldId, expectedNormalized = expected, ignoreCase = cond.ignoreCase)
                    "contains"   -> TextContainsExpression(field = fieldId, expectedNormalized = expected, ignoreCase = cond.ignoreCase)
                    "startsWith" -> TextStartsWithExpression(field = fieldId, expectedNormalized = expected, ignoreCase = cond.ignoreCase)
                    "endsWith"   -> TextEndsWithExpression(field = fieldId, expectedNormalized = expected, ignoreCase = cond.ignoreCase)
                    else -> throw CompilationException(ruleIdOrNull(cond), "Unsupported operator '$op' for text field")
                }
            }
            ruleengine.core.domain.FieldType.DECIMAL -> {
                if (op == "between") {
                    val between = cond.value as? BetweenLiteral
                        ?: throw CompilationException(ruleIdOrNull(cond), "Operator 'between' expects two numeric bounds for field '${cond.field}'")
                    val low  = try { java.math.BigDecimal(between.low)  } catch (ex: Exception) { throw CompilationException(ruleIdOrNull(cond), "Invalid lower bound: ${between.low}") }
                    val high = try { java.math.BigDecimal(between.high) } catch (ex: Exception) { throw CompilationException(ruleIdOrNull(cond), "Invalid upper bound: ${between.high}") }
                    return DecimalBetweenExpression(field = fieldId, low = low, high = high)
                }
                val literal = cond.value as? NumberLiteral
                    ?: throw CompilationException(ruleIdOrNull(cond), "Expected numeric literal for decimal field '${cond.field}'")
                val expected = try { java.math.BigDecimal(literal.value) } catch (ex: Exception) { throw CompilationException(ruleIdOrNull(cond), "Invalid decimal literal: ${literal.value}") }
                return when (op) {
                    "equals" -> DecimalComparisonExpression(field = fieldId, expected = expected, op = ComparisonOperator.EQ)
                    "gt"     -> DecimalComparisonExpression(field = fieldId, expected = expected, op = ComparisonOperator.GT)
                    "gte"    -> DecimalComparisonExpression(field = fieldId, expected = expected, op = ComparisonOperator.GTE)
                    "lt"     -> DecimalComparisonExpression(field = fieldId, expected = expected, op = ComparisonOperator.LT)
                    "lte"    -> DecimalComparisonExpression(field = fieldId, expected = expected, op = ComparisonOperator.LTE)
                    else -> throw CompilationException(ruleIdOrNull(cond), "Unsupported operator '$op' for decimal field")
                }
            }
            ruleengine.core.domain.FieldType.INTEGER -> {
                if (op == "between") {
                    val between = cond.value as? BetweenLiteral
                        ?: throw CompilationException(ruleIdOrNull(cond), "Operator 'between' expects two integer bounds for field '${cond.field}'")
                    val low  = try { between.low.toLong()  } catch (ex: Exception) { throw CompilationException(ruleIdOrNull(cond), "Invalid lower bound: ${between.low}") }
                    val high = try { between.high.toLong() } catch (ex: Exception) { throw CompilationException(ruleIdOrNull(cond), "Invalid upper bound: ${between.high}") }
                    return IntegerBetweenExpression(field = fieldId, low = low, high = high)
                }
                val literal = cond.value as? NumberLiteral
                    ?: throw CompilationException(ruleIdOrNull(cond), "Expected numeric literal for integer field '${cond.field}'")
                val expected = try { literal.value.toLong() } catch (ex: Exception) { throw CompilationException(ruleIdOrNull(cond), "Invalid integer literal: ${literal.value}") }
                return when (op) {
                    "equals" -> IntegerComparisonExpression(field = fieldId, expected = expected, op = IntegerComparisonOperator.EQ)
                    "gt"     -> IntegerComparisonExpression(field = fieldId, expected = expected, op = IntegerComparisonOperator.GT)
                    "gte"    -> IntegerComparisonExpression(field = fieldId, expected = expected, op = IntegerComparisonOperator.GTE)
                    "lt"     -> IntegerComparisonExpression(field = fieldId, expected = expected, op = IntegerComparisonOperator.LT)
                    "lte"    -> IntegerComparisonExpression(field = fieldId, expected = expected, op = IntegerComparisonOperator.LTE)
                    else -> throw CompilationException(ruleIdOrNull(cond), "Unsupported operator '$op' for integer field")
                }
            }
            ruleengine.core.domain.FieldType.STRING_SET -> {
                when (val v = cond.value) {
                    is ListLiteral -> {
                        val set = v.items.map { (it as? StringLiteral)?.value ?: throw CompilationException(ruleIdOrNull(cond), "Expected string items in list") }.toSet()
                        val normalized = set.map { s -> applyNormalizers(s, def.normalizers, normalizerRegistry) }.toSet()
                        return when (op) {
                            "containsAny" -> StringSetContainsAnyExpression(field = fieldId, expectedNormalized = normalized, ignoreCase = cond.ignoreCase)
                            "containsAll" -> StringSetContainsAllExpression(field = fieldId, expectedNormalized = normalized, ignoreCase = cond.ignoreCase)
                            else -> throw CompilationException(ruleIdOrNull(cond), "Unsupported operator '$op' for string set field")
                        }
                    }
                    is StringLiteral -> {
                        val normalized = applyNormalizers(v.value, def.normalizers, normalizerRegistry)
                        return StringSetContainsAnyExpression(field = fieldId, expectedNormalized = setOf(normalized), ignoreCase = cond.ignoreCase)
                    }
                    else -> throw CompilationException(ruleIdOrNull(cond), "Expected list or string for string set field '${cond.field}'")
                }
            }
            else -> throw CompilationException(ruleIdOrNull(cond), "Field type ${def.type} not supported in compiler yet")
        }
    }

    private fun applyNormalizers(value: String, normalizers: List<ruleengine.core.domain.NormalizerId>, registry: NormalizerRegistry): String {
        var v = value
        for (n in normalizers) v = registry.get(n).normalize(v)
        return v
    }

    private fun ruleIdOrNull(cond: ConditionAst): String? = null

}

