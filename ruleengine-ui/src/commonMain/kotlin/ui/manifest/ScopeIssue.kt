package ui.manifest

/** Type name of a collection field, as the schema's field types spell it. */
private const val COLLECTION_TYPE: String = "collection"

/**
 * Why a manifest entry's `scope` would stop the project from loading, or `null` when it would not.
 *
 * The engine settles this at load time and refuses the manifest outright — see
 * `ruleengine.builder.RuleEngineBuilder.scopedSchema`. The editor used to fall back to the
 * unscoped schema instead (`ScopedEvaluation.memberSchema(...) ?: schema`), so a scope naming
 * nothing looked like it worked here and failed everywhere else. The wording is copied from the
 * engine so the two can never disagree about the same manifest.
 *
 * [fieldTypes] maps each top-level field name to its declared type, lowercased. `null` means no
 * schema is loaded, which is no verdict rather than a complaint: there is nothing to check against.
 */
fun scopeIssue(scope: String, fieldTypes: Map<String, String>?): String? {
    if (fieldTypes == null) return null
    val name = scope.trim()
    if (name.isEmpty()) return null
    val type = fieldTypes[name] ?: return "scope '$name' is not a field of the schema"
    if (type != COLLECTION_TYPE) return "scope '$name' is $type, not a collection"
    return null
}

/** The names a `scope` may legally take, for the hint under an empty scope field. */
fun collectionNames(fieldTypes: Map<String, String>?): List<String> =
    fieldTypes.orEmpty().filterValues { type -> type == COLLECTION_TYPE }.keys.sorted()
