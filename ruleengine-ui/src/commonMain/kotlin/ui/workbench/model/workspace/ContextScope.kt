package ui.workbench.model.workspace

import ui.builder.model.catalog.CatalogFieldInfo

/**
 * What a name may refer to at the current selection, for the context column.
 *
 * @param title     Says whose names these are — the record's, or one element's inside a list.
 * @param fields    The fields or members that can be named here, in schema order.
 * @param variables The `$` variables an earlier rule publishes; empty inside an element, where a bare
 *   name means a member and never a variable.
 * @param note      What the list does not show, when that matters to what a name means here.
 */
data class ContextScope(
    val title: String,
    val fields: List<CatalogFieldInfo>,
    val variables: List<CatalogFieldInfo>,
    val note: String? = null,
)
