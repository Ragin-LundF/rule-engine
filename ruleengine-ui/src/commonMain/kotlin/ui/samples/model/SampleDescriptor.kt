package ui.samples.model

data class SampleDescriptor(
    val id: String,
    val name: String,
    val description: String,
    val category: SampleCategory,
    /**
     * The sample's manifest. Loaded alongside the rules so a sample opens with the same manifest state
     * a project does — without it the manifest run diagram has no entry to draw.
     */
    val manifestResPath: String,
    val schemaResPath: String,
    val actionsResPath: String,
    /**
     * The rule files, in the order the manifest lists them.
     *
     * That order is load-bearing, not cosmetic: a `set` clause only reaches the rules after it and a
     * `stop` ends the run at its own position. `SampleRegistryTest` pins this list against the
     * manifest so the two cannot drift.
     */
    val ruleResPaths: List<String>,
) {
    val ruleCount: Int get() = ruleResPaths.size
}
