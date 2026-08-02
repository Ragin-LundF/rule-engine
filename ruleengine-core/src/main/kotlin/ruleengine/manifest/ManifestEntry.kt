package ruleengine.manifest

/**
 * One rule set of a project manifest.
 *
 * All paths are relative to the manifest file. The order of [rules] is authoritative for rule
 * execution order.
 */
data class ManifestEntry(
    val id: String,
    val schema: String? = null,
    val actions: String? = null,
    val rules: List<String> = emptyList(),
    /**
     * Name of a declared collection to evaluate once per member, instead of once for the whole
     * document.
     *
     * Absent means whole-document evaluation, which is what every manifest written before this key
     * existed asks for. A scoped run resolves a rule's paths against the member first and falls back
     * to the document, so a rule can still read fields that belong to the document as a whole.
     */
    val scope: String? = null
)
