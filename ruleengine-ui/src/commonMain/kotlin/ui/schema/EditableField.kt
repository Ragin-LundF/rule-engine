package ui.schema

import ruleengine.core.domain.dto.FieldType

/**
 * Editable representation of a single field in the visual schema editor.
 *
 * All fields are plain strings so the composable layer stays in commonMain.
 * The bridge layer (jvmMain) converts between this model and [ruleengine.core.domain.dto.FieldSchema].
 */
data class EditableField(
    val path: String = "",
    val alias: String = "",
    val type: FieldType = FieldType.TEXT,
    /**
     * Date pattern for a [FieldType.DATE] / [FieldType.DATE_TIME] field, e.g. `dd.MM.yyyy`.
     * Empty means ISO-8601. Always empty for every other type.
     */
    val format: String = "",
    val normalizers: List<String> = emptyList(),
    val operators: List<String> = emptyList(),
    /**
     * Members of a [FieldType.COLLECTION] or [FieldType.OBJECT] field.
     *
     * Recursive, mirroring [ruleengine.core.domain.dto.FieldDefinition.fields], so a collection of objects
     * that themselves contain collections is expressible to any depth.
     */
    val fields: List<EditableField> = emptyList(),
)
