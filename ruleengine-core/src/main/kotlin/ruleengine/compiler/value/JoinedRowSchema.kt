package ruleengine.compiler.value

import ruleengine.core.domain.dto.field.FieldDefinition
import ruleengine.core.domain.dto.field.FieldId
import ruleengine.core.domain.dto.field.FieldSchema
import ruleengine.core.domain.dto.field.FieldType
import ruleengine.dsl.ast.path.JoinSegmentAst

/**
 * The declared shape of the rows a `joinByKey` produces: a collection whose elements carry the key
 * and one decimal per source alias.
 *
 * Validation and compilation both continue a path from here, so a filter on the rows is checked and
 * compiled against the same members. The key takes its declaration — type and normalizers — from the
 * first source that declares it. When none does, the row declares no members at all, which keeps the
 * filter as permissive as every other path over undeclared members.
 */
internal object JoinedRowSchema {

    fun of(segment: JoinSegmentAst, schema: FieldSchema): FieldDefinition {
        val key = segment.key
        val keyDefinition = key?.let { name -> declaredKey(segment = segment, key = name, schema = schema) }
        val members = if (keyDefinition == null) {
            emptyMap()
        } else {
            val aliases = segment.sources.map { source ->
                FieldDefinition(id = FieldId(value = source.alias), type = FieldType.DECIMAL)
            }
            (listOf(keyDefinition) + aliases).associateBy { definition -> definition.id }
        }
        return FieldDefinition(id = FieldId(value = key.orEmpty()), type = FieldType.COLLECTION, fields = members)
    }

    private fun declaredKey(segment: JoinSegmentAst, key: String, schema: FieldSchema): FieldDefinition? {
        return segment.sources.firstNotNullOfOrNull { source ->
            val collectionPath = source.path.path.dropLast(n = 1)
            FunctionCallValidator.memberFields(path = collectionPath, schema = schema)[FieldId(value = key)]
        }
    }
}
