package ruleengine.export

import ruleengine.core.domain.FieldPathResolution
import ruleengine.core.domain.FieldPathResolver
import ruleengine.core.domain.dto.FieldSchema
import ruleengine.core.domain.dto.isStructure

/**
 * Turns a field path written in a rule into a label a business reader recognises.
 *
 * `shipment.customer.tier` is precise and unreadable; "Customer › Tier" is neither ambiguous nor
 * jargon. The schema is consulted first — an [ruleengine.core.domain.dto.FieldDefinition.alias] is
 * the author's own name for the field and always wins — and only the derived form guesses.
 */
object FieldLabels {

    private const val PATH_SEPARATOR = '.'
    private const val LABEL_SEPARATOR = " › "

    /** Splits camelCase and ACRONYMFollowedByWord, so `weightKg` becomes `weight` + `Kg`. */
    private val CAMEL_BOUNDARY = Regex(pattern = "(?<=[a-z0-9])(?=[A-Z])|(?<=[A-Z])(?=[A-Z][a-z])")

    /**
     * Labels a full, top-level path such as `shipment.customer.tier`.
     *
     * The leading segment is dropped when the schema declares it as a `collection` or an `object`,
     * because a structure root names the record the fields live in rather than the field itself, and
     * the sentence around the label already supplies that context ("…of parcels"). It is kept
     * whenever the schema does not say so, since dropping a segment on a guess would silently rename
     * the field.
     */
    fun forPath(path: String, schema: FieldSchema?): String {
        val segments = path.split(PATH_SEPARATOR).filter { segment -> segment.isNotBlank() }
        if (segments.isEmpty()) {
            return path
        }

        val alias = aliasFor(path = path, schema = schema)
        if (alias != null) {
            return alias
        }

        val meaningful = if (segments.size > 1 && isStructureRoot(root = segments.first(), schema = schema)) {
            segments.drop(n = 1)
        } else {
            segments
        }

        return forSegments(segments = meaningful)
    }

    /**
     * Labels segments that are already relative to something — the path inside a filter, or the
     * measured member of an aggregate. Nothing is dropped: the caller has established the context.
     */
    fun forSegments(segments: List<String>): String {
        return segments.joinToString(separator = LABEL_SEPARATOR) { segment -> humanize(segment = segment) }
    }

    /**
     * `declaredValue` → `Declared Value`, `origin_hub` → `Origin Hub`, `IBAN` → `IBAN`.
     *
     * An all-caps word is left alone: it is an acronym, and title-casing it to `Iban` reads as a
     * misspelling rather than as prose.
     */
    fun humanize(segment: String): String {
        val words = segment
            .split('_', '-')
            .flatMap { part -> part.split(CAMEL_BOUNDARY) }
            .filter { word -> word.isNotBlank() }

        if (words.isEmpty()) {
            return segment
        }

        return words.joinToString(separator = " ") { word -> capitalize(word = word) }
    }

    private fun capitalize(word: String): String {
        if (word.length > 1 && word.none { character -> character.isLowerCase() }) {
            return word
        }

        return word.replaceFirstChar { character -> character.uppercaseChar() }
    }

    private fun aliasFor(path: String, schema: FieldSchema?): String? {
        if (schema == null) {
            return null
        }

        val resolution = FieldPathResolver.resolve(identifier = path, schema = schema)
        if (resolution !is FieldPathResolution.Resolved) {
            return null
        }

        return resolution.definition.alias
    }

    private fun isStructureRoot(root: String, schema: FieldSchema?): Boolean {
        if (schema == null) {
            return false
        }

        val resolution = FieldPathResolver.resolve(identifier = root, schema = schema)

        return resolution is FieldPathResolution.Resolved && resolution.definition.type.isStructure
    }
}
