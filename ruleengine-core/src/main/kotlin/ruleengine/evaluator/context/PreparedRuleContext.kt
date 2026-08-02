package ruleengine.evaluator.context

import ruleengine.core.domain.FieldPathResolver
import ruleengine.core.domain.TemporalFormat
import ruleengine.core.domain.dto.field.FieldDefinition
import ruleengine.core.domain.dto.field.FieldId
import ruleengine.core.domain.dto.field.FieldSchema
import ruleengine.core.domain.dto.field.FieldType
import ruleengine.core.normalizer.NormalizerRegistry
import ruleengine.evaluator.compiled.EvaluationCache
import ruleengine.evaluator.compiled.value.result.ExpressionValue
import ruleengine.evaluator.context.dto.PreparedBoolean
import ruleengine.evaluator.context.dto.PreparedDate
import ruleengine.evaluator.context.dto.PreparedDateTime
import ruleengine.evaluator.context.dto.PreparedDecimal
import ruleengine.evaluator.context.dto.PreparedInteger
import ruleengine.evaluator.context.dto.PreparedStringSet
import ruleengine.evaluator.context.dto.PreparedText
import ruleengine.evaluator.context.dto.PreparedValue
import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneOffset

/**
 * The evaluation context: input values typed and normalised against the field schema, plus the
 * scratch state one evaluation needs.
 *
 * [values] is immutable — the engine never modifies input data. [variables] is not: it holds the
 * values published by `set` clauses of rules that matched earlier in the same evaluation, and it is
 * the one place where a rule can affect a later one. It is reset by `RuleEngine.evaluate`, so
 * evaluating the same context twice starts from a clean slate both times.
 *
 * A context carries one record — [values] is a snapshot taken in [prepare] — so it belongs to a
 * single evaluation and must not be shared between threads: [variables] and [cache] are plain maps,
 * and concurrent evaluations would write to both. Sharing the [ruleengine.evaluator.RuleEngine]
 * itself is safe; give each thread its own context.
 */
