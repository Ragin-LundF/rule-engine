package ruleengine.intellij.yaml.model

/** The three YAML files of a rule project, each told apart by its top-level key. */
enum class RuleYamlKind(val topLevelKey: String, val displayName: String) {
    MANIFEST(topLevelKey = "entries", displayName = "Rule engine manifest"),
    FIELD_SCHEMA(topLevelKey = "fields", displayName = "Rule engine field schema"),
    ACTION_SCHEMA(topLevelKey = "actions", displayName = "Rule engine action schema"),
}
