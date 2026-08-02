package ui.builder

import ruleengine.dsl.ast.AssignmentKindAst
import ui.builder.model.mutable.BuilderEditorState
import ui.builder.model.mutable.MutableBuilderAction
import ui.builder.model.mutable.MutableBuilderComparison
import ui.builder.model.mutable.MutableBuilderCondition
import ui.builder.model.mutable.MutableBuilderVariable
import ui.builder.model.mutable.MutableConditionNode


/**
 * Generates valid rule DSL text from a [BuilderEditorState].
 *
 * The canonical saved format is always DSL text; this object is the only place
 * that converts Builder state back to text. It must never be called when
 * [BuilderEditorState.isLocked] is true.
 *
 * Output format mirrors the rule DSL spec:
 * ```
 * rule "id" {
 *   description "what the rule is for"
 *   when
 *     field operator value
 *     and field operator value
 *   then
 *     action arg
 *   else
 *     action otherArg
 * }
 * ```
 *
 * The `else` block is written only when the rule has one; it is optional in the DSL and empty is not
 * a legal spelling of "absent".
 */
object BuilderToRuleDsl {

    /**
     * Generates DSL text for the given [state].
     * Returns null if the state is locked or has no conditions.
     */
    fun generate(state: BuilderEditorState): String? {
        if (state.isLocked) return null
        if (state.ruleId.isBlank()) return null

        val sb = StringBuilder()
        sb.appendLine("rule \"${state.ruleId}\" {")

        // Emitted only when set: the clause is optional, and writing `description ""` for every
        // rule the user never described would add noise to files and defeat the validator warning.
        val description = state.description.trim()
        if (description.isNotEmpty()) {
            sb.appendLine("  description \"${escapeStringLiteral(text = description)}\"")
        }

        sb.appendLine("  when")

        renderNodes(
            nodes = state.conditionNodes,
            sb = sb,
            indent = 4,
        )

        appendBranch(
            sb = sb,
            keyword = "then",
            variables = state.variables,
            actions = state.actions,
            stop = state.stopOnThen,
        )
        // Emitted only when it has content: an empty `else` block does not parse, so a rule the author
        // has not given a false branch must not get the keyword either.
        if (state.hasElseBranch) {
            appendBranch(
                sb = sb,
                keyword = "else",
                variables = state.elseVariables,
                actions = state.elseActions,
                stop = state.stopOnElse,
            )
        }

        sb.appendLine("}")

        return sb.toString()
    }

    private fun appendBranch(
        sb: StringBuilder,
        keyword: String,
        variables: List<MutableBuilderVariable>,
        actions: List<MutableBuilderAction>,
        stop: Boolean,
    ) {
        sb.appendLine("  $keyword")
        // Assignments first: the engine applies them before it resolves the actions, so an action
        // reading `$name` must be written after the `set` that publishes it.
        variables.forEach { variable ->
            sb.appendLine("    ${renderVariable(variable)}")
        }
        actions.forEach { action ->
            sb.appendLine("    ${renderAction(action)}")
        }
        // Always last, which the parser requires. The Builder holds it as a flag rather than a row, so
        // there is no ordering to get wrong here however the author edited the branch.
        if (stop) {
            sb.appendLine("    stop")
        }
    }

    /**
     * Makes [text] safe to place inside a double-quoted DSL string literal.
     *
     * The lexer treats a backslash as a generic escape (`\X` yields `X`), so both the backslash and
     * the quote have to be escaped or a description containing either would terminate the literal
     * early and corrupt the rule file. Line breaks are collapsed rather than escaped: a literal may
     * span lines, but a multi-line clause makes the surrounding rule unreadable, and a description
     * is one sentence by definition.
     */
    private fun escapeStringLiteral(text: String): String {
        return text
            .replace(oldValue = "\\", newValue = "\\\\")
            .replace(oldValue = "\"", newValue = "\\\"")
            .replace(regex = Regex(pattern = "\\s*\\R\\s*"), replacement = " ")
    }

    // ── recursive node rendering ──────────────────────────────────────────────

    private fun renderNodes(
        nodes: List<MutableConditionNode>,
        sb: StringBuilder,
        indent: Int,
    ) {
        val indentStr = " ".repeat(indent)

        nodes.forEachIndexed { index, node ->
            val joinStr = if (index == 0) "" else " ${node.joinToPrevious.ifBlank { "and" }} "
            val notStr = if (node.negated) "not " else ""
            when (node) {
                is MutableConditionNode.Leaf -> {
                    sb.append("$indentStr$joinStr$notStr${renderConditionLine(node.inner)}\n")
                }
                is MutableConditionNode.ComparisonLeaf -> {
                    sb.append("$indentStr$joinStr$notStr${renderComparisonLine(node.inner)}\n")
                }
                is MutableConditionNode.Group -> {
                    sb.append("$indentStr$joinStr$notStr(\n")
                    renderNodes(
                        nodes = node.nodes.toList(),
                        sb = sb,
                        indent = indent + 4,
                    )
                    sb.appendLine("$indentStr)")
                }
            }
        }
    }

    // ── private helpers ──────────────────────────────────────────────────────

    private fun renderConditionLine(cond: MutableBuilderCondition): String {
        val op = cond.operator
        val body = when {
            OperatorOptions.isBetween(op) -> {
                val low = quoteIfNeeded(cond.value.trim())
                val high = quoteIfNeeded(cond.valueTo.trim())
                "${cond.field} between $low $high"
            }
            OperatorOptions.isList(op) -> {
                val items = cond.listItems.joinToString(", ") { quoteIfNeeded(it) }
                "${cond.field} $op [$items]"
            }
            else -> "${cond.field} $op ${quoteIfNeeded(cond.value)}"
        }
        return body + ignoreCaseSuffix(ignoreCase = cond.ignoreCase)
    }

    private fun renderComparisonLine(comparison: MutableBuilderComparison): String {
        val left = OperandText.toDsl(operand = comparison.left)
        val right = OperandText.toDsl(operand = comparison.right)
        return "$left ${comparison.operator} $right" + ignoreCaseSuffix(ignoreCase = comparison.ignoreCase)
    }

    private fun ignoreCaseSuffix(ignoreCase: Boolean): String = if (ignoreCase) " ignoreCase" else ""

    private fun renderVariable(variable: MutableBuilderVariable): String {
        val value = OperandText.toDsl(operand = variable.expression)
        return when (variable.kind) {
            AssignmentKindAst.SET -> "set ${variable.name} = $value"
            AssignmentKindAst.ADD -> "add $value to ${variable.name}"
        }
    }

    private fun renderAction(action: MutableBuilderAction): String {
        val args = action.arguments.joinToString(" ") { quoteIfNeeded(it) }
        return if (args.isBlank()) action.name else "${action.name} $args"
    }

    /**
     * Wraps [value] in double quotes if it is not already quoted and is not a
     * plain number or boolean literal.
     */
    private fun quoteIfNeeded(value: String): String = OperandText.quoteUnlessNumeric(value = value)
}
