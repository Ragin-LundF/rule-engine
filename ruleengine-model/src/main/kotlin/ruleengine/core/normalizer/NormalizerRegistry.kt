package ruleengine.core.normalizer

import ruleengine.core.domain.dto.NormalizerId

object NormalizerRegistry {
    private val builtins: Map<String, Normalizer> = mapOf(
        "trim" to Normalizer { it.trim() },
        "lowercase" to Normalizer { it.lowercase() },
        "uppercase" to Normalizer { it.uppercase() },
        "collapse_whitespace" to Normalizer { s -> s.replace(Regex(pattern = "\\s+"), " ") },
        "remove_punctuation" to Normalizer { s -> s.replace(Regex(pattern = "[\\p{Punct}]"), "") },
        "german_umlaut_fold" to Normalizer { s -> germanUmlautFold(input = s) }
    )

    val default = this

    /**
     * Every built-in normalizer id, in declaration order.
     *
     * Exposed so the schema editor and the YAML completions can offer exactly what [get] accepts,
     * instead of restating the list and drifting from it.
     */
    val ids: List<NormalizerId> = builtins.keys.map { key -> NormalizerId(value = key) }

    fun get(id: NormalizerId): Normalizer {
        return builtins[id.value] ?: throw IllegalArgumentException("Unknown normalizer: ${id.value}")
    }

    /**
     * Runs [normalizers] over [value] in order, left to right.
     *
     * The order matters and is the schema author's: `trim` then `lowercase` is not the same chain as
     * `lowercase` then `trim` once `collapse_whitespace` is in the middle. The compiler normalises a
     * literal with this, and `PreparedRuleContext` normalises the input value with it — the two must
     * apply the identical chain or a rule stops matching its own data.
     */
    fun applyAll(value: String, normalizers: List<NormalizerId>): String {
        var result = value
        for (id in normalizers) {
            result = get(id = id).normalize(value = result)
        }
        return result
    }

    fun createProfile(id: String, steps: List<NormalizerId>): NormalizerProfile {
        val ns = steps.map { get(it) }
        return NormalizerProfile(id = id, steps = ns)
    }

    private fun germanUmlautFold(input: String): String {
        // Map umlauts and ß to ASCII equivalents
        val sb = StringBuilder(input.length)
        for (ch in input) {
            when (ch) {
                'ä' -> sb.append("ae")
                'ö' -> sb.append("oe")
                'ü' -> sb.append("ue")
                'Ä' -> sb.append("AE")
                'Ö' -> sb.append("OE")
                'Ü' -> sb.append("UE")
                'ß' -> sb.append("ss")
                else -> sb.append(ch)
            }
        }
        return sb.toString()
    }
}

