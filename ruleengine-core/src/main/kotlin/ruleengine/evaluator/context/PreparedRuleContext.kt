package ruleengine.evaluator.context

import ruleengine.core.domain.FieldPathResolver
import ruleengine.core.domain.TemporalFormat
import ruleengine.core.domain.dto.FieldDefinition
import ruleengine.core.domain.dto.FieldId
import ruleengine.core.domain.dto.FieldSchema
import ruleengine.core.domain.dto.FieldType
import ruleengine.core.normalizer.NormalizerRegistry
import ruleengine.evaluator.compiled.EvaluationCache
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

class PreparedRuleContext(
    private val values: Map<FieldId, PreparedValue>,
    val rawContext: RuleContext,
    val cache: EvaluationCache = EvaluationCache()
) {
    fun get(field: FieldId): PreparedValue? {
        return values[field]
    }

    fun child(element: Map<*, *>): PreparedRuleContext {
        return PreparedRuleContext(
            values = emptyMap(),
            rawContext = ElementRuleContext(element = element),
            cache = cache
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

            return PreparedRuleContext(values = map, rawContext = ctx)
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
            var normalized = s
            for (n in def.normalizers) normalized = registry.get(n).normalize(value = normalized)
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
            var normalizedSet = set.map { it }.toSet()
            if (def.normalizers.isNotEmpty()) {
                normalizedSet = set.map { v ->
                    var n = v
                    for (nn in def.normalizers) n = registry.get(nn).normalize(value = n)
                    n
                }.toSet()
            }
            map[fieldId] = PreparedStringSet(original = set, normalized = normalizedSet)
        }
    }
}

