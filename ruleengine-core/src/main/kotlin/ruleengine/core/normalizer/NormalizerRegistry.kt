package ruleengine.core.normalizer

import ruleengine.core.domain.NormalizerId

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

    fun get(id: NormalizerId): Normalizer {
        return builtins[id.value] ?: throw IllegalArgumentException("Unknown normalizer: ${id.value}")
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

