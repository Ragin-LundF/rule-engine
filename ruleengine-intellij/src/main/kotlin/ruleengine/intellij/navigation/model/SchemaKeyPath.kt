package ruleengine.intellij.navigation.model

/**
 * Where a field is declared in the schema YAML: the chain of keys down to it (`fields`, `customer`,
 * `fields`, `tier`). [viaAlias] is true when the rule spelled the field by its alias.
 */
data class SchemaKeyPath(
    val keys: List<String>,
    val viaAlias: Boolean,
)
