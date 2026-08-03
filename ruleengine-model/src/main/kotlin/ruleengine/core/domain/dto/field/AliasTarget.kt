package ruleengine.core.domain.dto.field

/**
 * One aliased field, resolved to the canonical path a rule compiles down to.
 *
 * [collectionPath] is the dotted path of the nearest `collection` ancestor between the schema root and
 * this field, or `null` when the field is reachable without crossing one. A bare alias whose
 * [collectionPath] is set can only be written in its path position — a plain condition on it projects one
 * value per element, exactly as the dotted spelling would.
 */
data class AliasTarget(
    val alias: String,
    val path: FieldId,
    val definition: FieldDefinition,
    val collectionPath: String?,
)
