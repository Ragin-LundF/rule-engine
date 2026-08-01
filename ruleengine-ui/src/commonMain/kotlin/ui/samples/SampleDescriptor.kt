package ui.samples
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
