package ruleengine.core.domain

/**
 * Resolves a field identifier written in a rule to its declaration in a [FieldSchema].
 *
 * The schema supports two ways of describing the same nested input: a single flat field id that spells out
 * the whole path (`user.profile.age`), or nested `fields:` blocks that describe each level. Both must work
 * for every operator, so every stage that starts from a plain field identifier — `Validator`, `Compiler`,
 * `PreparedRuleContext` — resolves it here instead of doing its own map lookup.
 *
 * A flat id is matched first, so a schema that declares dotted keys keeps its exact previous behaviour and
 * can even shadow a nested declaration of the same path.
 */
object FieldPathResolver {

    private const val PATH_SEPARATOR = '.'

    /** Resolves [identifier] against [schema], honouring aliases at every level. */
    fun resolve(identifier: String, schema: FieldSchema): FieldPathResolution {
        val flat = resolveInFields(identifier = identifier, fields = schema.fields)
        if (flat != null) {
            return FieldPathResolution.Resolved(id = FieldId(value = flat.name), definition = flat.definition)
        }

        val segments = identifier.split(PATH_SEPARATOR)
        if (segments.size < 2) {
            return FieldPathResolution.Unknown
        }

        return walk(segments = segments, fields = schema.fields)
    }

    /**
     * Resolves a single segment against [fields] and returns its declared name, or the segment unchanged
     * when nothing matches. Used where a caller walks a path itself, such as a value expression.
     */
    fun resolveName(identifier: String, fields: Map<FieldId, FieldDefinition>): String {
        return resolveInFields(identifier = identifier, fields = fields)?.name ?: identifier
    }

    /**
     * Every scalar field reachable from [schema] without crossing a [FieldType.COLLECTION], keyed by its
     * canonical dotted path.
     *
     * Collections are excluded because their members are multi-valued once projected, which only the value
     * expression path can handle.
     */
    fun scalarPaths(schema: FieldSchema): Map<FieldId, FieldDefinition> {
        val paths = mutableMapOf<FieldId, FieldDefinition>()
        collectScalarPaths(prefix = null, fields = schema.fields, target = paths)
        return paths
    }

    private fun walk(segments: List<String>, fields: Map<FieldId, FieldDefinition>): FieldPathResolution {
        var currentFields = fields
        var current: FieldMatch? = null
        val canonical = mutableListOf<String>()

        for (segment in segments) {
            val parent = current
            if (parent != null && parent.definition.type == FieldType.COLLECTION) {
                return FieldPathResolution.CrossesCollection(
                    collectionPath = canonical.joinToString(separator = PATH_SEPARATOR.toString())
                )
            }

            val match = resolveInFields(identifier = segment, fields = currentFields)
                ?: return FieldPathResolution.Unknown
            canonical += match.name
            current = match
            currentFields = match.definition.fields
        }

        val resolved = current ?: return FieldPathResolution.Unknown
        return FieldPathResolution.Resolved(
            id = FieldId(value = canonical.joinToString(separator = PATH_SEPARATOR.toString())),
            definition = resolved.definition
        )
    }

    private fun collectScalarPaths(
        prefix: String?,
        fields: Map<FieldId, FieldDefinition>,
        target: MutableMap<FieldId, FieldDefinition>
    ) {
        for ((fieldId, definition) in fields) {
            val path = if (prefix == null) fieldId.value else "$prefix$PATH_SEPARATOR${fieldId.value}"
            when (definition.type) {
                FieldType.OBJECT -> collectScalarPaths(prefix = path, fields = definition.fields, target = target)
                FieldType.COLLECTION -> Unit
                else -> target[FieldId(value = path)] = definition
            }
        }
    }

    /** A declared field together with the declared name it was found under, which may differ from an alias. */
    private data class FieldMatch(val name: String, val definition: FieldDefinition)

    private fun resolveInFields(identifier: String, fields: Map<FieldId, FieldDefinition>): FieldMatch? {
        val direct = fields[FieldId(value = identifier)]
        if (direct != null) {
            return FieldMatch(name = identifier, definition = direct)
        }

        for ((id, definition) in fields) {
            if (definition.alias == identifier) {
                return FieldMatch(name = id.value, definition = definition)
            }
        }

        return null
    }
}
