package ui.builder

import ruleengine.compiler.Validator
import ruleengine.core.domain.dto.OperatorId
import ruleengine.core.domain.dto.RuleBranch
import ruleengine.core.domain.dto.field.FieldDefinition
import ruleengine.core.domain.dto.field.FieldId
import ruleengine.core.domain.dto.field.FieldSchema
import ruleengine.core.domain.dto.field.FieldType
import ruleengine.core.errors.Severity
import ruleengine.dsl.ast.AssignmentKindAst
import ruleengine.dsl.parser.Parser
import ui.builder.model.mutable.BuilderEditorState
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Round-trip of the optional `not_exists` block through the visual Builder.
 *
 * The Builder replaces the whole rule text on every edit, so anything the mapper drops is deleted from
 * the file. A branch that survives one direction and not the other is silent data loss, which is what
 * these tests exist to catch.
 */
class BuilderNotExistsBranchRoundTripTest {

    private val schema = FieldSchema(
        name = "test",
        fields = mapOf(
            FieldId("amount") to FieldDefinition(
                id = FieldId("amount"),
                type = FieldType.DECIMAL,
                operators = setOf(OperatorId(">="), OperatorId("<=")),
            ),
        ),
    )

    @Test
    fun `a not_exists block survives the builder round-trip unchanged`() {
        val original = """
            rule "tier" {
              description "d"
              when
                amount >= 1000
              then
                label "high"
              else
                label "low"
              not_exists
                label "unknown"
            }
        """.trimIndent()

        val state = builderStateFromDsl(dsl = original)

        assertEquals(expected = listOf("label"), actual = state.notExistsActions.map { it.name })
        assertTrue(actual = state.hasNotExistsBranch)
        assertEquals(expected = original, actual = BuilderToRuleDsl.generate(state = state)?.trim())
        assertNoErrors(dsl = original)
    }

    @Test
    fun `a not_exists block without an else block round-trips too`() {
        val original = """
            rule "tier" {
              description "d"
              when
                amount >= 1000
              then
                label "high"
              not_exists
                label "unknown"
            }
        """.trimIndent()

        val state = builderStateFromDsl(dsl = original)

        assertFalse(actual = state.hasElseBranch)
        assertTrue(actual = state.hasNotExistsBranch)
        assertEquals(expected = original, actual = BuilderToRuleDsl.generate(state = state)?.trim())
    }

    @Test
    fun `the generated block order is then, else, not_exists`() {
        val state = builderStateFromDsl(
            dsl = """
                rule "tier" {
                  description "d"
                  when
                    amount >= 1000
                  then
                    label "high"
                  not_exists
                    label "unknown"
                }
            """.trimIndent()
        )
        state.addAction(defaultName = "label", defaultArgCount = 1, branch = RuleBranch.ELSE)

        val generated = BuilderToRuleDsl.generate(state = state).orEmpty()

        assertTrue(
            actual = generated.indexOf(string = "  else") < generated.indexOf(string = "  not_exists"),
            message = "the parser reads else before not_exists, so the Builder must write them that way: $generated",
        )
        // The proof that the order is right: what the Builder wrote parses back.
        Parser(input = generated).parseRules().single()
    }

    @Test
    fun `set and add rows of a not_exists block keep their clause kind`() {
        val original = """
            rule "tier" {
              description "d"
              when
                amount >= 1000
              then
                label "high"
              not_exists
                set reason = "no-amount"
                add "amount" to missingFields
            }
        """.trimIndent()

        val state = builderStateFromDsl(dsl = original)

        assertEquals(
            expected = listOf(AssignmentKindAst.SET, AssignmentKindAst.ADD),
            actual = state.notExistsVariables.map { it.kind },
        )
        assertEquals(expected = original, actual = BuilderToRuleDsl.generate(state = state)?.trim())
    }

    @Test
    fun `a stop in a not_exists block stays last and stays in that branch`() {
        val original = """
            rule "guard" {
              description "d"
              when
                amount >= 1000
              then
                label "high"
              not_exists
                label "unknown"
                stop
            }
        """.trimIndent()

        val state = builderStateFromDsl(dsl = original)

        assertTrue(actual = state.stopOf(branch = RuleBranch.NOT_EXISTS))
        assertFalse(actual = state.stopOf(branch = RuleBranch.THEN))
        assertFalse(actual = state.stopOf(branch = RuleBranch.ELSE))
        assertEquals(expected = original, actual = BuilderToRuleDsl.generate(state = state)?.trim())
    }

    @Test
    fun `removing the last row of a not_exists branch drops the block`() {
        val state = builderStateFromDsl(
            dsl = """
                rule "tier" {
                  description "d"
                  when
                    amount >= 1000
                  then
                    label "high"
                  not_exists
                    label "unknown"
                }
            """.trimIndent()
        )

        state.removeAction(id = state.notExistsActions.single().id)

        assertFalse(actual = state.hasNotExistsBranch)
        assertFalse(
            actual = BuilderToRuleDsl.generate(state = state).orEmpty().contains(other = "not_exists"),
            message = "an empty not_exists block does not parse, so it must not be written at all",
        )
    }

    @Test
    fun `row ids stay unique across all three branches`() {
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
                  not_exists
                    label "unknown"
                }
            """.trimIndent()
        )
        state.addAction(defaultName = "flag", defaultArgCount = 1, branch = RuleBranch.NOT_EXISTS)
        state.addVariable(defaultName = "reason", branch = RuleBranch.NOT_EXISTS)

        val actionIds = (state.actions + state.elseActions + state.notExistsActions).map { it.id }
        val variableIds = (state.variables + state.elseVariables + state.notExistsVariables).map { it.id }

        assertEquals(expected = actionIds.distinct().size, actual = actionIds.size, message = "$actionIds")
        assertEquals(expected = variableIds.distinct().size, actual = variableIds.size, message = "$variableIds")
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
