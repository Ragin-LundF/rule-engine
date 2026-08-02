package ui.samples.model

data class LoadedSample(
    val descriptor: SampleDescriptor,
    val manifestYaml: String,
    val schemaYaml: String,
    val actionsYaml: String,
    val rulesText: String,
    /**
     * Each rule file as `(path relative to the manifest, content)`, in manifest order.
     *
     * Kept beside the concatenated [rulesText] because the two answer different questions: the editor
     * and the tester consume the concatenation, while the manifest run diagram needs to know which file
     * a rule came from — which joining the files first throws away.
     */
    val ruleFiles: List<Pair<String, String>>,
)
