package ui.dsl.model
/**
 * The structural section of the rule DSL where the cursor is located.
 *
 * [THEN], [ELSE] and [NOT_EXISTS] are kept apart even though all three offer the same completions: a
 * caller that wants to say which branch the cursor is in — a status bar, a diagnostic — cannot recover
 * that once they are merged, and merging them is a one-line `in BRANCHES` at each use.
 */
enum class DslSection {
    TOP_LEVEL,
    RULE_HEADER,
    WHEN,
    THEN,
    ELSE,
    NOT_EXISTS,
    ;

    /** True for the sections that hold a rule's output clauses, which all take the same contents. */
    fun isBranch(): Boolean {
        return this == THEN || this == ELSE || this == NOT_EXISTS
    }
}
