package ui.builder.model.catalog

import ui.builder.OperatorOptions


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
 *
 * Rule output variables are included. They are scalars as far as a condition row is concerned, and
 * [OperatorOptions.forCatalogField] is what keeps the row to operator spellings the parser routes
 * through the expression path — a named operator would report `${'$'}name` as an unknown field.
 */
fun List<CatalogFieldInfo>.scalarPaths(): List<CatalogFieldInfo> {
    return buildList {
        collectScalarPaths(prefix = "", fields = this@scalarPaths, target = this)
    }
}

/**
 * The first collection reachable in this catalog, by its dotted path, or `null` when there is none.
 *
 * Descends through objects the same way [collectScalarPaths] does, so a collection declared under an
 * object is found too. Its purpose is to answer "did [scalarPaths] have to leave fields out, and
 * which one can I name in the explanation" in one pass: a condition dropdown that silently omits
 * half a schema reads as a defect, and an example built from the author's own field reads as an
 * answer where a made-up one reads as boilerplate.
 */
fun List<CatalogFieldInfo>.firstCollectionPath(prefix: String = ""): String? {
    for (field in this) {
        val path = "$prefix${field.id}"
        val found = when {
            field.type.lowercase() == OBJECT_TYPE -> field.nestedFields.firstCollectionPath(prefix = "$path.")
            OperatorOptions.isStructureType(fieldType = field.type) -> path
            else -> null
        }
        if (found != null) return found
    }
    return null
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

            else -> {
                target += field.copy(id = path)
                // The alias is a legal identifier on its own, so offer it as its own entry rather than
                // relabelling the path: the Builder stores whichever spelling the author picked, and an
                // alias-authored rule then resolves by exact match.
                field.alias?.let { alias -> target += field.copy(id = alias) }
            }
        }
    }
}
