package ui.builder

import ruleengine.compiler.Validator
import ruleengine.core.domain.dto.OperatorId
import ruleengine.core.domain.dto.RuleBranch
import ruleengine.core.domain.dto.field.FieldDefinition
import ruleengine.core.domain.dto.field.FieldId
import ruleengine.core.domain.dto.field.FieldSchema
import ruleengine.core.domain.dto.field.FieldType
import ruleengine.core.errors.Severity
import ruleengine.dsl.ast.StringLiteral
import ruleengine.dsl.parser.Parser
import ui.builder.model.BuilderRule
import ui.builder.model.mutable.BuilderEditorState
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/** Round-trip of the optional `else` block through the visual Builder. */
class BuilderElseBranchRoundTripTest {

    private val schema = FieldSchema(
        name = "test",
        fields = mapOf(
            FieldId("amount") to FieldDefinition(
                id = FieldId("amount"),
                type = FieldType.DECIMAL,
                operators = setOf(OperatorId(">="), OperatorId("<=")),
            ),
            FieldId("reference") to FieldDefinition(
                id = FieldId("reference"),
                type = FieldType.TEXT,
            ),
        ),
    )

    @Test
    fun `an else block survives the builder round-trip unchanged`() {
        val original = """
            rule "tier" {
              description "d"
              when
                amount >= 1000
              then
                label "high"
              else
                label "low"
            }
        """.trimIndent()

        val state = builderStateFromDsl(dsl = original)

        assertEquals(expected = listOf("label"), actual = state.actions.map { it.name })
        assertEquals(expected = listOf("label"), actual = state.elseActions.map { it.name })
        assertTrue(actual = state.hasElseBranch)

        assertEquals(expected = original, actual = BuilderToRuleDsl.generate(state = state)?.trim())
    }

    @Test
    fun `an else block with set rows survives the round-trip`() {
        val original = """
            rule "tier" {
              description "d"
              when
                amount >= 1000
              then
                set tierLevel = 2
                label "high"
              else
                set tierLevel = 1
                label "low"
            }
        """.trimIndent()

        val state = builderStateFromDsl(dsl = original)

        assertEquals(expected = listOf("tierLevel"), actual = state.variables.map { it.name })
        assertEquals(expected = listOf("tierLevel"), actual = state.elseVariables.map { it.name })

        val regenerated = BuilderToRuleDsl.generate(state = state).orEmpty()
        assertEquals(expected = original, actual = regenerated.trim())
        assertNoErrors(dsl = regenerated)
    }

    /**
     * An empty `else` does not parse, so "the author removed the last else row" has to be spelled as
     * no `else` keyword at all rather than an empty block.
     */
    @Test
    fun `no else keyword is emitted once the last else row is removed`() {
        val state = builderStateFromDsl(
            dsl = """
                rule "tier" {
                  description "d"
                  when
                    amount >= 1000
                  then
                    label "high"
                  else
                    label "low"
                }
            """.trimIndent()
        )

        state.removeAction(id = state.elseActions.single().id)

        assertFalse(actual = state.hasElseBranch)
        val regenerated = BuilderToRuleDsl.generate(state = state).orEmpty()
        assertFalse(
            actual = regenerated.contains(other = "else"),
            message = "unexpected else block: $regenerated"
        )
        assertNoErrors(dsl = regenerated)
    }

    @Test
    fun `a rule without an else block round-trips without gaining one`() {
        val original = """
            rule "tier" {
              description "d"
              when
                amount >= 1000
              then
                label "high"
            }
        """.trimIndent()

        val state = builderStateFromDsl(dsl = original)

        assertFalse(actual = state.hasElseBranch)
        assertEquals(expected = original, actual = BuilderToRuleDsl.generate(state = state)?.trim())
    }

