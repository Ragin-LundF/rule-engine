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
        // has not given a false branch must not get the keyword either. Same for `not_exists`, which
        // also has to come after `else` — the parser reads the blocks in that order.
        if (state.hasElseBranch) {
            appendBranch(
                sb = sb,
                keyword = "else",
                variables = state.elseVariables,
                actions = state.elseActions,
                stop = state.stopOnElse,
            )
        }
        if (state.hasNotExistsBranch) {
            appendBranch(
                sb = sb,
                keyword = "not_exists",
                variables = state.notExistsVariables,
                actions = state.notExistsActions,
                stop = state.stopOnNotExists,
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

    /**
     * The exact text [generate] writes for one leaf row, without its indent or join.
     *
     * Exists so the dock can highlight the line belonging to the selected row by matching the text
     * rather than by counting lines. Counting would need the generator to report provenance for every
     * line it writes, and would silently point at the wrong row the first time anything about the
     * layout changed. Matching the generator's own output cannot drift from it, because it *is* it.
     *
     * Returns null for a group: a group is a bracket spanning several lines, not a line.
     */
    internal fun renderRow(node: MutableConditionNode): String? = when (node) {
        is MutableConditionNode.Leaf -> renderConditionLine(cond = node.inner)
        is MutableConditionNode.ComparisonLeaf -> renderComparisonLine(comparison = node.inner)
        is MutableConditionNode.Group -> null
    }

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
        val extraction = action.extraction
        val args = action.arguments.joinToString(" ") { argument ->
            if (extraction != null && EXTRACTION_REF.matches(input = argument.trim())) {
                argument.trim()
            } else {
                quoteIfNeeded(argument)
            }
        }
        val call = if (args.isBlank()) action.name else "${action.name} $args"
        if (extraction == null) {
            return call
        }
        // The pattern goes through the same escaping a description does: the lexer treats a backslash
        // as a generic escape, so a `\d` written raw would be read as a plain `d` on the next parse.
        return "extract ${extraction.sourceField} " +
                "regex(\"${escapeStringLiteral(text = extraction.pattern)}\", ${extraction.groupIndex}) " +
                call
    }

    /**
     * An extraction reference, i.e. `$` followed by digits only.
     *
     * Emitted bare while [OperandText.quoteUnlessNumeric] keeps it quoted, and both are right: outside
     * an `extract` clause `$1` is a value the author means literally — a `$100` price — and inside one
     * it is the capture group, which the parser reads as a reference only when unquoted.
     */
    private val EXTRACTION_REF = Regex(pattern = """\$\d+""")

    /**
     * Wraps [value] in double quotes if it is not already quoted and is not a
     * plain number or boolean literal.
     */
    private fun quoteIfNeeded(value: String): String = OperandText.quoteUnlessNumeric(value = value)
}
