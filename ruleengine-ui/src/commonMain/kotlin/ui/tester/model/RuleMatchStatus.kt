package ui.tester.model

/**
 * How close a rule came to firing.
 *
 * More than matched/not-matched, because a rule that had one condition hold and one fail nearly fired,
 * while a rule that had nothing hold never came close. Both are "no match", but only the first is worth
 * a second look when a rule does not behave as its author expected.
 */
enum class RuleMatchStatus {
    /** The rule fired and emitted its actions. */
    MATCHED,

    /**
     * The rule's condition did not hold, but it declares an `else` block, so it emitted that instead.
     *
     * Distinct from [MATCHED] because the condition was false, and distinct from [NO_MATCH] because the
     * rule still produced output — reporting either would misread the run.
     */
    ELSE_MATCHED,

    /**
     * The rule's condition could not be decided, and it declares a `not_exists` block, so it emitted
     * that instead.
     *
     * Distinct from [ELSE_MATCHED] because the condition was not false — the record carried no data to
     * answer it — and distinct from [NO_MATCH] because the rule still produced output.
     */
    NOT_EXISTS_MATCHED,

    /** The rule did not fire, but at least one of its conditions held. */
    PARTIAL,

    /**
     * The rule was never reached: an earlier rule's `stop` ended the run.
     *
     * Not a kind of "no match" — the rule was not tested at all, so nothing is known about whether it
     * would have fired, and reporting it as not matching would be a claim the run never made.
     */
    NOT_EVALUATED,

    /** The rule did not fire and none of its conditions held. */
    NO_MATCH,
}
