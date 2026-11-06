package ruleengine.intellij.yaml

import ruleengine.intellij.yaml.model.RuleYamlKind

/** Recognises a rule project YAML file by its top-level key; no file naming convention is needed. */
object RuleYamlKinds {
    private val PATTERNS = RuleYamlKind.entries.associateWith { kind ->
        Regex(pattern = "^${kind.topLevelKey}\\s*:", option = RegexOption.MULTILINE)
    }

    fun kindOf(text: CharSequence): RuleYamlKind? {
        return RuleYamlKind.entries.firstOrNull { kind -> PATTERNS.getValue(kind).containsMatchIn(input = text) }
    }
}
