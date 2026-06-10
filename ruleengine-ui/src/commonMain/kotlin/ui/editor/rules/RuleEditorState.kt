
package ui.editor.rules

data class RuleEditorState(
    val schemaText: String = "",
    val actionSchemaText: String = "",
    val manifestText: String = "",
    val ruleText: String = "",
    val status: String = "Ready",
    val statusKind: StatusKind = StatusKind.IDLE,
    val viewMode: EditorViewMode = EditorViewMode.CODE,
)


