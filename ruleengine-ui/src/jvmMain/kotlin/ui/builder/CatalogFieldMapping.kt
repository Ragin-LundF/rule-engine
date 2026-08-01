package ui.builder

import ruleengine.core.domain.dto.field.FieldDefinition
import ui.builder.model.catalog.CatalogFieldInfo

/**
 * Converts an engine [FieldDefinition] into the platform-neutral [CatalogFieldInfo] the Builder uses.
 *
 * Recurses into nested members so a collection of objects containing collections arrives in
 * `commonMain` with its full shape, letting path pickers descend to any declared depth.
 */
fun FieldDefinition.toCatalogFieldInfo(): CatalogFieldInfo = CatalogFieldInfo(
    id = id.value,
    type = type.name.lowercase(),
    operators = operators.map { it.value },
    format = format ?: "",
    nestedFields = fields.values.map { it.toCatalogFieldInfo() },
)
