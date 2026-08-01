package ui.diagrams.model

import ruleengine.core.domain.dto.FieldDefinition
import ruleengine.core.domain.dto.FieldSchema

/**
 * The scalar field paths a schema declares, in declaration order.
 *
 * The field flow view needs the full list rather than only the paths rules mention, because a field
 * nothing reads is exactly what it exists to point out. Only leaves are returned: an `object` or
 * `collection` node is a container on the way to a value, not a value a rule can compare.
 *
 * A structure whose members are not declared is itself treated as a leaf — schema validation stays
 * permissive there, so it is the deepest path the schema actually promises.
 */
object SchemaLeaves {

    fun pathsOf(schema: FieldSchema): List<String> {
        val paths = mutableListOf<String>()
        schema.fields.forEach { (id, definition) ->
            collect(prefix = id.value, definition = definition, into = paths)
        }
        return paths
    }

    private fun collect(prefix: String, definition: FieldDefinition, into: MutableList<String>) {
        if (definition.fields.isEmpty()) {
            into += prefix
            return
        }
        definition.fields.forEach { (id, child) ->
            collect(prefix = "$prefix.${id.value}", definition = child, into = into)
        }
    }
}
