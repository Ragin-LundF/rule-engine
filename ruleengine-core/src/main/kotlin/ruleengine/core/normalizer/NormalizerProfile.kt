package ruleengine.core.normalizer

data class NormalizerProfile(
    val id: String,
    val steps: List<Normalizer>
) {
    fun apply(input: String): String {
        var inputValue = input
        for (normalizer in steps) {
            inputValue = normalizer.normalize(value = inputValue)
        }
        return inputValue
    }
}
