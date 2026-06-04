package ruleengine.core.domain

enum class ActionArgType { STRING, INTEGER, DECIMAL }

data class ActionDefinition(
    val name: String,
    val argTypes: List<ActionArgType>
)

data class ActionSchema(
    val actions: Map<String, ActionDefinition>
)

object DefaultActionSchema {
    val basic = ActionSchema(actions = mapOf(
        "label" to ActionDefinition(name = "label", argTypes = listOf(ActionArgType.STRING)),
        "category" to ActionDefinition(name = "category", argTypes = listOf(ActionArgType.STRING)),
        "flag" to ActionDefinition(name = "flag", argTypes = listOf(ActionArgType.STRING)),
        "score" to ActionDefinition(name = "score", argTypes = listOf(ActionArgType.INTEGER))
    ))
}

