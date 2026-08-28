package ui.util

/**
 * Returns a copy of this list with [index] replaced by [value].
 *
 * The builder's operand trees are immutable values held in single Compose state slots, so every edit
 * to a nested argument, term or path segment is expressed as "rebuild the list around one new
 * element". This was defined beside one of the operand editors; it moved here when the resolver
 * needed it too, and those editors are being folded into the inspector.
 *
 * Out-of-range indices return the list unchanged rather than throwing: a selection can outlive the
 * thing it points at — an argument removed while its editor is open — and a crash is the wrong answer
 * to a stale index.
 */
fun <T> List<T>.replaceAt(index: Int, value: T): List<T> {
    if (index !in indices) {
        return this
    }
    return toMutableList().also { copy -> copy[index] = value }
}
