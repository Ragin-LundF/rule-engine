package ui.builder.outline

import androidx.compose.runtime.mutableStateListOf
import ruleengine.core.domain.dto.RuleBranch
import ruleengine.dsl.parser.Parser
import ui.builder.BuilderToRuleDsl
import ui.builder.RowIssues
import ui.builder.RuleAstToBuilderMapper
import ui.builder.model.BuilderLockKind
import ui.builder.model.BuilderRule
import ui.builder.model.mutable.BuilderEditorState
import ui.builder.model.mutable.MutableConditionNode
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * What the outline canvas is *for*, asserted through the model it drives rather than through pixels.
 *
 * The canvas itself is a rendering of [BuilderEditorState] and the text it generates; the behaviour
 * worth protecting is the second of those. So these tests exercise the gestures the canvas offers —
 * group, ungroup, add a branch, select a row — and assert the generated DSL, because that is what is
 * written to the file. A Compose test that asserted the same thing through node text would fail for
 * layout reasons and pass for correctness reasons, which is the wrong way round.
 */
class OutlineCanvasBehaviourTest {

    private fun stateOf(dsl: String): BuilderEditorState {
        val ast = Parser(input = dsl).parseRules().single()
        val rule = RuleAstToBuilderMapper.map(rule = ast)
        assertTrue(
            actual = rule is BuilderRule.Supported,
            message = "fixture is not editable: " + (rule as? BuilderRule.Unsupported)?.reason.orEmpty(),
        )
        return BuilderEditorState.fromBuilderRule(rule = rule)
    }

    private fun generate(state: BuilderEditorState): String {
        return assertNotNull(actual = BuilderToRuleDsl.generate(state = state))
    }

    // ── grouping (task 2.3) ───────────────────────────────────────────────────

    @Test
    fun `grouping two rows brackets them and leaves the third alone`() {
        val state = stateOf(
            dsl = """
                rule "r" {
                  when
                    a == 1
                    or b == 2
                    or c == 3
                  then
                    flag "hit"
                }
            """.trimIndent(),
        )
        val ids = state.conditionNodes.take(n = 2).map { node -> node.id }.toSet()

        state.groupConditions(ids = ids)
        val generated = generate(state = state)

        // The bracket has to reach the text, or precedence on screen and precedence in the file differ
        // — and the file is the one that decides.
        assertTrue(
            actual = generated.contains("(") && generated.contains(")"),
            message = "grouping produced no brackets:\n$generated",
        )
        assertTrue(actual = generated.contains("c == 3"), message = generated)
        // Still parses, which is the only real test of a generated bracket.
        Parser(input = generated).parseRules().single()
    }

    @Test
    fun `wrapping a single row in brackets and ungrouping it restores the original text`() {
        val dsl = """
            rule "r" {
              when
                a == 1
                and b == 2
              then
                flag "hit"
            }
        """.trimIndent()
        val state = stateOf(dsl = dsl)
        val before = generate(state = state)
        val id = state.conditionNodes.first().id

        assertTrue(actual = state.wrapInGroup(id = id), message = "wrap refused")
        val groupId = state.conditionNodes.first().id
        state.ungroup(id = groupId)

        assertEquals(
            expected = before,
            actual = generate(state = state),
            message = "wrap then ungroup must be a no-op on the text",
        )
    }

    @Test
    fun `a group keeps its join to what came before it`() {
        val state = stateOf(
            dsl = """
                rule "r" {
                  when
                    a == 1
                    or b == 2
                    or c == 3
                  then
                    flag "hit"
                }
            """.trimIndent(),
        )
        val ids = state.conditionNodes.drop(n = 1).map { node -> node.id }.toSet()

        state.groupConditions(ids = ids)
        val generated = generate(state = state)

        // Losing the `or` here would silently turn the rule into `a AND (b OR c)` — a different rule
        // that still parses, which is the worst kind of regression.
        assertTrue(actual = generated.contains("or ("), message = "join lost:\n$generated")
    }

    // ── every branch renders and survives (task 2.2) ──────────────────────────

    @Test
    fun `a rule with all three branches round-trips every one of them`() {
        val state = stateOf(
            dsl = """
                rule "r" {
                  when
                    amount > 100
                  then
                    set tier = "high"
                    flag "big"
                    stop
                  else
                    flag "small"
                  not_exists
                    flag "unknown"
                }
            """.trimIndent(),
        )

        val generated = generate(state = state)

        listOf("then", "else", "not_exists", "stop", "tier", "big", "small", "unknown").forEach { part ->
            assertTrue(actual = generated.contains(part), message = "'$part' missing from:\n$generated")
        }
        Parser(input = generated).parseRules().single()
    }

    @Test
    fun `stop stays at the end of its branch when an action is added after it`() {
        val state = stateOf(
            dsl = """
                rule "r" {
                  when
                    amount > 100
                  then
                    flag "big"
                    stop
                }
            """.trimIndent(),
        )

        state.addAction(defaultName = "flag", defaultArgCount = 1, branch = RuleBranch.THEN)
        val generated = generate(state = state)

        // `stop` is a flag on the branch rather than a row precisely so this cannot break: a `stop`
        // in the middle of a branch does not parse.
        val stopLine = generated.lines().indexOfFirst { line -> line.trim() == "stop" }
        val lastAction = generated.lines().indexOfLast { line -> line.trim().startsWith("flag") }
        assertTrue(
            actual = stopLine > lastAction,
            message = "stop is no longer last in its branch:\n$generated",
        )
    }

