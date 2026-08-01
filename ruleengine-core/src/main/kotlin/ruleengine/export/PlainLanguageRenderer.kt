package ruleengine.export

import ruleengine.compiler.operators.OperatorUtils
import ruleengine.core.domain.FieldPathResolution
import ruleengine.core.domain.FieldPathResolver
import ruleengine.core.domain.OperatorNames
import ruleengine.core.domain.dto.FieldSchema
import ruleengine.core.domain.dto.FieldType
import ruleengine.core.domain.dto.isTemporal
import ruleengine.dsl.ast.AndAst
import ruleengine.dsl.ast.ArithmeticOperatorAst
import ruleengine.dsl.ast.ArithmeticValueAst
import ruleengine.dsl.ast.BetweenLiteral
import ruleengine.dsl.ast.BooleanLiteral
import ruleengine.dsl.ast.ComparisonExpressionAst
import ruleengine.dsl.ast.ComparisonOperatorAst
import ruleengine.dsl.ast.ConditionAst
import ruleengine.dsl.ast.ExpressionAst
import ruleengine.dsl.ast.ExtractionRefLiteral
import ruleengine.dsl.ast.FieldAccessAst
import ruleengine.dsl.ast.FieldSegmentAst
import ruleengine.dsl.ast.FilterSegmentAst
import ruleengine.dsl.ast.FunctionCallValueAst
import ruleengine.dsl.ast.ListLiteral
import ruleengine.dsl.ast.LiteralAst
import ruleengine.dsl.ast.LiteralValueAst
import ruleengine.dsl.ast.NotAst
import ruleengine.dsl.ast.NumberLiteral
import ruleengine.dsl.ast.OrAst
import ruleengine.dsl.ast.PathSegmentAst
import ruleengine.dsl.ast.StringLiteral
import ruleengine.dsl.ast.ValueExpressionAst
import ruleengine.evaluator.compiled.AggregateFunctionName
import ruleengine.export.dto.PlainAll
import ruleengine.export.dto.PlainAny
import ruleengine.export.dto.PlainCondition
import ruleengine.export.dto.PlainLeaf
import ruleengine.export.dto.PlainNot

/**
 * Restates a rule's condition as sentences, for a reader who has never seen the DSL.
 *
 * The counterpart to [ruleengine.dsl.ast.ValueExpressionRenderer], which reproduces the condition in
 * DSL syntax for someone who has. An export shows both: the sentence is what a business reader
 * checks, the DSL text is what their technical reviewer verifies against.
 *
 * The wording is deliberately literal. It never infers intent — `> total * 0.25` becomes "more than
 * … multiplied by 0.25", not "more than a quarter of …" — because a description that quietly
 * rephrases what a rule does is worse than one that reads a little mechanically. Saying what the
 * rule is *for* is the author's job, in the `description` clause.
 */
object PlainLanguageRenderer {

    /** Restates [expr] as a condition tree. [schema] supplies field aliases; null still works. */
    fun render(expr: ExpressionAst, schema: FieldSchema? = null): PlainCondition {
        return when (expr) {
            is ConditionAst -> PlainLeaf(text = renderCondition(condition = expr, schema = schema))
            is ComparisonExpressionAst -> PlainLeaf(text = renderComparison(comparison = expr, schema = schema))
            is NotAst -> PlainNot(child = render(expr = expr.child, schema = schema))
            is AndAst -> PlainAll(children = expr.children.map { child -> render(expr = child, schema = schema) })
            is OrAst -> PlainAny(children = expr.children.map { child -> render(expr = child, schema = schema) })
        }
    }

    // ── plain field-vs-literal conditions ─────────────────────────────────────

    private fun renderCondition(condition: ConditionAst, schema: FieldSchema?): String {
        val subject = FieldLabels.forPath(path = condition.field, schema = schema)
        val predicate = renderPredicate(
            operator = OperatorUtils.normalizeOperator(op = condition.operator),
            value = condition.value,
            type = typeOf(path = condition.field, schema = schema),
        )
        val suffix = if (condition.ignoreCase) ", ignoring capitalisation" else ""

        return "$subject $predicate$suffix"
    }

