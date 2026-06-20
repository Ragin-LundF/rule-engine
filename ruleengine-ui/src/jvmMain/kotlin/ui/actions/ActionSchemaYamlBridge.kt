package ui.actions

import ruleengine.core.domain.ActionArgType
import ruleengine.core.domain.ActionDefinition
import ruleengine.core.domain.ActionSchema
import ruleengine.schema.ActionSchemaLoader

/**
 * Converts between [ActionEditorState] and YAML text understood by [ActionSchemaLoader].
 */
object ActionSchemaYamlBridge {

    /**
     * Parses [yaml] into an [ActionEditorState].
     * Returns [ActionEditorState.Empty] with [isReadOnly] = false on blank input.
     */
    fun fromYaml(yaml: String): ActionEditorState {
        if (yaml.isBlank()) return ActionEditorState.Empty

        val schema: ActionSchema = runCatching {
            ActionSchemaLoader.loadFromString(content = yaml)
        }.getOrElse {
            return ActionEditorState.Empty.copy(isReadOnly = true)
        }

        return ActionEditorState(
            actions = schema.actions.values.map { def ->
                EditableAction(
                    name = def.name,
                    argTypes = def.argTypes.map { it.name.lowercase() },
                    purpose = "",
                )
            },
            isReadOnly = false,
        )
    }

    /**
     * Serialises [state] to YAML text that [ActionSchemaLoader.loadFromString] can reload.
     */
    fun toYaml(state: ActionEditorState): String {
        if (state.actions.isEmpty()) return ""

        val sb = StringBuilder()
        sb.appendLine("actions:")
        state.actions.forEach { action ->
            sb.appendLine("  ${action.name}:")
            if (action.purpose.isNotBlank()) {
                sb.appendLine("    purpose: \"${escape(action.purpose)}\"")
            }
            if (action.argTypes.isNotEmpty()) {
                sb.appendLine("    argTypes:")
                action.argTypes.forEach { argType ->
                    sb.appendLine("      - $argType")
                }
            } else {
                sb.appendLine("    argTypes: []")
            }
        }
        return sb.toString().trim()
    }

    internal fun toActionSchema(state: ActionEditorState): ActionSchema {
        val actions = state.actions.associate { action ->
            action.name to ActionDefinition(
                name = action.name,
                argTypes = action.argTypes.mapNotNull { parseArgType(it) },
            )
        }
        return ActionSchema(actions = actions)
    }

    private fun parseArgType(s: String): ActionArgType? = when (s.lowercase()) {
        "string" -> ActionArgType.STRING
        "integer", "int" -> ActionArgType.INTEGER
        "decimal", "number" -> ActionArgType.DECIMAL
        else -> null
    }

    private fun escape(s: String): String = s.replace("\\", "\\\\").replace("\"", "\\\"")
}
