package ui.builder


/** Type name of an object field, as [CatalogFieldInfo.type] spells it. */
private const val OBJECT_TYPE: String = "object"

/**
 * Every scalar field reachable from this catalog without crossing a collection, each carrying its
 * dotted path as its [CatalogFieldInfo.id].
 *
 * A plain condition row names one field and compares it directly, so it can only address a scalar,
 * and the engine rejects a path that runs through a collection — see
 * `ruleengine.core.domain.FieldPathResolution.CrossesCollection`. This mirrors
 * `ruleengine.core.domain.FieldPathResolver.scalarPaths` so the Builder offers exactly the paths a
 * condition may use, and resolves the ones a rule already contains.
 *
 * A schema that declares flat dotted keys is unaffected: without nesting the ids pass through
 * unchanged.
 */
fun List<CatalogFieldInfo>.scalarPaths(): List<CatalogFieldInfo> {
    return buildList {
        collectScalarPaths(prefix = "", fields = this@scalarPaths, target = this)
    }
}

private fun collectScalarPaths(
    prefix: String,
    fields: List<CatalogFieldInfo>,
    target: MutableList<CatalogFieldInfo>,
) {
    for (field in fields) {
        val path = "$prefix${field.id}"
        when {
            field.type.lowercase() == OBJECT_TYPE ->
                collectScalarPaths(prefix = "$path.", fields = field.nestedFields, target = target)

            // A collection's members are multi-valued once projected, which only an aggregate or a
            // filtered comparison row can handle — never a plain condition.
            OperatorOptions.isStructureType(fieldType = field.type) -> Unit

            else -> target += field.copy(id = path)
        }
    }
}
