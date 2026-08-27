package ruleengine.evaluator.compiled.value

import ruleengine.core.domain.dto.NormalizerId
import ruleengine.core.domain.dto.field.FieldId
import ruleengine.evaluator.compiled.EvaluationCost
import ruleengine.evaluator.compiled.value.path.CompiledFieldSegment
import ruleengine.evaluator.compiled.value.path.CompiledFilterSegment
import ruleengine.evaluator.compiled.value.path.CompiledPathSegment
import ruleengine.evaluator.compiled.value.path.CompiledSliceSegment
import ruleengine.evaluator.compiled.value.path.CompiledSortSegment
import ruleengine.evaluator.compiled.value.result.ArrayExpressionValue
import ruleengine.evaluator.compiled.value.result.BooleanExpressionValue
import ruleengine.evaluator.compiled.value.result.DateExpressionValue
import ruleengine.evaluator.compiled.value.result.ExpressionValue
import ruleengine.evaluator.compiled.value.result.ExpressionValues
import ruleengine.evaluator.compiled.value.result.MissingExpressionValue
import ruleengine.evaluator.compiled.value.result.NumberExpressionValue
import ruleengine.evaluator.compiled.value.result.ObjectExpressionValue
import ruleengine.evaluator.compiled.value.result.TextExpressionValue
import ruleengine.evaluator.context.PreparedRuleContext
import ruleengine.evaluator.context.dto.PreparedBoolean
import ruleengine.evaluator.context.dto.PreparedDate
import ruleengine.evaluator.context.dto.PreparedDateTime
import ruleengine.evaluator.context.dto.PreparedDecimal
import ruleengine.evaluator.context.dto.PreparedInteger
import ruleengine.evaluator.context.dto.PreparedStringSet
import ruleengine.evaluator.context.dto.PreparedText
import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneOffset

/**
 * Reads a field path — plain, dotted, filtered, or projected across a collection.
 *
 * @param normalizers Declared on the field the path ends at, applied to every text value the path
 *   produces. `PreparedRuleContext` normalises only what it prepares and deliberately prepares no
 *   collection member, so without this `invoices[...].customerId` would compare raw text while a
 *   top-level `customerId` compares normalised.
 *
 *   Readable because `Compiler` matches a literal on the other side of a comparison under the same
 *   list. Taking it from the compiled node reuses the path walk that produced it rather than
 *   repeating that walk — and it was two walks disagreeing that made `status == "PAID"` and
 *   `status equals "PAID"` answer differently.
 */
