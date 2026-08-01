package ui.autocompletion

import ruleengine.core.domain.FieldPathResolution
import ruleengine.core.domain.FieldPathResolver
import ruleengine.core.domain.dto.field.FieldDefinition
import ruleengine.core.domain.dto.field.FieldSchema

/** Field-name resolution shared by the completion builders. */
internal fun FieldDefinition.getDisplayId(): String {
    return alias ?: id.value
}

internal fun resolveFieldByIdentifier(
    identifier: String,
    schema: FieldSchema?
): FieldDefinition? {
    val resolution = FieldPathResolver.resolve(
        identifier = identifier,
        schema = schema ?: return null
    )
    return (resolution as? FieldPathResolution.Resolved)?.definition
}
