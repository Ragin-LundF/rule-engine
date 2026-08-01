package ruleengine.core.domain

import ruleengine.core.domain.dto.action.ActionArgType
import ruleengine.core.domain.dto.action.ActionDefinition
import ruleengine.core.domain.dto.action.ActionSchema

object DefaultActionSchema {
    val basic = ActionSchema(actions = mapOf(
        "label" to ActionDefinition(name = "label", argTypes = listOf(ActionArgType.STRING)),
        "category" to ActionDefinition(name = "category", argTypes = listOf(ActionArgType.STRING)),
        "flag" to ActionDefinition(name = "flag", argTypes = listOf(ActionArgType.STRING)),
        "score" to ActionDefinition(name = "score", argTypes = listOf(ActionArgType.INTEGER))
    ))
}
