package ui.builder.model.catalog

/** How a dotted path is spelled, both in the DSL and in a flat schema key. */
private const val PATH_SEPARATOR: Char = '.'

/**
 * Resolves the fields available at the end of a path, by walking [nestedFields] one name at a time.
 *
 * Returns an empty list when the path leaves declared territory — the caller then has nothing to
 * offer in a dropdown, which is the honest answer for an undeclared structure.
 */
fun List<CatalogFieldInfo>.fieldsAtPath(segments: List<String>): List<CatalogFieldInfo> {
    var current = this
    for (segment in segments) {
        val match = current.firstOrNull { it.id == segment || it.alias == segment } ?: return emptyList()
        current = match.nestedFields
    }
    return current
}

/** Resolves the definition a path points at, or null when the path is not fully declared. */
fun List<CatalogFieldInfo>.fieldAtPath(segments: List<String>): CatalogFieldInfo? {
    if (segments.isEmpty()) return null
    var current: CatalogFieldInfo? = null
    var candidates = this
    for (segment in segments) {
        current = candidates.firstOrNull { it.id == segment || it.alias == segment } ?: return null
        candidates = current.nestedFields
    }
    return current
}

/**
 * The members reachable at the end of [segments], resolved the way the engine resolves a path.
 *
 * See [expandRoot] for what "the way the engine resolves a path" adds over the plain nested walk.
 */
fun BuilderCatalog.fieldsAtPath(segments: List<String>): List<CatalogFieldInfo> =
    fields.fieldsAtPath(segments = expandRoot(segments = segments))

/** The definition [segments] points at, resolved the way the engine resolves a path. */
fun BuilderCatalog.fieldAtPath(segments: List<String>): CatalogFieldInfo? =
    fields.fieldAtPath(segments = expandRoot(segments = segments))

/**
 * The canonical segments [segments] stands for, mirroring `FieldPathResolver.resolve`'s own order.
 *
 * Three forms describe the same input and all three must resolve, because the engine accepts all
 * three and the Builder is editing rules the engine has already compiled:
 *
 *  1. **A flat dotted key.** A schema may declare `user.profile.age` as one top-level field instead of
 *     three nested ones. The engine tries the whole identifier as a declared name first, so a flat key
 *     even shadows a nested declaration of the same path — matched first here for the same reason.
 *  2. **The nested walk**, with a per-segment alias. Handled by the list overloads above, unchanged.
 *  3. **A bare alias declared at any depth.** Expanded through [BuilderCatalog.aliasPaths], and only
 *     when nothing above matched: a declared name always wins, which is what the engine's
 *     `resolveInFields`-then-`aliasPaths` order means.
 *
 * An identifier that matches none of the three is returned unchanged, so an undeclared path stays
 * undeclared rather than becoming a resolution error.
 */
private fun BuilderCatalog.expandRoot(segments: List<String>): List<String> {
    if (segments.isEmpty()) {
        return segments
    }
    val flat = segments.joinToString(separator = PATH_SEPARATOR.toString())
    if (fields.any { field -> field.id == flat || field.alias == flat }) {
        return listOf(flat)
    }
    val root = segments.first()
    if (fields.any { field -> field.id == root || field.alias == root }) {
        return segments
    }
    val expanded = aliasPaths[root] ?: return segments
    return expanded + segments.drop(n = 1)
}
