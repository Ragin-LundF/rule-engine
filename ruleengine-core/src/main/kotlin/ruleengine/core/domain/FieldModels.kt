package ruleengine.core.domain

import java.math.BigDecimal
import java.nio.file.Path

@JvmInline
value class FieldId(val value: String)

@JvmInline
value class OperatorId(val value: String)

@JvmInline
value class NormalizerId(val value: String)

enum class FieldType {
    TEXT,
    INTEGER,
    DECIMAL,
    BOOLEAN,
    STRING_SET,
    DATE
}

data class FieldDefinition(
    val id: FieldId,
    val type: FieldType,
    val normalizers: List<NormalizerId> = emptyList(),
    val operators: Set<OperatorId> = emptySet()
)

data class FieldSchema(
    val name: String,
    val fields: Map<FieldId, FieldDefinition>
)

data class RuleAction(
    val name: String,
    val arguments: List<Any?> = emptyList()
)

data class RuleMatch(
    val ruleId: String,
    val actions: List<RuleAction>
)

data class EvaluationResult(
    val matches: List<RuleMatch>,
    val trace: Any? = null
)