    @Test
    fun `adding an else action creates the branch and emits the block`() {
        val state = builderStateFromDsl(
            dsl = """
                rule "tier" {
                  description "d"
                  when
                    amount >= 1000
                  then
                    label "high"
                }
            """.trimIndent()
        )

        state.addAction(defaultName = "label", defaultArgCount = 1, branch = RuleBranch.ELSE)
        state.elseActions.single().arguments[0] = "low"

        assertTrue(actual = state.hasElseBranch)
        assertEquals(
            expected = """
                rule "tier" {
                  description "d"
                  when
                    amount >= 1000
                  then
                    label "high"
                  else
                    label "low"
                }
            """.trimIndent(),
            actual = BuilderToRuleDsl.generate(state = state)?.trim(),
        )
    }

    /** Row ids have to be unique across the rule, or removing in one branch would hit the other. */
    @Test
    fun `removing a then action leaves the else actions alone`() {
        val state = builderStateFromDsl(
            dsl = """
                rule "tier" {
                  description "d"
                  when
                    amount >= 1000
                  then
                    label "high"
                  else
                    label "low"
                }
            """.trimIndent()
        )

        state.removeAction(id = state.actions.single().id)

        assertTrue(actual = state.actions.isEmpty())
        assertEquals(expected = 1, actual = state.elseActions.size)
    }

    /** An extraction is carried per action, so it round-trips in either branch. */
    @Test
    fun `an extraction in the else block survives the builder round-trip`() {
        val original = """
            rule "tier" {
              description "d"
              when
                amount >= 1000
              then
                label "high"
              else
                extract reference regex("ref-([0-9]+)", 1) label ${'$'}1
            }
        """.trimIndent()
        val rule = Parser(input = original).parseRules().single()

        val mapped = assertIs<BuilderRule.Supported>(value = RuleAstToBuilderMapper.map(rule))
        val generated = assertNotNull(
            actual = BuilderToRuleDsl.generate(state = BuilderEditorState.fromBuilderRule(rule = mapped))
        )
        val reparsed = Parser(input = generated).parseRules().single()

        assertEquals(
            expected = rule.elseActions,
            actual = reparsed.elseActions,
            message = "the else branch's extraction must survive.\nGenerated:\n$generated"
        )
        assertTrue(
            actual = reparsed.actions.single().extraction == null,
            message = "the then branch must not pick up the else branch's extraction"
        )
    }

    // ── stop ──────────────────────────────────────────────────────────────────

    @Test
    fun `a stop survives the builder round-trip on each branch`() {
        val original = """
            rule "tier" {
              description "d"
              when
                amount >= 1000
              then
                label "high"
                stop
              else
                label "low"
                stop
            }
        """.trimIndent()

        val state = builderStateFromDsl(dsl = original)

        assertTrue(actual = state.stopOnThen)
        assertTrue(actual = state.stopOnElse)
        assertEquals(expected = original, actual = BuilderToRuleDsl.generate(state = state)?.trim())
    }

    @Test
    fun `a rule without a stop does not gain one`() {
        val state = builderStateFromDsl(
            dsl = """
                rule "tier" {
                  description "d"
                  when
                    amount >= 1000
                  then
                    label "high"
                }
            """.trimIndent()
        )

        assertFalse(actual = state.stopOnThen)
        assertFalse(actual = state.stopOnElse)
        assertFalse(
            actual = BuilderToRuleDsl.generate(state = state).orEmpty().contains(other = "stop"),
        )
    }

    /**
     * The behaviour the badge exists for: `stop` is held as a flag, not a row, so output added after it
     * cannot end up below it — the generated DSL still writes `stop` last, which is what the parser
     * requires.
     */
    @Test
    fun `an action added after the stop is still emitted above it`() {
        val state = builderStateFromDsl(
            dsl = """
                rule "tier" {
                  description "d"
                  when
                    amount >= 1000
                  then
                    label "high"
                    stop
                }
            """.trimIndent()
        )

        state.addAction(defaultName = "label", defaultArgCount = 1, branch = RuleBranch.THEN)
        state.actions.last().arguments[0] = "extra"
        state.addVariable(defaultName = "tierLevel", branch = RuleBranch.THEN)

        val regenerated = BuilderToRuleDsl.generate(state = state).orEmpty()
        assertEquals(
            expected = """
                rule "tier" {
                  description "d"
                  when
                    amount >= 1000
                  then
                    set tierLevel = ""
                    label "high"
                    label "extra"
                    stop
                }
            """.trimIndent(),
            actual = regenerated.trim(),
        )
    }

