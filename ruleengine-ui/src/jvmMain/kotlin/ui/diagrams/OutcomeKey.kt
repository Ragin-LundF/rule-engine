package ui.diagrams

import ruleengine.dsl.ast.ActionAst
import ruleengine.dsl.ast.BooleanLiteral
import ruleengine.dsl.ast.ListLiteral
import ruleengine.dsl.ast.LiteralAst
import ruleengine.dsl.ast.NumberLiteral
import ruleengine.dsl.ast.StringLiteral

/**
 * The output identity of an action, as the engine sees it and as a reader groups it.
 *
 * These two are not the same thing and the outcome map must not blur them:
 *
 * - [staticOutputKey] mirrors `RuleEngine.staticOutputKeys` exactly. It is the key the engine buckets
 *   rules by when `shortCircuitByOutput` is enabled, and it uses the **whole** first argument. So
 *   `assessment "transit:green"` and `assessment "transit:red"` are two different buckets and the
 *   rules producing them never compete with each other.
 * - [displayFamily] is a reading aid with no runtime meaning. It collapses the `:`-separated prefix
 *   of the value so a human sees the `assessment:transit` family at a glance.
 *
 * A rule that emits several actions belongs to one bucket per action, which is also what
 * `RuleEngine.staticOutputKeys` does.
 */
object OutcomeKey {

    /**
     * The `actionName:firstStaticArgument` key, or `null` when the action produces no static output —
     * no arguments, or a first argument that is only resolved at evaluation time.
     */
    fun staticOutputKey(action: ActionAst): String? {
        val value = staticFirstArgument(action = action) ?: return null
        return "${action.name}:$value"
    }

    /**
     * The display grouping: `actionName:prefix` when the first argument carries a `:`-separated
     * prefix, otherwise just the action name. `null` when the action produces no static output.
     */
    fun displayFamily(action: ActionAst): String? {
        val value = staticFirstArgument(action = action) ?: return null
        if (!value.contains(char = ':')) {
            return action.name
        }
        return "${action.name}:${value.substringBefore(delimiter = ':')}"
    }

    private fun staticFirstArgument(action: ActionAst): String? {
        val literal = action.arguments.firstOrNull() ?: return null
        return staticValue(literal = literal)
    }

    /**
     * The compile-time value of an action argument, rendered the way the engine's key interpolation
     * renders it. Mirrors `Compiler.staticArgumentValue`, including the fact that a list argument
     * ends up as Kotlin's `List.toString()` form inside the key.
     *
     * Returns `null` for literals that never become a `CompiledActionArgument.Static`: an extraction
     * reference is resolved per evaluation, and a `between` range is rejected by the compiler as an
     * action argument.
     */
    private fun staticValue(literal: LiteralAst): String? {
        return when (literal) {
            is StringLiteral -> literal.value
            is NumberLiteral -> literal.value
            is BooleanLiteral -> literal.value.toString()
            is ListLiteral -> renderList(literal = literal)
            else -> null
        }
    }

    private fun renderList(literal: ListLiteral): String? {
        val items = literal.items.map { item -> staticValue(literal = item) ?: return null }
        return items.joinToString(separator = ", ", prefix = "[", postfix = "]")
    }
}
