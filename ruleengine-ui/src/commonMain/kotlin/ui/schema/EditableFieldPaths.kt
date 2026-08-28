package ui.schema

import ui.schema.model.EditableField

/**
 * Reaching a field by the name a rule calls it: its **dotted path**.
 *
 * The editor model is a tree — `existingLoans` holds `lender` — while every other part of the app names
 * a field by the path a rule writes. The Inspector's selection is one of those paths, so this is the
 * translation, and it is here rather than inline because both halves of it (find, and update in place)
 * have to agree about what a path means.
 *
 * A leaf name is not unique: `lender` can be a member of two different collections. Everything below
 * walks the tree segment by segment rather than searching by name, which is the same reason
 * `ui.dock.schemaFieldRange` descends through each parent's own `fields:` key.
 */

/** The field [dotted] names, or null when the path does not exist. */
fun List<EditableField>.findByPath(dotted: String): EditableField? {
    val segments = dotted.split(".").filter { part -> part.isNotBlank() }
    if (segments.isEmpty()) return null

    var level: List<EditableField> = this
    var found: EditableField? = null
    for (segment in segments) {
        found = level.firstOrNull { field -> field.path == segment } ?: return null
        level = found.fields
    }
    return found
}

/**
 * [this] with the field at [dotted] replaced by `transform` applied to it.
 *
 * A no-op when the path does not exist, rather than an insert: the caller is editing something the user
 * selected, and inventing a field because the selection went stale would write a row nobody asked for.
 */
fun List<EditableField>.updateAtPath(
    dotted: String,
    transform: (EditableField) -> EditableField,
): List<EditableField> {
    val segments = dotted.split(".").filter { part -> part.isNotBlank() }
    if (segments.isEmpty()) return this
    return updateSegments(segments = segments, transform = transform)
}

private fun List<EditableField>.updateSegments(
    segments: List<String>,
    transform: (EditableField) -> EditableField,
): List<EditableField> {
    val head = segments.first()
    val index = indexOfFirst { field -> field.path == head }
    if (index < 0) return this

    val current = this[index]
    val updated = if (segments.size == 1) {
        transform(current)
    } else {
        current.copy(fields = current.fields.updateSegments(segments = segments.drop(n = 1), transform = transform))
    }
    return toMutableList().also { list -> list[index] = updated }
}

/**
 * [this] without the field at [dotted].
 *
 * Removing a parent takes its members with it, which is what the tree already means — a member has no
 * existence outside the structure that declares it.
 */
fun List<EditableField>.removeAtPath(dotted: String): List<EditableField> {
    val segments = dotted.split(".").filter { part -> part.isNotBlank() }
    if (segments.isEmpty()) return this
    return removeSegments(segments = segments)
}

private fun List<EditableField>.removeSegments(segments: List<String>): List<EditableField> {
    val head = segments.first()
    val index = indexOfFirst { field -> field.path == head }
    if (index < 0) return this

    if (segments.size == 1) {
        return filterIndexed { position, _ -> position != index }
    }
    val current = this[index]
    val updated = current.copy(fields = current.fields.removeSegments(segments = segments.drop(n = 1)))
    return toMutableList().also { list -> list[index] = updated }
}

/** Every field, with the dotted path it is reached by. Parents come before their members. */
fun List<EditableField>.flattenPaths(prefix: String = ""): List<Pair<String, EditableField>> =
    flatMap { field ->
        val dotted = if (prefix.isEmpty()) field.path else "$prefix.${field.path}"
        listOf(dotted to field) + field.fields.flattenPaths(prefix = dotted)
    }

/** The dotted path of [dotted]'s parent, or null when it is top level. */
fun parentPathOf(dotted: String): String? =
    dotted.substringBeforeLast(delimiter = '.', missingDelimiterValue = "").takeIf { it.isNotBlank() }