class PreparedRuleContext(
    private val values: Map<FieldId, PreparedValue>,
    val rawContext: RuleContext,
    val cache: EvaluationCache = EvaluationCache(),
    /** Variables published by `set` clauses so far, keyed by name without the `$` prefix. */
    val variables: MutableMap<String, ExpressionValue> = mutableMapOf(),
    /**
     * The normalizers that produced [values], kept so the value-expression path can normalise the
     * text it reads through [rawContext] the same way. Without it a member of a collection would
     * compare raw while the same field compares normalised when declared at the top level.
     */
    val normalizerRegistry: NormalizerRegistry = NormalizerRegistry.default
) {
    fun get(field: FieldId): PreparedValue? {
        return values[field]
    }

    /** Drops every variable, so the next evaluation starts from a clean slate. */
    fun clearVariables() {
        variables.clear()
    }

    /**
     * A context scoped to one element of a filtered collection. It shares [variables] with its
     * parent so a filter predicate reads the same variables the surrounding rule does.
     *
     * It does **not** share [cache]. The cache is keyed by the compiled node alone, so one cache
     * across elements would answer `orders[count(items) > 2]` for every order with the first
     * order's item count.
     *
     * The element reads through to this context for names it does not carry, so a predicate can
     * compare a member against a document-level field.
     *
     * ponytail: the child has no prepared values of its own, so a document field read from inside a
     * predicate comes back raw rather than typed. Text is unaffected — the compiled path carries the
     * declared normalizers — but a date with a non-ISO `format` would not be parsed. Give the child a
     * prepared-value fallback if that case ever comes up.
     */
    fun child(element: Map<*, *>): PreparedRuleContext {
        return PreparedRuleContext(
            values = emptyMap(),
            rawContext = ElementRuleContext(element = element, fallback = rawContext),
            variables = variables,
            normalizerRegistry = normalizerRegistry
        )
    }

    companion object {
        fun prepare(
            ctx: RuleContext,
            schema: FieldSchema,
            normalizerRegistry: NormalizerRegistry = NormalizerRegistry.default
        ): PreparedRuleContext {
            val map = mutableMapOf<FieldId, PreparedValue>()

            for ((fieldId, def) in schema.fields) {
                prepareValue(fieldId = fieldId, def = def, ctx = ctx, map = map, registry = normalizerRegistry)
            }

            // A scalar declared inside an `object` is read by its dotted path, which `RuleContext.get`
            // already navigates. Collections are left out: projecting a list yields many values, which only
            // the value expression path can compare. A dotted field id declared flat is prepared by the loop
            // above and is not overwritten here.
            for ((fieldId, def) in FieldPathResolver.scalarPaths(schema = schema)) {
                if (!map.containsKey(fieldId)) {
                    prepareValue(fieldId = fieldId, def = def, ctx = ctx, map = map, registry = normalizerRegistry)
                }
            }

            return PreparedRuleContext(
                values = map,
                rawContext = ctx,
                normalizerRegistry = normalizerRegistry
            )
        }

        private fun prepareValue(
            fieldId: FieldId,
            def: FieldDefinition,
            ctx: RuleContext,
            map: MutableMap<FieldId, PreparedValue>,
            registry: NormalizerRegistry
        ) {
            val raw = ctx.get(fieldId) ?: return
            when (def.type) {
                FieldType.TEXT -> prepareText(
                    fieldId = fieldId,
                    def = def,
                    raw = raw,
                    map = map,
                    registry = registry
                )

                FieldType.INTEGER -> prepareInteger(fieldId = fieldId, raw = raw, map = map)
                FieldType.DECIMAL -> prepareDecimal(fieldId = fieldId, raw = raw, map = map)
                FieldType.BOOLEAN -> prepareBoolean(fieldId = fieldId, raw = raw, map = map)
                FieldType.DATE -> prepareDate(fieldId = fieldId, def = def, raw = raw, map = map)
                FieldType.DATE_TIME -> prepareDateTime(fieldId = fieldId, def = def, raw = raw, map = map)
                FieldType.STRING_SET -> prepareStringSet(
                    fieldId = fieldId,
                    def = def,
                    raw = raw,
                    map = map,
                    registry = registry
                )

                else -> {
                    // Structure types carry no value of their own; their members are prepared instead.
                }
            }
        }

        private fun prepareText(
            fieldId: FieldId,
            def: FieldDefinition,
            raw: Any?,
            map: MutableMap<FieldId, PreparedValue>,
            registry: NormalizerRegistry
        ) {
            val s = raw.toString()
            val normalized = registry.applyAll(value = s, normalizers = def.normalizers)
            map[fieldId] = PreparedText(original = s, normalized = normalized)
        }

        /**
         * Reads a calendar date. A value carrying a time is truncated to its date; an `Instant` is
         * resolved at UTC, because the engine compares calendar dates and has no timezone concept.
         *
         * A `String` is read with the field's declared [FieldDefinition.format], or as ISO-8601 when the
         * field declares none. Already-typed values carry no text, so no format applies to them.
         */
        private fun prepareDate(
            fieldId: FieldId,
            def: FieldDefinition,
            raw: Any?,
            map: MutableMap<FieldId, PreparedValue>
        ) {
            val date = when (raw) {
                is LocalDate -> raw
                is LocalDateTime -> raw.toLocalDate()
                is Instant -> raw.atZone(ZoneOffset.UTC).toLocalDate()
                is String -> TemporalFormat.parseDate(text = raw, pattern = def.format) ?: return
                else -> return
            }
            map[fieldId] = PreparedDate(value = date)
        }

        /**
         * Reads a date with its time of day. A date-only value starts at midnight, and an `Instant` is
         * resolved at UTC. A `String` is read with the field's declared [FieldDefinition.format], or as
         * ISO-8601 when the field declares none.
         */
        private fun prepareDateTime(
            fieldId: FieldId,
            def: FieldDefinition,
            raw: Any?,
            map: MutableMap<FieldId, PreparedValue>
        ) {
            val dateTime = when (raw) {
                is LocalDateTime -> raw
                is LocalDate -> raw.atStartOfDay()
                is Instant -> LocalDateTime.ofInstant(raw, ZoneOffset.UTC)
                is String -> TemporalFormat.parseDateTime(text = raw, pattern = def.format) ?: return
                else -> return
            }
            map[fieldId] = PreparedDateTime(value = dateTime)
        }

        private fun prepareBoolean(fieldId: FieldId, raw: Any?, map: MutableMap<FieldId, PreparedValue>) {
            val flag = when (raw) {
                is Boolean -> raw
                is String -> when {
                    raw.equals(other = "true", ignoreCase = true) -> true
                    raw.equals(other = "false", ignoreCase = true) -> false
                    else -> return
                }

                else -> return
            }
            map[fieldId] = PreparedBoolean(value = flag)
        }

        private fun prepareInteger(fieldId: FieldId, raw: Any?, map: MutableMap<FieldId, PreparedValue>) {
            val num = when (raw) {
                is Number -> raw.toLong()
                is String -> raw.toLongOrNull() ?: return
                else -> return
            }
            map[fieldId] = PreparedInteger(value = num)
        }

        private fun prepareDecimal(fieldId: FieldId, raw: Any?, map: MutableMap<FieldId, PreparedValue>) {
            val bd = when (raw) {
                is Number -> BigDecimal(raw.toString())
                is String -> try {
                    BigDecimal(raw)
                } catch (_: Exception) {
                    return
                }

                else -> return
            }
            map[fieldId] = PreparedDecimal(value = bd)
        }

        private fun prepareStringSet(
            fieldId: FieldId,
            def: FieldDefinition,
            raw: Any?,
            map: MutableMap<FieldId, PreparedValue>,
            registry: NormalizerRegistry
        ) {
            val set = when (raw) {
                is Collection<*> -> raw.filterNotNull().map { it.toString() }.toSet()
                is Array<*> -> raw.filterNotNull().map { it.toString() }.toSet()
                is String -> setOf(raw)
                else -> return
            }
            val normalizedSet = set.map { value ->
                registry.applyAll(value = value, normalizers = def.normalizers)
            }.toSet()
            map[fieldId] = PreparedStringSet(original = set, normalized = normalizedSet)
        }
    }
}