    private fun typeOf(path: String, schema: FieldSchema?): FieldType? {
        if (schema == null) {
            return null
        }

        val resolution = FieldPathResolver.resolve(identifier = path, schema = schema)

        return (resolution as? FieldPathResolution.Resolved)?.definition?.type
    }

    @Suppress("CyclomaticComplexMethod")
    private fun renderPredicate(operator: String, value: LiteralAst, type: FieldType?): String {
        if (type?.isTemporal == true) {
            val temporal = temporalPredicate(operator = operator, value = value, type = type)
            if (temporal != null) {
                return temporal
            }
        }

        return when (operator) {
            OperatorNames.EQUALS -> "is ${literal(value = value)}"
            OperatorNames.GT -> "is more than ${literal(value = value)}"
            OperatorNames.GTE -> "is at least ${literal(value = value)}"
            OperatorNames.LT -> "is less than ${literal(value = value)}"
            OperatorNames.LTE -> "is at most ${literal(value = value)}"
            OperatorNames.BETWEEN -> renderBetween(value = value)
            OperatorNames.CONTAINS -> "contains ${literal(value = value)}"
            OperatorNames.STARTS_WITH -> "starts with ${literal(value = value)}"
            OperatorNames.ENDS_WITH -> "ends with ${literal(value = value)}"
            OperatorNames.IN -> "is one of ${literal(value = value)}"
            OperatorNames.CONTAINS_ANY -> "includes at least one of ${literal(value = value)}"
            OperatorNames.CONTAINS_ALL -> "includes all of ${literal(value = value)}"
            OperatorNames.REGEX -> "matches the pattern ${literal(value = value)}"
            // An operator the engine knows but this renderer has no wording for still has to produce a
            // readable sentence rather than an empty one, so it falls back to its own name.
            else -> "$operator ${literal(value = value)}"
        }
    }

    /**
     * Time reads differently from quantity: a date is not "at least" another date, it is "on or
     * after" it. The engine has no separate before/after operators — `lt` *is* before and `gt` *is*
     * after — so the distinction exists only in the wording, and only a document ever shows it.
     *
     * Returns null for operators that need no special wording, leaving them to the general case.
     */
    private fun temporalPredicate(operator: String, value: LiteralAst, type: FieldType): String? {
        // A date compares by calendar day and a date_time by instant, so "on" and "at" are not
        // interchangeable: "is on 2024-06-15T09:30" would claim a precision the comparison lacks.
        val at = if (type == FieldType.DATE_TIME) "at" else "on"
        val moment = temporalLiteral(value = value)

        return when (operator) {
            OperatorNames.EQUALS -> "is $at $moment"
            OperatorNames.GT -> "is after $moment"
            OperatorNames.GTE -> "is $at or after $moment"
            OperatorNames.LT -> "is before $moment"
            OperatorNames.LTE -> "is $at or before $moment"
            else -> null
        }
    }

    /**
     * A date literal without its quotes.
     *
     * The DSL quotes dates because it has no date token, but the quotes are syntax, not meaning: a
     * reader sees a date, not a piece of text being matched. Dropping them also keeps `equals`
     * consistent with `between`, which reads its two bounds unquoted.
     */
    private fun temporalLiteral(value: LiteralAst): String {
        if (value is StringLiteral) {
            return value.value
        }

        return literal(value = value)
    }

    private fun renderBetween(value: LiteralAst): String {
        if (value !is BetweenLiteral) {
            return "is between ${literal(value = value)}"
        }

        return "is between ${value.low} and ${value.high}"
    }

    // ── expression comparisons (aggregates and arithmetic) ────────────────────

    private fun renderComparison(comparison: ComparisonExpressionAst, schema: FieldSchema?): String {
        val left = operand(expr = comparison.left, schema = schema)
        val right = operand(expr = comparison.right, schema = schema)
        val suffix = if (comparison.ignoreCase) ", ignoring capitalisation" else ""

        return "$left ${comparisonPhrase(operator = comparison.operator)} $right$suffix"
    }

