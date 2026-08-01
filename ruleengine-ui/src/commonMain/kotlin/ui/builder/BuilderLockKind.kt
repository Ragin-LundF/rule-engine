package ui.builder

/**
 * Top-level mutable state for the Builder editor.
 *
 * Created from a [BuilderRule.Supported] snapshot; changes are serialised back to DSL text
 * via [BuilderToRuleDsl.generate] and written to the Code editor's text field.
 *
 * When [isLocked] is true the rule cannot be edited in Builder mode (unsupported syntax).
 */
/** Why Builder mode is unavailable, so the message can be chosen without matching on reason text. */
enum class BuilderLockKind {
    /** Not locked. */
    NONE,

    /** No rule is selected yet. */
    NO_RULE_SELECTED,

    /** The rule uses a construct the Builder cannot represent. */
    UNSUPPORTED_SYNTAX,
}
