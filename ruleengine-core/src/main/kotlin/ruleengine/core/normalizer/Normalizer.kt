package ruleengine.core.normalizer

fun interface Normalizer {
    fun normalize(value: String): String
}
