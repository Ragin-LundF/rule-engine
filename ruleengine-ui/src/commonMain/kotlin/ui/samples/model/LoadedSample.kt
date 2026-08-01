package ui.samples.model
data class LoadedSample(
    val descriptor: SampleDescriptor,
    val schemaYaml: String,
    val actionsYaml: String,
    val rulesText: String,
)
