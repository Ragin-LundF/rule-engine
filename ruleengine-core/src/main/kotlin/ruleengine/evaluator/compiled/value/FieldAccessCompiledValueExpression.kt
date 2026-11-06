package ruleengine.evaluator.compiled.value

import ruleengine.core.domain.dto.NormalizerId
import ruleengine.core.domain.dto.field.FieldId
import ruleengine.evaluator.compiled.EvaluationCost
import ruleengine.evaluator.compiled.value.function.KeyedSumCompiledValueExpression
import ruleengine.evaluator.compiled.value.path.CompiledFieldSegment
import ruleengine.evaluator.compiled.value.path.CompiledFilterSegment
import ruleengine.evaluator.compiled.value.path.CompiledJoinSegment
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
import ruleengine.evaluator.context.NumericInput
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
    val yieldsCollection: Boolean = false,
    /**
     * Written as `orEmpty(...)` (or as an `optional` keyed source): a collection the record does not
     * carry — absent or `null`, at the end of the path or on the way to it — reads as an empty one.
     * A value the record does carry reads exactly as it would without the flag.
     */
    private val missingAsEmpty: Boolean = false
) : CompiledValueExpression {
    /**
     * Reading a path is cheap; ordering or joining one is not. A sorting or joining path is declared
     * expensive so `AndExpression`'s cost-ordered evaluation leaves it until a cheaper condition has
     * had its chance to decide the rule. Filtering stays cheap deliberately — reclassifying it would
     * reorder conditions in rules that exist today.
     */
    override val cost: EvaluationCost = if (
        segments.any { segment -> segment is CompiledSortSegment || segment is CompiledJoinSegment }
    ) {
        EvaluationCost.EXPENSIVE
    } else {
        EvaluationCost.CHEAP
    }

    /**
     * The root name when the path is plain names only. Such a path reads nothing but what that name
     * resolves to, which is what lets an aggregate over it be shared across filter elements.
     */
    val plainRootName: String? = segments.takeIf { all -> all.all { segment -> segment is CompiledFieldSegment } }
        ?.let { plain -> (plain.first() as CompiledFieldSegment).name }

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
            if (context.isUnreadable(field = FieldId(value = declaredName))) {
                return MissingExpressionValue
            }
        }
        val single = segments.singleOrNull() as? CompiledFieldSegment
        if (single != null) {
            // A whole collection read by name stays one array, even when it holds a single element:
            // collapsing it would turn `tags contains "x"` into a text comparison.
            val raw = context.rawContext.getRaw(fieldPath = listOf(single.name))
            return if (raw == null && missingAsEmpty) {
                ArrayExpressionValue(values = emptyList())
            } else {
                rawToExpressionValue(raw = raw, context = context)
            }
        }
        // A collection the record does not carry stays distinguishable from one it carries holding
        // nothing. Collapsing both to an empty list is what used to make `sum(orders.amount)`
        // undecided for `orders: []`.
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
     * [resolveRawListOrNull] with a collection the record does not carry read as `emptyList()`.
     *
     * No caller is left: every reader has to tell a missing collection from an empty one since 1.14.0.
     */
    @Deprecated(
        message = "Hides a missing collection behind an empty one; use resolveRawListOrNull.",
        replaceWith = ReplaceWith(expression = "resolveRawListOrNull(context = context).orEmpty()"),
    )
    fun resolveRawList(context: PreparedRuleContext): List<Any?> {
        return resolveRawListOrNull(context = context).orEmpty()
    }

    /**
     * Every raw element the path selects, before any of it is wrapped in an [ExpressionValue], or
     * `null` when the record does not carry what the path names.
     *
     * Slicing, collection predicates and keyed joins need the input elements themselves — a key and
     * its value live on the same element, and a projection has already separated them. One shared
     * walk is what keeps those features from each growing their own copy of path resolution.
     *
     * "Does not carry" means: the root is absent, or a step is absent while the walk is still on one
     * single object — `account.orders` with `account` present but holding no `orders`. Once the walk
     * has crossed a collection, an element without the member simply contributes nothing, so
     * `orders.tags` over orders that carry no tags is empty rather than missing: the record does carry
     * the orders.
     *
     * Everything that has to *answer* for the difference reads this instead: [evaluate], so an empty
     * collection becomes an empty array rather than a missing value; `isEmpty`; `sumByKey`; and the
     * collection predicates, which are undecided over a collection that never arrived and vacuously
     * decided over one that arrived empty.
     */
    fun resolveRawListOrNull(context: PreparedRuleContext): List<Any?>? {
        return walkRaw(context = context) ?: emptyList<Any?>().takeIf { missingAsEmpty }
    }

    private fun walkRaw(context: PreparedRuleContext): List<Any?>? {
        val root = segments[0]
        if (root is CompiledJoinSegment) {
            val rows = joinRows(join = root, context = context) ?: return null
            return segments.drop(n = 1).fold(initial = rows) { current, segment ->
                applySegment(segment = segment, current = current, context = context)
            }
        }
        val rootName = (root as? CompiledFieldSegment)?.name ?: return null
        val rootRaw = context.rawContext.getRaw(fieldPath = listOf(rootName)) ?: return null
        var single: Any? = rootRaw.takeUnless { raw -> raw is Collection<*> }
        var current = asList(raw = rootRaw)
        for (index in 1 until segments.size) {
            val segment = segments[index]
            if (single != null && segment is CompiledFieldSegment) {
                val member = (single as? Map<*, *>)?.get(segment.name) ?: return nestedMissing(context = context)
                single = member.takeUnless { raw -> raw is Collection<*> }
                current = asList(raw = member)
                continue
            }
            single = null
            current = applySegment(segment = segment, current = current, context = context)
        }
        return current
    }

    /**
     * What a collection nested in a present object reads as when the object does not carry it:
     * missing, unless the manifest entry asks for the 1.13 reading with `missingCollections: empty`.
     * A single-valued path stays missing either way.
     */
    private fun nestedMissing(context: PreparedRuleContext): List<Any?>? {
        return emptyList<Any?>().takeIf { context.missingCollectionsAsEmpty && yieldsCollection }
    }

    /**
     * One row per key, in first-seen order: the key, plus each source's total under its alias, zero
     * where the source does not mention the key. Null when any source is missing, so a join over data
     * the record does not carry is undecided rather than a row of zeros.
     *
     * The totals come from the same summing `sumByKey` uses, so the two always agree.
     */
    private fun joinRows(join: CompiledJoinSegment, context: PreparedRuleContext): List<Any?>? {
        val totals = KeyedSumCompiledValueExpression.totalsPerSource(
            key = join.key,
            sources = join.sources.values.toList(),
            context = context
        ) ?: return null
        val keys = LinkedHashSet<Any>()
        totals.forEach { sourceTotals -> keys.addAll(sourceTotals.keys) }
        val aliases = join.sources.keys.toList()
        return keys.map { keyValue ->
            val row = LinkedHashMap<String, Any>()
            row[join.key] = keyValue
            aliases.forEachIndexed { index, alias -> row[alias] = totals[index][keyValue] ?: BigDecimal.ZERO }
            row
        }
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
            is PreparedDateTime -> DateExpressionValue(
                value = prepared.value.toLocalDate(),
                timeOfDay = prepared.value.toLocalTime()
            )

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
            // Only ever the first segment, which `resolveRawListOrNull` turns into rows itself.
            is CompiledJoinSegment -> current
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
            is Number -> NumericInput.decimalOrNull(raw = raw)
                ?.let { number -> NumberExpressionValue(value = number) }
                ?: MissingExpressionValue

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
}
