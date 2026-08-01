package ui.workbench.model
/**
 * An item selected in the inspector panel.
 */
sealed interface InspectorItem {
    /** A field definition from the field schema. */
    data class Field(val id: String) : InspectorItem

    /** An action definition from the action schema. */
    data class Action(val name: String) : InspectorItem

    /** A parsed rule. */
    data class Rule(val id: String) : InspectorItem

    /** A single condition row inside Builder mode. */
    data class Condition(val conditionId: String) : InspectorItem

    /** A manifest project. */
    data class Manifest(val name: String) : InspectorItem
}
