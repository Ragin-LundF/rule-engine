package ruleengine.core.domain.dto

/**
 * What a rule's condition answered.
 *
 * A comparison needs data on both sides, and a record does not always carry it. Two-valued logic has
 * to call that case something, and it called it [FALSE] — which reads as "the condition does not hold"
 * when the truth is "the condition could not be decided". [UNKNOWN] is that third answer, and it is
 * what lets a rule declare a `not_exists` branch for missing data instead of quietly taking `else`.
 *
 * Propagation is Kleene's: `FALSE and UNKNOWN` is `FALSE` because nothing the missing operand could
 * have said would change it, `TRUE or UNKNOWN` is `TRUE` for the same reason, and `not UNKNOWN` is
 * `UNKNOWN`. Only a condition whose *own* answer is [UNKNOWN] reaches the `not_exists` branch.
 *
 * Lives in `ruleengine-model` for the same reason [RuleBranch] does: the UI's `commonMain` has to name
 * a verdict — to render a trace node, to label a result — without depending on `ruleengine-core`.
 */
enum class ConditionVerdict {
    TRUE,
    FALSE,

    /**
     * The condition read something the record does not carry: an absent field, or a variable no rule
     * has published.
     *
     * Born in exactly two places — a leaf condition whose prepared value is absent, and a comparison
     * with a missing operand. Functions keep their own contract: `count` over a missing collection is
     * still `0`, and `every` over one is still vacuously true, because those answers are about the
     * elements rather than about whether the data arrived.
     */
    UNKNOWN,
    ;

    /**
     * The verdict as the two-valued answer the engine gave before `not_exists` existed.
     *
     * [UNKNOWN] becomes `false`, which is what every leaf returned for absent data. Used where a rule
     * declares no `not_exists` branch, and by consumers of the trace that only ask "did it hold".
     */
    fun isTrue(): Boolean {
        return this == TRUE
    }

    companion object {

        /** The verdict of a test that had its data and simply answered yes or no. */
        fun of(value: Boolean): ConditionVerdict {
            return if (value) TRUE else FALSE
        }
    }
}
