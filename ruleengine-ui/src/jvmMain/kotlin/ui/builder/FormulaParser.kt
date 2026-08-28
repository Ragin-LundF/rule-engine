package ui.builder

import ruleengine.dsl.diagnostics.ParseException
import ruleengine.dsl.parser.Parser
import ui.builder.formula.model.FormulaResult
import ui.builder.model.BuilderRule

/**
 * Reads one condition typed as text and turns it into a Builder node.
 *
 * **No new parser.** The expression is wrapped in a synthetic rule and handed to the engine's own
 * `Parser`, then to the mappers that already exist. That is the entire implementation, and it is the
 * point: a second parser for "just conditions" would drift from the real one, and the drift would show
 * up as an expression the bar accepts and the engine rejects — which the Builder would then write to the
 * file.
 *
 * ```
 * "count(invoices) > 2"
 *   → rule "__fx" { when count(invoices) > 2 then __fx "x" }
 *   → Parser(...).parseRules().single()
 *   → RuleAstToBuilderMapper.map(...)          // the same mapper the file load uses
 *   → BuilderConditionNode
 * ```
 *
 * The synthetic rule needs a `then` block because a rule without one does not parse, and its action name
 * is deliberately unlikely: if the wrapper ever leaks into a diagnostic, `__fx` in the text says where
 * it came from.
 */
object FormulaParser {

    /** Reads [text] as a single condition. */
    fun parseCondition(text: String): FormulaResult {
        val trimmed = text.trim()
        if (trimmed.isEmpty()) {
            return FormulaResult.Failed(message = "Type a condition, for example `amount > 100`.")
        }

        val ast = try {
            Parser(input = wrap(condition = trimmed)).parseRules().single()
        } catch (exception: ParseException) {
            return FormulaResult.Failed(message = describe(exception = exception, condition = trimmed))
        }

        val rule = RuleAstToBuilderMapper.map(rule = ast)
        if (rule !is BuilderRule.Supported) {
            val reason = (rule as? BuilderRule.Unsupported)?.reason.orEmpty()
            return FormulaResult.Failed(
                message = "That parses, but the Builder cannot show it visually: $reason",
            )
        }

        // More than one node means the text held a join — `a > 1 and b < 2`. Applying it to one row
        // would silently drop half of it, so it is refused with the reason rather than truncated.
        val node = rule.conditionNodes.singleOrNull()
            ?: return FormulaResult.Failed(
                message = "That is more than one condition. Edit one row at a time, or add rows and " +
                    "set the joins between them.",
            )

        return FormulaResult.Parsed(node = node)
    }

    private fun wrap(condition: String): String {
        return """
            rule "$SYNTHETIC_ID" {
              when
                $condition
              then
                $SYNTHETIC_ID "x"
            }
        """.trimIndent()
    }

    /**
     * The parser's complaint, with the wrapper's line offset removed.
     *
     * The condition sits on line 3 of the synthetic rule, so a raw "error at line 3" would point at a
     * line the author cannot see. Only the column is reported, and only when it is inside the text they
     * typed.
     */
    private fun describe(exception: ParseException, condition: String): String {
        val column = exception.column.takeIf { value -> value in 1..condition.length }
        val where = column?.let { value -> " near column $value" }.orEmpty()
        return "Cannot read that$where: ${exception.messageText}"
    }

    /** The synthetic rule's id and action name — see the note on the object. */
    private const val SYNTHETIC_ID: String = "__fx"
}
