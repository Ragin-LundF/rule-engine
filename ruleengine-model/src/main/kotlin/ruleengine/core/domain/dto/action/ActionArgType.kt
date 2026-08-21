package ruleengine.core.domain.dto.action

/**
 * What an action's single argument is written as.
 *
 * A load-time vocabulary rather than a runtime one: nothing downstream of the validator reads it, so it
 * exists to catch a rule that writes the wrong kind of value and to tell the editor what to offer.
 *
 * [VARIABLE_STRING] and [VARIABLE_LIST] say the argument is a `$name` reference rather than a literal.
 * Passing a variable to an action has always worked and is unaffected by this — declaring one of these
 * types is what turns "anything goes here" into a checked contract the editor can also complete against.
 */
enum class ActionArgType {
    /** Text in double quotes: `label "rent"`. */
    STRING,

    /** A whole number, unquoted: `score 10`. */
    INTEGER,

    /** A number with decimal places, unquoted: `threshold 0.75`. */
    DECIMAL,

    /**
     * A reference to a variable published with `set`: `reason $why`.
     *
     * The value reaches the consumer as whatever the `set` expression produced, or `null` when no rule
     * that ran published it.
     */
    VARIABLE_STRING,

    /**
     * A reference to a list variable accumulated with `add`: `topics $topics`.
     *
     * The value reaches the consumer as a `List`, or `null` when no rule that ran added to it. Kept
     * apart from [VARIABLE_STRING] because the engine already keeps the two clauses apart — a name is
     * either a plain value or a list, never both — so the declaration can be checked against the clause
     * that writes it.
     */
    VARIABLE_LIST,
    ;

    /** True for the types whose argument must be a `$name` reference rather than a literal. */
    fun isVariableReference(): Boolean {
        return this == VARIABLE_STRING || this == VARIABLE_LIST
    }
}
