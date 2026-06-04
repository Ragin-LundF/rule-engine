package ruleengine.evaluator.context

import ruleengine.core.domain.FieldId
import ruleengine.core.domain.FieldSchema
import ruleengine.core.domain.FieldType
import ruleengine.core.normalizer.NormalizerRegistry
import java.math.BigDecimal

sealed interface PreparedValue

data class PreparedText(val original: String, val normalized: String) : PreparedValue
data class PreparedInteger(val value: Long) : PreparedValue
data class PreparedDecimal(val value: BigDecimal) : PreparedValue
data class PreparedStringSet(val original: Set<String>, val normalized: Set<String>) : PreparedValue

class PreparedRuleContext(private val values: Map<FieldId, PreparedValue>) {
    fun get(field: FieldId): PreparedValue? {
        return values[field]
    }

    companion object {
        fun prepare(
            ctx: RuleContext,
            schema: FieldSchema,
            normalizerRegistry: NormalizerRegistry = NormalizerRegistry.default
        ): PreparedRuleContext {
            val map = mutableMapOf<FieldId, PreparedValue>()

            for ((fieldId, def) in schema.fields) {
                val raw = ctx.get(fieldId) ?: continue
                when (def.type) {
                    FieldType.TEXT -> prepareText(
                        fieldId = fieldId,
                        def = def,
                        raw = raw,
                        map = map,
                        registry = normalizerRegistry
                    )

                    FieldType.INTEGER -> prepareInteger(fieldId = fieldId, raw = raw, map = map)
                    FieldType.DECIMAL -> prepareDecimal(fieldId = fieldId, raw = raw, map = map)
                    FieldType.STRING_SET -> prepareStringSet(
                        fieldId = fieldId,
                        def = def,
                        raw = raw,
                        map = map,
                        registry = normalizerRegistry
                    )

                    else -> {
                        // unsupported types for now
                    }
                }
            }

            return PreparedRuleContext(values = map)
        }

        private fun prepareText(
            fieldId: FieldId,
            def: ruleengine.core.domain.FieldDefinition,
            raw: Any?,
            map: MutableMap<FieldId, PreparedValue>,
            registry: NormalizerRegistry
        ) {
            val s = raw.toString()
            var normalized = s
            for (n in def.normalizers) normalized = registry.get(n).normalize(normalized)
            map[fieldId] = PreparedText(original = s, normalized = normalized)
        }

        private fun prepareInteger(fieldId: FieldId, raw: Any?, map: MutableMap<FieldId, PreparedValue>) {
            val num = when (raw) {
                is Number -> raw.toLong()
                is String -> raw.toLongOrNull() ?: return
                else -> return
            }
            map[fieldId] = PreparedInteger(num)
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
            map[fieldId] = PreparedDecimal(bd)
        }

        private fun prepareStringSet(
            fieldId: FieldId,
            def: ruleengine.core.domain.FieldDefinition,
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
                    for (nn in def.normalizers) n = registry.get(nn).normalize(n)
                    n
                }.toSet()
            }
            map[fieldId] = PreparedStringSet(original = set, normalized = normalizedSet)
        }
    }
}

