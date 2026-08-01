package ui.project

/** The kinds of file a manifest can reference, used to label paths in messages and badges. */
enum class ProjectFileKind(val label: String) {
    SCHEMA(label = "schema"),
    ACTIONS(label = "actions"),
    RULE(label = "rule"),
}
