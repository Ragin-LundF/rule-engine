package ruleengine.dsl.ast

/**
 * Which clause wrote a variable: `set` replaces the value, `add` appends to a list.
 *
 * The two kinds share [VariableAssignmentAst] and therefore one list per branch, which keeps `set`
 * and `add` in source order and lets the engine apply them with the same loop. They differ in how the
 * validator scopes them: a name written by [ADD] is readable in the condition of the very rule that
 * writes it, because an accumulator has an identity element — see
 * [ruleengine.compiler.support.VariableScopeValidator].
 *
 * A single name must be written by one kind only; mixing them is a load-time error.
 */
enum class AssignmentKindAst {
    /** `set <name> = <expression>` — publishes the expression's value, replacing any previous one. */
    SET,

    /** `add <expression> to <name>` — appends to a list variable, ignoring a value already present. */
    ADD,
}
