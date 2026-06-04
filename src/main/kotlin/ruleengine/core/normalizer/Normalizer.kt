package ruleengine.core.normalizer

fun interface Normalizer {
    fun normalize(value: String): String
}

data class NormalizerProfile(
    val id: String,
    val steps: List<Normalizer>
) {
    fun apply(input: String): String {
        var v = input
        for (n in steps) v = n.normalize(v)
        return v
    }
}

