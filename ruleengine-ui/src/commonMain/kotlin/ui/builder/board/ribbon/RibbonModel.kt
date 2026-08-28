package ui.builder.board.ribbon

import ui.builder.board.ribbon.model.RibbonCard
import ui.builder.board.ribbon.model.RibbonGroup
import ui.builder.model.BuilderConditionNode
import ui.builder.model.BuilderOperand
import ui.builder.model.BuilderRule
import ui.workbench.model.catalog.RuleTreeFile

/**
 * Builds the ribbon from the rules already loaded, in the order they run.
 *
 * All of this is derived. The board holds no state of its own about the run — it reads the same
 * `BuilderRule` list and the same file grouping the tree panel uses, so the ribbon cannot disagree with
 * the rule the canvas below it is showing. Anything the board needed to remember separately would be a
 * second source of truth about evaluation order, which is the one thing the board exists to show.
 */
object RibbonModel {

    /** The `$` prefix that marks a rule output variable rather than a schema field. */
    private const val VARIABLE_PREFIX = "$"

    /**
     * The groups, in manifest order, with ordinals running continuously across files.
     *
     * Continuous numbering is deliberate: the ordinal is the rule's position in the *run*, and the run
     * does not restart at each file. Numbering per file would make `⊘` on rule 3 of file 1 look like it
     * stops three rules rather than everything after it.
     */
    fun groups(files: List<RuleTreeFile>, rules: List<BuilderRule>): List<RibbonGroup> {
        val byId = rules.associateBy { rule -> ruleIdOf(rule = rule) }
        var ordinal = 0

        return files.map { file ->
            RibbonGroup(
                relativePath = file.relativePath,
                cards = file.rules.map { catalogRule ->
                    ordinal++
                    cardFor(ordinal = ordinal, ruleId = catalogRule.id, rule = byId[catalogRule.id])
                },
            )
        }
    }

    private fun ruleIdOf(rule: BuilderRule): String = when (rule) {
        is BuilderRule.Supported -> rule.id
        is BuilderRule.Unsupported -> rule.id
        BuilderRule.None -> ""
    }

    /**
     * The card for one rule.
     *
     * A rule the Builder cannot render still gets a card. Leaving it out would make the ribbon claim an
     * evaluation order that skips it, which is worse than a card that admits it cannot say what the rule
     * reads: the rule still runs, and still stops the run if it says `stop`.
     */
    private fun cardFor(ordinal: Int, ruleId: String, rule: BuilderRule?): RibbonCard {
        val supported = rule as? BuilderRule.Supported
            ?: return RibbonCard(
                ordinal = ordinal,
                ruleId = ruleId,
                reads = emptyList(),
                sets = emptyList(),
                halts = false,
                locked = true,
            )

        return RibbonCard(
            ordinal = ordinal,
            ruleId = ruleId,
            reads = readsOf(rule = supported),
            sets = setsOf(rule = supported),
            halts = supported.stopOnThen || supported.stopOnElse || supported.stopOnNotExists,
            locked = false,
        )
    }

    /** Variables named anywhere in the rule's conditions, deduplicated, in first-appearance order. */
    fun readsOf(rule: BuilderRule.Supported): List<String> {
        val found = mutableListOf<String>()
        collectReads(nodes = rule.conditionNodes, into = found)
        return found.distinct()
    }

    /**
     * Variables the rule assigns, across all three branches.
     *
     * Order is `then`, `else`, `not_exists` — the DSL's own order. Only one branch runs, so no branch's
     * assignment shadows another's at evaluation time; a name in two branches is one variable set to
     * different values on different paths, which is exactly one entry on the card.
     */
    fun setsOf(rule: BuilderRule.Supported): List<String> {
        return (rule.variables + rule.elseVariables + rule.notExistsVariables)
            .map { variable -> variable.name.removePrefix(prefix = VARIABLE_PREFIX) }
            .filter { name -> name.isNotBlank() }
            .distinct()
    }

    private fun collectReads(nodes: List<BuilderConditionNode>, into: MutableList<String>) {
        nodes.forEach { node ->
            when (node) {
                is BuilderConditionNode.Condition -> addIfVariable(name = node.field, into = into)

                is BuilderConditionNode.Comparison -> {
                    collectOperandReads(operand = node.left, into = into)
                    collectOperandReads(operand = node.right, into = into)
                }

                is BuilderConditionNode.Group -> collectReads(nodes = node.nodes, into = into)
            }
        }
    }

    /**
     * Every variable inside [operand], however deep.
     *
     * Recurses through arguments and terms because a variable reaches a rule at any depth —
     * `abs($budget - sum(invoices.amount))` reads `$budget` — and a ribbon that showed only top-level
     * reads would draw a flow arrow for some uses of a variable and not others.
     */
    private fun collectOperandReads(operand: BuilderOperand, into: MutableList<String>) {
        when (operand) {
            is BuilderOperand.FieldRef -> operand.path.firstOrNull()?.let { step ->
                addIfVariable(name = step.name, into = into)
            }

            is BuilderOperand.Aggregate -> operand.path.firstOrNull()?.let { step ->
                addIfVariable(name = step.name, into = into)
            }

            is BuilderOperand.Call -> operand.args.forEach { arg ->
                collectOperandReads(operand = arg, into = into)
            }

            is BuilderOperand.Calc -> operand.terms.forEach { term ->
                collectOperandReads(operand = term.operand, into = into)
            }

            // A literal or a written-out list names nothing.
            is BuilderOperand.Literal, is BuilderOperand.ListLiteral -> Unit
        }
    }

    private fun addIfVariable(name: String, into: MutableList<String>) {
        if (name.startsWith(prefix = VARIABLE_PREFIX) && name.length > 1) {
            into.add(name.removePrefix(prefix = VARIABLE_PREFIX))
        }
    }
}