class FieldAccessCompiledValueExpression(
    private val segments: List<CompiledPathSegment>,
    val normalizers: List<NormalizerId> = emptyList(),
    /**
     * True when the path is collection-valued by its declared shape — it passes through a
     * `collection`, ends at one or at a `string_set`, or carries a filter, slice or sort.
     *
     * Decided at compile time because the runtime answer is not trustworthy: [collapse] reduces a
     * selection of exactly one element to a scalar, so a path that yields many values for one record
     * and one value for the next would otherwise change what `contains` means between them.
     */
    val yieldsCollection: Boolean = false
) : CompiledValueExpression {
    /**
     * Reading a path is cheap; ordering one is not. A sorting path is declared expensive so
     * `AndExpression`'s cost-ordered evaluation leaves it until a cheaper condition has had its
     * chance to decide the rule. Filtering stays cheap deliberately — reclassifying it would reorder
     * conditions in rules that exist today.
     */
    override val cost: EvaluationCost = if (segments.any { segment -> segment is CompiledSortSegment }) {
        EvaluationCost.EXPENSIVE
    } else {
        EvaluationCost.CHEAP
    }

    override fun evaluate(context: PreparedRuleContext): ExpressionValue {
        // A path of plain names may name a prepared value, including a dotted one: a scalar declared
        // inside an `object` is prepared under its whole path. Reading it from there is what applies
        // the field's declared `format` and normalizers — neither is recoverable from the raw input.
        val declaredName = declaredPathName()
        if (declaredName != null) {
            val prepared = preparedValue(name = declaredName, context = context)
            if (prepared != null) {
                return prepared
            }
        }
        val single = segments.singleOrNull() as? CompiledFieldSegment
        if (single != null) {
            // A whole collection read by name stays one array, even when it holds a single element:
            // collapsing it would turn `tags contains "x"` into a text comparison.
            val raw = context.rawContext.getRaw(fieldPath = listOf(single.name))
            return rawToExpressionValue(raw = raw, context = context)
        }
        // `resolveRawListOrNull` rather than `resolveRawList`, so that a collection the record does
        // not carry stays distinguishable from one it carries holding nothing. Collapsing both to an
        // empty list is what used to make `sum(orders.amount)` undecided for `orders: []`.
        val elements = resolveRawListOrNull(context = context) ?: return MissingExpressionValue
        return collapse(values = elements, context = context)
    }

    /** The dotted field id this path spells out, or null when it filters or projects on the way. */
    private fun declaredPathName(): String? {
        if (segments.any { segment -> segment !is CompiledFieldSegment }) {
            return null
        }
        return segments.joinToString(separator = ".") { segment -> (segment as CompiledFieldSegment).name }
    }

    /**
     * Every raw element the path selects, before any of it is wrapped in an [ExpressionValue].
     *
     * Slicing, collection predicates and keyed joins need the input elements themselves — a key and
     * its value live on the same element, and a projection has already separated them. One shared
     * walk is what keeps those features from each growing their own copy of path resolution.
     */
    fun resolveRawList(context: PreparedRuleContext): List<Any?> {
        return resolveRawListOrNull(context = context).orEmpty()
    }

    /**
     * The same walk as [resolveRawList], but `null` when the record carries nothing at the root.
     *
     * [resolveRawList] answers `emptyList()` for both an absent root and a root holding no elements,
     * which is the right reading for a caller that only wants to iterate what is there — a slice or a
     * keyed join has nothing to work on either way.
     *
     * Everything that has to *answer* for the difference reads this instead: [evaluate], so an empty
     * collection becomes an empty array rather than a missing value; `isEmpty`; and the collection
     * predicates, which are undecided over a collection that never arrived and vacuously decided over
     * one that arrived empty.
     */
    fun resolveRawListOrNull(context: PreparedRuleContext): List<Any?>? {
        val rootName = (segments[0] as? CompiledFieldSegment)?.name ?: return null
        val rootRaw = context.rawContext.getRaw(fieldPath = listOf(rootName)) ?: return null
        var current = asList(raw = rootRaw)
        for (index in 1 until segments.size) {
            current = applySegment(segment = segments[index], current = current, context = context)
        }
        return current
    }

    /**
     * The prepared value for a single-segment path, or null when the path has to be read raw.
     *
     * A prepared value is already typed and normalised against the schema, so reading one is both
     * cheaper and more faithful than re-deriving it from the input map.
     */
    private fun preparedValue(name: String, context: PreparedRuleContext): ExpressionValue? {
        return when (val prepared = context.get(field = FieldId(value = name))) {
            is PreparedInteger -> NumberExpressionValue(value = BigDecimal.valueOf(prepared.value))
            is PreparedDecimal -> NumberExpressionValue(value = prepared.value)
            is PreparedText -> TextExpressionValue(value = prepared.normalized)
            is PreparedBoolean -> BooleanExpressionValue(value = prepared.value)
            is PreparedStringSet -> ArrayExpressionValue(
                values = prepared.normalized.map { entry -> TextExpressionValue(value = entry) }
            )

            // The prepared value is the only place the field's declared `format` was applied, so a
            // date written as `dd.MM.yyyy` is only readable as a date from here.
            is PreparedDate -> DateExpressionValue(value = prepared.value)
            is PreparedDateTime -> DateExpressionValue(value = prepared.value.toLocalDate())

            else -> null
        }
    }

    private fun applySegment(
        segment: CompiledPathSegment,
        current: List<Any?>,
        context: PreparedRuleContext
    ): List<Any?> {
        return when (segment) {
            is CompiledFieldSegment -> project(current = current, name = segment.name)
            is CompiledFilterSegment -> current.filter { element ->
                element is Map<*, *> &&
                    segment.expression.evaluate(context = context.child(element = element), trace = null)
                        .isTrue()
            }
            // `take`/`takeLast` on the raw list, before conversion: a collection shorter than the
            // slice simply yields what it has, which is what makes the empty case need no guard.
            is CompiledSliceSegment -> if (segment.fromEnd) {
                current.takeLast(n = segment.count)
            } else {
                current.take(n = segment.count)
            }
            // Ordered on the raw list too, and for the same reason: `take(sortBy(orders, "total",
            // desc), 3)` should order raw elements and convert three of them, not convert every
            // order and discard most of the work.
            is CompiledSortSegment -> sort(segment = segment, current = current, context = context)
        }
    }

    /**
     * Puts the selected elements in order, by the segment's member or by the elements themselves.
     *
     * The key goes through [rawToExpressionValue] rather than being compared raw, which is what
     * applies the field's declared normalizers and reads a `LocalDate` / `Instant` as a date — so a
     * sort orders values the same way a comparison on the same field would.
     *
     * Stable, so equal keys keep their source order and a following slice is deterministic.
     * Elements with no orderable key — an absent member, a `null`, a structure, a nested list — go
     * last in **both** directions: they are not the smallest value, they are not a value, and
     * `take(sortBy(x, "m", desc), 3)` has to mean the three largest rather than three blanks.
     */
    private fun sort(
        segment: CompiledSortSegment,
        current: List<Any?>,
        context: PreparedRuleContext
    ): List<Any?> {
        val keyed = current.map { element ->
            val key = sortKeyOf(element = element, member = segment.member)
            element to rawToExpressionValue(raw = key, context = context)
        }
        return keyed.sortedWith(comparator = sortComparator(descending = segment.descending))
            .map { entry -> entry.first }
    }

    private fun sortKeyOf(element: Any?, member: String?): Any? {
        if (member == null) {
            return element
        }
        return (element as? Map<*, *>)?.get(member)
    }

    private fun sortComparator(descending: Boolean): Comparator<Pair<Any?, ExpressionValue>> {
        return Comparator { left, right ->
            val leftOrderable = ExpressionValues.isOrderable(value = left.second)
            val rightOrderable = ExpressionValues.isOrderable(value = right.second)
            when {
                !leftOrderable && !rightOrderable -> 0
                !leftOrderable -> 1
                !rightOrderable -> -1
                else -> {
                    val order = ExpressionValues.compareByValue(left = left.second, right = right.second)
                    if (descending) -order else order
                }
            }
        }
    }

    private fun project(current: List<Any?>, name: String): List<Any?> {
        return current.flatMap { element ->
            when (element) {
                is Map<*, *> -> asList(raw = element[name])
                else -> emptyList()
            }
        }
    }

    private fun asList(raw: Any?): List<Any?> {
        return when (raw) {
            null -> emptyList()
            is Collection<*> -> raw.toList()
            else -> listOf(raw)
        }
    }

    /**
     * Collapses the selected elements into one value: exactly one is a scalar, anything else an array.
     *
     * Nothing selected is an **empty array**, not a missing value — the caller has already established
     * that the record carries the root, so this is a collection that holds nothing rather than a
     * collection that is not there. Keeping the two apart is what lets `sum` answer `0` for an empty
     * collection while still answering "no value" for an absent one, and what makes
     * `orders[...].tag contains "x"` a decided `false` when the filter selected nothing.
     *
     * A scalar path that reads a `null` still yields missing: [rawToExpressionValue] maps it there and
     * the element is dropped, so a one-element selection holding `null` collapses to an empty array —
     * which for a collection-valued path is the right reading.
     */
    private fun collapse(values: List<Any?>, context: PreparedRuleContext): ExpressionValue {
        val converted = values.mapNotNull { raw ->
            rawToExpressionValue(raw = raw, context = context).takeIf { value -> value !is MissingExpressionValue }
        }
        return when (converted.size) {
            0 -> if (yieldsCollection) ArrayExpressionValue(values = emptyList()) else MissingExpressionValue
            1 -> converted[0]
            else -> ArrayExpressionValue(values = converted)
        }
    }

    private fun rawToExpressionValue(raw: Any?, context: PreparedRuleContext): ExpressionValue {
        return when (raw) {
            null -> MissingExpressionValue
            is Number -> NumberExpressionValue(value = toBigDecimal(raw = raw))
            is String -> TextExpressionValue(
                value = context.normalizerRegistry.applyAll(value = raw, normalizers = normalizers)
            )

            is Boolean -> BooleanExpressionValue(value = raw)
            is LocalDate -> DateExpressionValue(value = raw)
            is LocalDateTime -> DateExpressionValue(value = raw.toLocalDate())
            is Instant -> DateExpressionValue(value = LocalDate.ofInstant(raw, ZoneOffset.UTC))
            is Collection<*> -> ArrayExpressionValue(
                values = raw.mapNotNull { element ->
                    rawToExpressionValue(raw = element, context = context)
                        .takeIf { value -> value !is MissingExpressionValue }
                }
            )

            is Map<*, *> -> ObjectExpressionValue(value = raw)
            else -> MissingExpressionValue
        }
    }

    /**
     * Whole numbers go through [BigDecimal.valueOf] rather than the string constructor: this runs
     * once per numeric leaf of every projected collection, and the string form allocates twice.
     * `toString()` stays for the rest, where it is the only conversion that keeps the written value.
     */
    private fun toBigDecimal(raw: Number): BigDecimal {
        return when (raw) {
            is BigDecimal -> raw
            is Int, is Long, is Short, is Byte -> BigDecimal.valueOf(raw.toLong())
            else -> BigDecimal(raw.toString())
        }
    }
}
