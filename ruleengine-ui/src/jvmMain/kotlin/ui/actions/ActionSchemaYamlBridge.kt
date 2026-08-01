package ui.actions

import ruleengine.core.domain.dto.ActionSchema
import ruleengine.schema.ActionSchemaLoader
import ui.util.YamlScalars

/**
 * Converts between [ActionEditorState] and YAML text understood by [ActionSchemaLoader].
 */
object ActionSchemaYamlBridge {

    /**
     * Parses a YAML string into an instance of [ActionEditorState].
     * If the YAML is invalid or cannot be parsed, a read-only empty state is returned.
     *
     * @param yaml The YAML string that represents an action schema.
     * @return An [ActionEditorState] containing the parsed actions or a read-only empty state if parsing fails.
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
     * Converts the given [ActionEditorState] object to its YAML string representation.
     *
     * @param state The state of the action editor containing a list of actions.
     * @return A YAML string representing the actions in the provided state. Returns an
     * empty string if there are no actions.
     */
    fun toYaml(state: ActionEditorState): String {
        if (state.actions.isEmpty()) return ""

        val sb = StringBuilder()
        sb.appendLine(value = "actions:")
        state.actions.forEach { action ->
            sb.appendLine(value = "  ${action.name}:")
            if (action.purpose.isNotBlank()) {
                sb.appendLine(value = "    purpose: \"${YamlScalars.escape(action.purpose)}\"")
            }
            if (action.argTypes.isNotEmpty()) {
                sb.appendLine(value = "    argTypes:")
                action.argTypes.forEach { argType ->
                    sb.appendLine(value = "      - $argType")
                }
            } else {
                sb.appendLine(value = "    argTypes: []")
            }
        }
        return sb.toString().trim()
    }


}
