package ruleengine.core.domain.dto

data class ActionDefinition(
    val name: String,
    val argTypes: List<ActionArgType>
)