    @Test
    fun `removing the stop drops the keyword and keeps the actions`() {
        val state = builderStateFromDsl(
            dsl = """
                rule "tier" {
                  description "d"
                  when
                    amount >= 1000
                  then
                    label "high"
                    stop
                }
            """.trimIndent()
        )

        state.setStop(branch = RuleBranch.THEN, stop = false)

        val regenerated = BuilderToRuleDsl.generate(state = state).orEmpty()
        assertFalse(actual = regenerated.contains(other = "stop"), message = regenerated)
        assertTrue(actual = regenerated.contains(other = """label "high""""), message = regenerated)
        assertNoErrors(dsl = regenerated)
    }

    /** An else branch that only stops is a valid rule, so the branch must survive losing its actions. */
    @Test
    fun `an else branch holding only a stop still emits the block`() {
        val state = builderStateFromDsl(
            dsl = """
                rule "tier" {
                  description "d"
                  when
                    amount >= 1000
                  then
                    label "high"
                  else
                    label "low"
                }
            """.trimIndent()
        )

        state.setStop(branch = RuleBranch.ELSE, stop = true)
        state.removeAction(id = state.elseActions.single().id)

        assertTrue(actual = state.hasElseBranch)
        val regenerated = BuilderToRuleDsl.generate(state = state).orEmpty()
        assertEquals(
            expected = """
                rule "tier" {
                  description "d"
                  when
                    amount >= 1000
                  then
                    label "high"
                  else
                    stop
                }
            """.trimIndent(),
            actual = regenerated.trim(),
        )
        assertNoErrors(dsl = regenerated)
    }

    // ── quoting ───────────────────────────────────────────────────────────────

    /**
     * The regression this guards: an action argument containing a `"` was emitted verbatim, which closed
     * the string literal mid-word and left the rest of the rule unparseable. Since the Builder replaces
     * the whole rule text on every edit, that corrupted the file rather than only rendering wrongly.
     *
     * A customer-facing `message` quoting an expected format is the realistic case — see the
     * `kyc-onboarding` sample.
     */
    @Test
    fun `an action argument containing a quote survives the round-trip`() {
        val original = """
            rule "format-hint" {
              description "d"
              when
                amount >= 1
              then
                label "use the format \"HRB 123456\" exactly"
            }
        """.trimIndent()

        val state = builderStateFromDsl(dsl = original)
        val regenerated = BuilderToRuleDsl.generate(state = state).orEmpty()

        // Parses at all — before the fix this threw rather than returning a rule.
        val reparsed = Parser(input = regenerated).parseRules().single()
        assertEquals(
            expected = """use the format "HRB 123456" exactly""",
            actual = assertIs<StringLiteral>(value = reparsed.actions.single().arguments.single()).value,
        )
        assertNoErrors(dsl = regenerated)
    }

    @Test
    fun `an action argument containing a backslash survives the round-trip`() {
        val original = """
            rule "path-hint" {
              description "d"
              when
                amount >= 1
              then
                label "a\\b"
            }
        """.trimIndent()

        val state = builderStateFromDsl(dsl = original)
        val reparsed = Parser(input = BuilderToRuleDsl.generate(state = state).orEmpty()).parseRules().single()

        assertEquals(
            expected = """a\b""",
            actual = assertIs<StringLiteral>(value = reparsed.actions.single().arguments.single()).value,
        )
    }

    private fun builderStateFromDsl(dsl: String): BuilderEditorState {
        val rule = Parser(input = dsl).parseRules().single()
        return BuilderEditorState.fromBuilderRule(RuleAstToBuilderMapper.map(rule))
    }

    private fun assertNoErrors(dsl: String) {
        val result = Validator.validate(asts = Parser(input = dsl).parseRules(), schema = schema, actions = null)
        assertTrue(
            actual = result.diagnostics.none { it.severity == Severity.ERROR },
            message = "unexpected errors: ${result.diagnostics}"
        )
    }
}
