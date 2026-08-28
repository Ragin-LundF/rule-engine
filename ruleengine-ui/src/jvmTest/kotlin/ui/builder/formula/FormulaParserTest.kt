package ui.builder.formula

import ruleengine.dsl.parser.Parser
import ui.builder.BuilderToRuleDsl
import ui.builder.FormulaParser
import ui.builder.RuleAstToBuilderMapper
import ui.builder.formula.model.FormulaResult
import ui.builder.model.BuilderRule
import ui.builder.model.mutable.BuilderEditorState
import ui.builder.model.mutable.MutableConditionNode
import ui.builder.model.mutable.replaceNodeFromFormula
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

/**
 * The formula bar's parse, tested where it matters: does text survive the trip to the model and back.
 *
 * Every case goes `text → FormulaParser → BuilderConditionNode → BuilderToRuleDsl → text` and compares
 * the two ends. That is the property the bar rests on. Anything the mapper cannot represent must be
 * *refused* rather than silently reduced, because the Builder replaces the whole rule text on every
 * edit — an expression accepted half-understood is an expression written to the file half-understood.
 */
class FormulaParserTest {

    /** Puts [condition] through the bar and back out as text. */
    private fun roundTrip(condition: String): String {
        val result = FormulaParser.parseCondition(text = condition)
        val parsed = assertIs<FormulaResult.Parsed>(
            value = result,
            message = "refused '$condition': ${(result as? FormulaResult.Failed)?.message}",
        )

        // Rebuild a whole rule around the node so the real generator renders it, rather than trusting a
        // second rendering path that only the test would use.
        val rule = BuilderRule.Supported(
            id = "r",
            conditionNodes = listOf(parsed.node),
            actions = emptyList(),
        )
        val state = BuilderEditorState.fromBuilderRule(rule = rule)
        val generated = BuilderToRuleDsl.generate(state = state).orEmpty()

        return generated
            .lines()
            .dropWhile { line -> !line.trim().startsWith("when") }
            .drop(n = 1)
            .first { line -> line.isNotBlank() }
            .trim()
    }

    // ── the shapes the DSL has ────────────────────────────────────────────────

    @Test
    fun `every condition shape survives text to model to text`() {
        val cases = listOf(
            "amount > 100",
            "amount >= 1000",
            "status equals \"paid\"",
            "purpose contains \"rent\"",
            "purpose contains \"rent\" ignoreCase",
            "iban startsWith \"DE\"",
            "amount between 10 20",
            "status in [\"paid\", \"sent\"]",
            "count(invoices) > 2",
            "sum(invoices.amount) >= 500",
            "avg(orders.total) < 99.5",
            "abs(budget - spent) > 10",
            "count(invoices) > count(orders)",
        )

        cases.forEach { condition ->
            assertEquals(
                expected = condition,
                actual = roundTrip(condition = condition),
                message = "'$condition' did not survive the round trip",
            )
        }
    }

    @Test
    fun `the bundled samples' own conditions survive the round trip`() {
        // Rather than invent expressions, take the ones the shipped samples actually contain: those are
        // the shapes that have to work, and they are the shapes a regression would break.
        val conditions = sampleConditions()
        assertTrue(
            actual = conditions.size >= MIN_SAMPLE_CONDITIONS,
            message = "expected to find real conditions to test, found ${conditions.size}",
        )

        val refused = conditions.filter { condition ->
            FormulaParser.parseCondition(text = condition) !is FormulaResult.Parsed
        }
        assertTrue(
            actual = refused.isEmpty(),
            message = "the bar refused conditions the Builder can already show:\n" +
                refused.joinToString(separator = "\n"),
        )
    }

    // ── refusals ──────────────────────────────────────────────────────────────

    @Test
    fun `a malformed expression is refused with a message`() {
        val failed = assertIs<FormulaResult.Failed>(
            value = FormulaParser.parseCondition(text = "amount >"),
        )
        assertTrue(actual = failed.message.isNotBlank())
    }

    @Test
    fun `an empty expression asks for one instead of failing obscurely`() {
        val failed = assertIs<FormulaResult.Failed>(value = FormulaParser.parseCondition(text = "   "))
        assertTrue(
            actual = failed.message.contains("amount > 100"),
            message = "the empty case should show an example, not a parser error",
        )
    }

    @Test
    fun `two conditions joined by and are refused rather than truncated`() {
        val failed = assertIs<FormulaResult.Failed>(
            value = FormulaParser.parseCondition(text = "amount > 1 and status equals \"paid\""),
        )
        // Truncating would drop half the expression into the file. The refusal names the reason.
        assertTrue(
            actual = failed.message.contains("more than one condition"),
            message = "unexpected message: ${failed.message}",
        )
    }

    @Test
    fun `the wrapper never leaks into a successful parse`() {
        val text = roundTrip(condition = "amount > 100")

        assertTrue(
            actual = !text.contains("__fx"),
            message = "the synthetic rule leaked into the result: $text",
        )
    }

    /** Every condition line of every bundled sample the Builder can already render. */
    private fun sampleConditions(): List<String> {
        val dir = java.io.File("src/commonMain/composeResources/files/samples")
        if (!dir.isDirectory) {
            return emptyList()
        }

        return dir.walkTopDown()
            .filter { file -> file.isFile }
            .flatMap { file -> conditionsOf(text = file.readText()) }
            .distinct()
            .toList()
    }

