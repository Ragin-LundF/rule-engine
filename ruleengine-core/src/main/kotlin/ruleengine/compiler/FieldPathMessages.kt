package ruleengine.compiler

/**
 * Wording for the two ways a plain field path can fail to resolve.
 *
 * `Validator` reports them as diagnostics and `Compiler` as exceptions; both must say the same thing, so the
 * text lives here instead of in each of them.
 */
internal object FieldPathMessages {

    fun crossesCollection(field: String, collectionPath: String): String {
        return "Field '$field' reads through collection '$collectionPath', which yields one value per " +
            "element and cannot be compared directly; use an aggregate function such as " +
            "count($collectionPath), sum($field) or a filter such as $collectionPath[...]"
    }

    fun unknownField(field: String): String {
        return "Unknown field '$field'"
    }
}