    private fun comparisonPhrase(operator: ComparisonOperatorAst): String {
        return when (operator) {
            ComparisonOperatorAst.EQ -> "is"
            ComparisonOperatorAst.NEQ -> "is not"
            ComparisonOperatorAst.GT -> "is more than"
            ComparisonOperatorAst.GTE -> "is at least"
            ComparisonOperatorAst.LT -> "is less than"
            ComparisonOperatorAst.LTE -> "is at most"
        }
    }

    private fun operand(expr: ValueExpressionAst, schema: FieldSchema?): String {
        return when (expr) {
            is LiteralValueAst -> literal(value = expr.literal)
            is FieldAccessAst -> fieldOperand(path = expr.path, schema = schema)
            is FunctionCallValueAst -> aggregate(call = expr, schema = schema)
            is ArithmeticValueAst -> arithmetic(expr = expr, schema = schema)
        }
    }

    private fun arithmetic(expr: ArithmeticValueAst, schema: FieldSchema?): String {
        val left = operand(expr = expr.left, schema = schema)
        val right = operand(expr = expr.right, schema = schema)

        return "$left ${arithmeticPhrase(operator = expr.operator)} $right"
    }

    private fun arithmeticPhrase(operator: ArithmeticOperatorAst): String {
        return when (operator) {
            ArithmeticOperatorAst.ADD -> "plus"
            ArithmeticOperatorAst.SUBTRACT -> "minus"
            ArithmeticOperatorAst.MULTIPLY -> "multiplied by"
            ArithmeticOperatorAst.DIVIDE -> "divided by"
        }
    }

    // ── aggregates over a collection ──────────────────────────────────────────

    /**
     * `sum(parcels[category == "fragile"].weightKg)` becomes
     * "the total Weight Kg of parcels where Category is "fragile"".
     *
     * The argument is split into what is measured (the member after the collection) and what is
     * counted over (the collection itself, plus any filter), because the two land in different parts
     * of the sentence. `count(parcels[...])` measures nothing, so it reads "the number of parcels …".
     */
    private fun aggregate(call: FunctionCallValueAst, schema: FieldSchema?): String {
        val argument = call.arguments.singleOrNull()
        val function = AggregateFunctionName.entries
            .firstOrNull { candidate -> candidate.name.equals(other = call.name, ignoreCase = true) }

        if (argument !is FieldAccessAst) {
            val rendered = call.arguments.joinToString(separator = ", ") { arg ->
                operand(expr = arg, schema = schema)
            }
            return "${aggregatePhrase(function = function, name = call.name, measure = null)} ($rendered)"
        }

        val split = splitAggregatePath(path = argument.path)
        val measure = split.measure?.let { segments -> FieldLabels.forSegments(segments = segments) }
        val container = describeContainer(segments = split.containerSegments, filters = split.filters)

        return "${aggregatePhrase(function = function, name = call.name, measure = measure)} of $container"
    }

    /**
     * [name] is the function as the author spelled it, used only when it matches no known
     * [AggregateFunctionName] — the validator rejects that case, but the renderer also runs on rules
     * that were never validated, and a sentence naming the unknown function beats an empty one.
     */
    private fun aggregatePhrase(function: AggregateFunctionName?, name: String, measure: String?): String {
        val quantity = when (function) {
            AggregateFunctionName.COUNT -> "the number"
            AggregateFunctionName.SUM -> "the total"
            AggregateFunctionName.AVG -> "the average"
            AggregateFunctionName.MEDIAN -> "the median"
            AggregateFunctionName.MAX -> "the highest"
            AggregateFunctionName.MIN -> "the lowest"
            AggregateFunctionName.SUBTRACT -> "the difference of"
            null -> "the ${name.lowercase()}"
        }

        if (measure == null) {
            return quantity
        }

        // "the number Weight Kg" is not English. Every other quantity takes the measure as a noun
        // ("the total Weight Kg"), but a count counts occurrences of it, not the quantity itself.
        if (function == AggregateFunctionName.COUNT) {
            return "$quantity of $measure values"
        }

        return "$quantity $measure"
    }

    /**
     * A path inside an aggregate, cut where the measured member begins.
     *
     * [containerSegments] name the collection, [measure] the member read from each element (absent
     * for `count`), and [filters] the predicates that narrow the collection.
     */
    private data class AggregatePath(
        val containerSegments: List<String>,
        val measure: List<String>?,
        val filters: List<ExpressionAst>,
    )