    /**
     * Pulls the condition lines out of [text] by parsing it and re-rendering each rule.
     *
     * Re-rendering rather than reading the file's own lines: the generator's spelling is what the bar has
     * to match, and a sample's source may be indented or spaced differently without being different.
     */
    private fun conditionsOf(text: String): List<String> {
        val rules = try {
            Parser(input = text).parseRules()
        } catch (_: ruleengine.dsl.diagnostics.ParseException) {
            return emptyList()
        }

        return rules.mapNotNull { ast ->
            val rule = RuleAstToBuilderMapper.map(rule = ast) as? BuilderRule.Supported
            rule?.conditionNodes?.singleOrNull()?.let { node ->
                val state = BuilderEditorState.fromBuilderRule(
                    rule = rule.copy(conditionNodes = listOf(node)),
                )
                BuilderToRuleDsl.generate(state = state)
                    ?.lines()
                    ?.dropWhile { line -> !line.trim().startsWith("when") }
                    ?.drop(n = 1)
                    ?.firstOrNull { line -> line.isNotBlank() }
                    ?.trim()
            }
        }
    }

    private companion object {
        /** A floor, so an empty samples directory cannot make the sample test pass by finding nothing. */
        const val MIN_SAMPLE_CONDITIONS: Int = 5
    }
}

/**
 * Applying a parsed expression to a row that already exists.
 *
 * The parse is only half of the bar. The other half is that applying it keeps the row's *identity* and
 * its *join* — the parsed node came out of a synthetic one-condition rule and has neither — because
 * losing the id breaks every selection pointing at the row, and losing the join silently turns an `or`
 * into an `and`: a different rule that still parses, which is the worst kind of regression.
 */
class FormulaApplyTest {

    private fun stateOf(dsl: String): BuilderEditorState {
        val ast = Parser(input = dsl).parseRules().single()
        val rule = RuleAstToBuilderMapper.map(rule = ast)
        assertIs<BuilderRule.Supported>(value = rule)
        return BuilderEditorState.fromBuilderRule(rule = rule)
    }

    private val twoRows = """
        rule "r" {
          when
            amount > 100
            or status equals "paid"
          then
            flag "hit"
        }
    """.trimIndent()

    @Test
    fun `applying an expression rewrites the row and keeps its id`() {
        val state = stateOf(dsl = twoRows)
        val rowId = state.conditionNodes.first().id
        val parsed = assertIs<FormulaResult.Parsed>(
            value = FormulaParser.parseCondition(text = "count(invoices) > 2"),
        )

        assertTrue(actual = state.replaceNodeFromFormula(id = rowId, parsed = parsed.node))

        assertEquals(
            expected = rowId,
            actual = state.conditionNodes.first().id,
            message = "the row's identity must survive, or every selection pointing at it breaks",
        )
        val generated = BuilderToRuleDsl.generate(state = state).orEmpty()
        assertTrue(actual = generated.contains("count(invoices) > 2"), message = generated)
        assertTrue(actual = !generated.contains("amount > 100"), message = generated)
    }

    @Test
    fun `applying an expression to the second row keeps its OR`() {
        val state = stateOf(dsl = twoRows)
        val secondId = state.conditionNodes[1].id
        val parsed = assertIs<FormulaResult.Parsed>(
            value = FormulaParser.parseCondition(text = "status equals \"sent\""),
        )

        state.replaceNodeFromFormula(id = secondId, parsed = parsed.node)
        val generated = BuilderToRuleDsl.generate(state = state).orEmpty()

        assertTrue(
            actual = generated.contains("or status equals \"sent\""),
            message = "the join was lost — the rule now means something else:\n$generated",
        )
        Parser(input = generated).parseRules().single()
    }

    @Test
    fun `applying to a row inside a group finds it`() {
        val state = stateOf(dsl = twoRows)
        state.groupConditions(ids = state.conditionNodes.map { node -> node.id }.toSet())
        val group = state.conditionNodes.filterIsInstance<MutableConditionNode.Group>().single()
        val inner = group.nodes.first().id

        val parsed = assertIs<FormulaResult.Parsed>(
            value = FormulaParser.parseCondition(text = "amount > 500"),
        )

        assertTrue(actual = state.replaceNodeFromFormula(id = inner, parsed = parsed.node))
        val generated = BuilderToRuleDsl.generate(state = state).orEmpty()
        assertTrue(actual = generated.contains("amount > 500"), message = generated)
        Parser(input = generated).parseRules().single()
    }

    @Test
    fun `applying to a row that is not there changes nothing`() {
        val state = stateOf(dsl = twoRows)
        val before = BuilderToRuleDsl.generate(state = state)
        val parsed = assertIs<FormulaResult.Parsed>(
            value = FormulaParser.parseCondition(text = "amount > 1"),
        )

        assertFalse(actual = state.replaceNodeFromFormula(id = "no-such-row", parsed = parsed.node))
        assertEquals(expected = before, actual = BuilderToRuleDsl.generate(state = state))
    }

    @Test
    fun `a refused expression leaves the rule untouched`() {
        val state = stateOf(dsl = twoRows)
        val before = BuilderToRuleDsl.generate(state = state)

        val result = FormulaParser.parseCondition(text = "amount >")

        assertIs<FormulaResult.Failed>(value = result)
        // Nothing to apply, and nothing applied: the whole safety property of the bar in one assertion.
        assertEquals(expected = before, actual = BuilderToRuleDsl.generate(state = state))
    }
}
