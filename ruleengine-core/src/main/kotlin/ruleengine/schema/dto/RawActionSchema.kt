package ruleengine.schema.dto

data class RawActionSchema(
    val actions: Map<String, RawActionDef> = emptyMap()
)
