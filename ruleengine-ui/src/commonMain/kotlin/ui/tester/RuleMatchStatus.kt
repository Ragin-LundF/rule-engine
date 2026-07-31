package ui.tester

/**
 * How close a rule came to firing.
 *
 * Three states rather than matched/not-matched, because a rule that had one condition hold and one
 * fail nearly fired, while a rule that had nothing hold never came close. Both are "no match", but
 * only the first is worth a second look when a rule does not behave as its author expected.
 */
enum class RuleMatchStatus {
    /** The rule fired and emitted its actions. */
    MATCHED,

    /** The rule did not fire, but at least one of its conditions held. */
    PARTIAL,

    /** The rule did not fire and none of its conditions held. */
    NO_MATCH,
}
