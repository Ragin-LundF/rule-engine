package ruleengine.intellij.navigation

import ruleengine.core.domain.FieldPathResolver
import ruleengine.core.domain.dto.field.FieldDefinition
import ruleengine.core.domain.dto.field.FieldId
import ruleengine.core.domain.dto.field.FieldSchema
import ruleengine.intellij.navigation.model.SchemaKeyPath

/**
 * Maps a field path written in a rule to the YAML keys that declare it.
 *
 * Uses core's resolver for each step, so aliases, bare aliases of nested fields and flat dotted ids
 * resolve as the engine resolves them. Unlike a condition, navigation may walk through a collection.
 */
object SchemaKeys {
    private const val FIELDS_KEY = "fields"

    fun keysFor(path: String, schema: FieldSchema): SchemaKeyPath? {
        if (schema.fields.containsKey(FieldId(value = path))) {
            return SchemaKeyPath(keys = listOf(FIELDS_KEY, path), viaAlias = false)
        }
        val written = path.split('.')
        val canonical = FieldPathResolver.expandRoot(name = written.first(), schema = schema) + written.drop(n = 1)
        var fields = schema.fields
        var declared: FieldDefinition? = null
        val keys = mutableListOf<String>()
        for (segment in canonical) {
            val name = FieldPathResolver.resolveName(identifier = segment, fields = fields)
            val definition = fields[FieldId(value = name)] ?: return null
            keys += listOf(FIELDS_KEY, name)
            fields = definition.fields
            declared = definition
        }
        return SchemaKeyPath(keys = keys, viaAlias = declared?.alias == written.last())
    }
}
