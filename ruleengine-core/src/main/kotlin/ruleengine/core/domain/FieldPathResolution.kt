package ruleengine.core.domain

import ruleengine.core.domain.dto.field.FieldDefinition
import ruleengine.core.domain.dto.field.FieldId
import ruleengine.core.domain.dto.field.FieldSchema
import ruleengine.core.domain.dto.field.FieldType

/** Outcome of resolving a dotted field identifier against a [FieldSchema]. */
sealed interface FieldPathResolution {

    /**
     * The identifier names a declared field.
     *
     * [id] is the canonical dotted path with every alias replaced by its declared name, which is the
     * identifier the compiler and [ruleengine.evaluator.context.PreparedRuleContext] agree on.
     */
    data class Resolved(val id: FieldId, val definition: FieldDefinition) : FieldPathResolution

    /**
     * The path reads through a [FieldType.COLLECTION], which a plain condition cannot do: a projection over a
     * list yields many values and there is nothing to compare a single literal against.
     *
     * [collectionPath] is the canonical path of the collection so the diagnostic can name it and point to
     * an aggregate function instead.
     */
    data class CrossesCollection(val collectionPath: String) : FieldPathResolution

    /** No declared field or alias matches the identifier. */
    data object Unknown : FieldPathResolution
}