    /**
     * Everything up to and including the last filter is the container; whatever follows is measured.
     *
     * A filter can only apply to a collection, so it marks the boundary exactly. With no filter the
     * split falls back to "last segment is the measure", which is what a bare `sum(parcels.weightKg)`
     * means.
     */
    private fun splitAggregatePath(path: List<PathSegmentAst>): AggregatePath {
        val filters = path.filterIsInstance<FilterSegmentAst>().map { segment -> segment.expression }
        val lastFilterIndex = path.indexOfLast { segment -> segment is FilterSegmentAst }

        if (lastFilterIndex >= 0) {
            val container = path.take(n = lastFilterIndex + 1).fieldNames()
            val measure = path.drop(n = lastFilterIndex + 1).fieldNames()
            return AggregatePath(
                containerSegments = container,
                measure = measure.ifEmpty { null },
                filters = filters,
            )
        }

        val names = path.fieldNames()
        if (names.size < 2) {
            return AggregatePath(containerSegments = names, measure = null, filters = filters)
        }

        return AggregatePath(
            containerSegments = names.dropLast(n = 1),
            measure = listOf(names.last()),
            filters = filters,
        )
    }

    /**
     * No schema is threaded in: a filter's condition is written relative to the element it filters
     * (`origin.hub` inside `parcels[...]` means a parcel's hub), so resolving it against the
     * top-level schema would look up the wrong field.
     */
    private fun describeContainer(segments: List<String>, filters: List<ExpressionAst>): String {
        // Left as written rather than title-cased: the container reads as a noun mid-sentence
        // ("of parcels where …"), where "of Parcels" would look like a proper name.
        val name = segments.joinToString(separator = ".").ifEmpty { "the collection" }
        if (filters.isEmpty()) {
            return name
        }

        val conditions = filters.joinToString(separator = " and ") { filter ->
            flatten(condition = render(expr = filter, schema = null))
        }

        return "$name where $conditions"
    }

    // ── field paths outside an aggregate ──────────────────────────────────────

    private fun fieldOperand(path: List<PathSegmentAst>, schema: FieldSchema?): String {
        val filters = path.filterIsInstance<FilterSegmentAst>().map { segment -> segment.expression }
        if (filters.isEmpty()) {
            return FieldLabels.forPath(path = path.fieldNames().joinToString(separator = "."), schema = schema)
        }

        val split = splitAggregatePath(path = path)
        val measure = split.measure?.let { segments -> FieldLabels.forSegments(segments = segments) }
        val container = describeContainer(segments = split.containerSegments, filters = split.filters)

        if (measure == null) {
            return container
        }

        return "$measure of $container"
    }

    // ── literals ──────────────────────────────────────────────────────────────

    private fun literal(value: LiteralAst): String {
        return when (value) {
            is StringLiteral -> "\"${value.value}\""
            is NumberLiteral -> value.value
            is BooleanLiteral -> value.value.toString()
            is BetweenLiteral -> "${value.low} and ${value.high}"
            is ListLiteral -> value.items.joinToString(separator = ", ") { item -> literal(value = item) }
            is ExtractionRefLiteral -> "the text captured by group ${value.groupIndex}"
        }
    }

    // ── helpers ───────────────────────────────────────────────────────────────

    private fun List<PathSegmentAst>.fieldNames(): List<String> {
        return filterIsInstance<FieldSegmentAst>().map { segment -> segment.name }
    }

    /**
     * Collapses a condition tree back to one line, for the inside of a filter.
     *
     * A filter predicate belongs in the middle of the sentence describing its collection, so it
     * cannot become its own bullet — but the engine already forbids `and` / `or` inside a filter, so
     * in practice there is only ever one leaf to collapse.
     */
    private fun flatten(condition: PlainCondition): String {
        return when (condition) {
            is PlainLeaf -> condition.text
            is PlainNot -> "not ${flatten(condition = condition.child)}"
            is PlainAll -> condition.children.joinToString(separator = " and ") { child ->
                flatten(condition = child)
            }

            is PlainAny -> condition.children.joinToString(separator = " or ") { child ->
                flatten(condition = child)
            }
        }
    }
}
