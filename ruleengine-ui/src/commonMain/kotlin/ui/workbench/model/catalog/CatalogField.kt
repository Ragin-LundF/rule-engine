package ui.workbench.model.catalog

/**
 * A field entry derived from the loaded schema, for the inspector.
 *
 * Deliberately not merged with [ui.builder.model.catalog.CatalogFieldInfo], which describes the same schema for a
 * different job: the inspector shows a field's `normalizers` and `alias`, the builder's path picker
 * needs a date `format` hint and a recursive `nestedFields` tree. One type would carry seven fields
 * of which each caller reads about five, and the recursion only makes sense for the picker.
 */
data class CatalogField(
    val id: String,
    val type: String,
    val operators: List<String> = emptyList(),
    val normalizers: List<String> = emptyList(),
    val alias: String? = null,
)
