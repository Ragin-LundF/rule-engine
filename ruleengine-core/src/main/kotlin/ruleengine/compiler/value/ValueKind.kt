package ruleengine.compiler.value

/**
 * What a value expression evaluates to, as far as the schema can say at load time.
 *
 * Coarser than [ruleengine.core.domain.dto.field.FieldType] on purpose: validation only needs to
 * know which operands may be compared, and a `date` and a `date_time` answer that question the same
 * way.
 *
 * [UNKNOWN] is not "invalid" — it means the kind could not be determined, as for a variable whose
 * value depends on which rule assigned it. Every operand check treats it as compatible rather than
 * guessing.
 */
internal enum class ValueKind {
    NUMERIC,
    TEXT,
    BOOLEAN,
    DATE,
    ARRAY,
    UNKNOWN
}
