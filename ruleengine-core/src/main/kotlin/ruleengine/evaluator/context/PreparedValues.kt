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
    fun get(field: FieldId): PreparedValue? = values[field]

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
                    FieldType.TEXT -> {
                        val s = raw.toString()
                        // apply configured normalizers in order
                        var normalized = s
                        for (n in def.normalizers) normalized = normalizerRegistry.get(n).normalize(normalized)
                        map[fieldId] = PreparedText(original = s, normalized = normalized)
                    }

                    FieldType.INTEGER -> {
                        val num = when (raw) {
                            is Number -> raw.toLong()
                            is String -> raw.toLongOrNull() ?: continue
                            else -> continue
                        }
                        map[fieldId] = PreparedInteger(num)
                    }

                    FieldType.DECIMAL -> {
                        val bd = when (raw) {
                            is Number -> BigDecimal(raw.toString())
                            is String -> try {
                                BigDecimal(raw)
                            } catch (_: Exception) {
                                continue
                            }

                            else -> continue
                        }
                        map[fieldId] = PreparedDecimal(bd)
                    }

                    FieldType.STRING_SET -> {
                        val set = when (raw) {
                            is Collection<*> -> raw.filterNotNull().map { it.toString() }.toSet()
                            is Array<*> -> raw.filterNotNull().map { it.toString() }.toSet()
                            is String -> setOf(raw)
                            else -> continue
                        }
                        var normalizedSet = set.map { it }.toSet()
                        if (def.normalizers.isNotEmpty()) {
                            normalizedSet = set.map { v ->
                                var n = v
                                for (nn in def.normalizers) n = normalizerRegistry.get(nn).normalize(n)
                                n
                            }.toSet()
                        }
                        map[fieldId] = PreparedStringSet(original = set, normalized = normalizedSet)
                    }

                    else -> {
                        // unsupported types for now
                    }
                }
            }

            return PreparedRuleContext(values = map)
        }
    }
}