    // ── the locked rule (task 2.7) ────────────────────────────────────────────

    @Test
    fun `an unsupported rule opens locked with a reason and generates nothing`() {
        val ast = Parser(
            input = """
                rule "r" {
                  when
                    amount > 100
                  then
                    flag "hit"
                }
            """.trimIndent(),
        ).parseRules().single()

        // A rule the mapper refuses is represented as Unsupported, and the state built from it must
        // refuse to generate — otherwise the canvas would overwrite a rule it cannot fully read.
        val locked = BuilderEditorState.fromBuilderRule(
            rule = BuilderRule.Unsupported(id = ast.id, reason = "uses syntax the Builder cannot render"),
        )

        assertTrue(actual = locked.isLocked)
        assertEquals(expected = BuilderLockKind.UNSUPPORTED_SYNTAX, actual = locked.lockKind)
        assertTrue(actual = locked.lockReason.isNotBlank())
        assertNull(
            actual = BuilderToRuleDsl.generate(state = locked),
            message = "a locked rule must never generate text: it would replace what it cannot read",
        )
    }

    @Test
    fun `no rule selected is locked but is not an error`() {
        val empty = BuilderEditorState.fromBuilderRule(rule = BuilderRule.None)

        assertTrue(actual = empty.isLocked)
        assertEquals(expected = BuilderLockKind.NO_RULE_SELECTED, actual = empty.lockKind)
    }

    // ── the dock's row highlight (task 2.5) ───────────────────────────────────

    @Test
    fun `the text the dock highlights is a line of the text it shows`() {
        val state = stateOf(
            dsl = """
                rule "r" {
                  when
                    amount > 100
                    and purpose contains "rent" ignoreCase
                  then
                    flag "hit"
                }
            """.trimIndent(),
        )
        val generated = generate(state = state)

        state.conditionNodes.forEach { node ->
            val rowText = assertNotNull(
                actual = BuilderToRuleDsl.renderRow(node = node),
                message = "leaf row rendered as null",
            )
            // The highlight matches the generator's own output, so this is the invariant that keeps
            // the dock pointing at the right line as the generator changes.
            assertTrue(
                actual = generated.lines().any { line -> line.trim().endsWith(rowText.trim()) },
                message = "'$rowText' is not a line of:\n$generated",
            )
        }
    }

    @Test
    fun `a group has no line of its own to highlight`() {
        val state = stateOf(
            dsl = """
                rule "r" {
                  when
                    a == 1
                    and b == 2
                  then
                    flag "hit"
                }
            """.trimIndent(),
        )
        state.groupConditions(ids = state.conditionNodes.map { node -> node.id }.toSet())

        val group = state.conditionNodes.single()
        assertNull(actual = BuilderToRuleDsl.renderRow(node = group))
    }

    // ── inline row issues (task 2.6) ──────────────────────────────────────────

    @Test
    fun `a complete row reports no issue`() {
        val state = stateOf(
            dsl = """
                rule "r" {
                  when
                    amount between 10 20
                    and purpose in ["rent", "food"]
                    and count(invoices) > 2
                  then
                    flag "hit"
                }
            """.trimIndent(),
        )

        state.conditionNodes.forEach { node ->
            assertNull(
                actual = RowIssues.of(node = node),
                message = "complete row reported an issue: ${BuilderToRuleDsl.renderRow(node = node)}",
            )
        }
    }

    @Test
    fun `a freshly added row says what it still needs`() {
        val state = stateOf(
            dsl = """
                rule "r" {
                  when
                    amount > 100
                  then
                    flag "hit"
                }
            """.trimIndent(),
        )

        state.addCondition()
        val added = state.conditionNodes.last()

        // The point of the note: a row added and not filled in generates text that does not parse, and
        // the author finds out here rather than after the file has been rewritten.
        assertNotNull(
            actual = RowIssues.of(node = added),
            message = "an empty row must say what it needs",
        )
    }

    @Test
    fun `an empty group says so`() {
        // Built directly rather than by emptying a real group, because the guard that keeps a rule with
        // at least one condition refuses to remove the last row — so the state cannot reach this shape
        // by removal. It can reach it by grouping and then dragging the children out, which Phase 3
        // adds, and it is reachable today by ungrouping into a group. The note is what stops `()` being
        // generated silently either way.
        val empty = MutableConditionNode.Group(id = "grp-1", nodes = mutableStateListOf())

        assertEquals(expected = "empty group", actual = RowIssues.of(node = empty))
    }

    @Test
    fun `removing the last condition is refused rather than generating an empty when`() {
        val state = stateOf(
            dsl = """
                rule "r" {
                  when
                    amount > 100
                  then
                    flag "hit"
                }
            """.trimIndent(),
        )
        val before = generate(state = state)
        val onlyRow = state.conditionNodes.single().id

        val refusal = state.blockedRemoval(id = onlyRow)

        assertNotNull(actual = refusal, message = "emptying a `when` must be refused with a reason")
        assertEquals(
            expected = before,
            actual = generate(state = state),
            message = "the refusal must leave the text untouched",
        )
    }
}
