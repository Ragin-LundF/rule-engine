package ruleengine.core.domain.dto.action

data class ActionDefinition(
    val name: String,
    val argTypes: List<ActionArgType>
)
