package ui.samples

enum class SampleCategory(val label: String) {
    FINANCE("Finance"),
    LOGGING("Logging"),
    ECOMMERCE("E-Commerce"),
    SECURITY("Security"),
}

data class SampleDescriptor(
    val id: String,
    val name: String,
    val description: String,
    val category: SampleCategory,
    val schemaResPath: String,
    val actionsResPath: String,
    val ruleResPaths: List<String>,
) {
    val ruleCount: Int get() = ruleResPaths.size
}

data class LoadedSample(
    val descriptor: SampleDescriptor,
    val schemaYaml: String,
    val actionsYaml: String,
    val rulesText: String,
)
