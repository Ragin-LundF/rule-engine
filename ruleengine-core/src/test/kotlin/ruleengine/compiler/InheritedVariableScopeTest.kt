package ruleengine.compiler

import ruleengine.core.domain.dto.field.FieldDefinition
import ruleengine.core.domain.dto.field.FieldId
import ruleengine.core.domain.dto.field.FieldSchema
import ruleengine.core.domain.dto.field.FieldType
import ruleengine.core.errors.Severity
import ruleengine.core.errors.ValidationDiagnostic
import ruleengine.dsl.ast.AssignmentKindAst
import ruleengine.dsl.parser.Parser
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Validating part of a manifest entry: what `inheritedVariables` does and does not let through.
 *
 * The engine flattens an entry's rule files before validating, so a `$name` read in the last file
 * resolves against the `set` and `add` clauses of the files before it. A caller holding one file — the
 * editor, and the per-file pass of an entry-wide validation — names those writers instead.
 */
class InheritedVariableScopeTest {

    private val schema = FieldSchema(
        name = "orders",
        fields = mapOf(
            FieldId(value = "amount") to FieldDefinition(
                id = FieldId(value = "amount"),
                type = FieldType.DECIMAL,
            ),
        ),
    )

    @Test
    fun `a read of an inherited variable is accepted`() {
        val diagnostics = validate(
            rules = """
                rule "reads-inherited" {
                  description "Reads what an earlier file published."
                  when
                    ${'$'}orderTotal >= 300
                  then
                    label "vip"
                }
            """.trimIndent(),
            inherited = mapOf("orderTotal" to AssignmentKindAst.SET),
        )

        assertEquals(expected = emptyList(), actual = diagnostics)
    }

    @Test
    fun `the same read without the inherited scope is an error`() {
        val diagnostics = validate(
            rules = """
                rule "reads-inherited" {
                  description "Reads what an earlier file published."
                  when
                    ${'$'}orderTotal >= 300
                  then
                    label "vip"
                }
            """.trimIndent(),
            inherited = emptyMap(),
        )

        assertEquals(expected = 1, actual = diagnostics.size)
        assertEquals(expected = Severity.ERROR, actual = diagnostics.single().severity)
        assertTrue(
            actual = diagnostics.single().message.contains(other = "reads unknown variable '\$orderTotal'"),
            message = "Expected the unknown-variable diagnostic but got: ${diagnostics.single().message}",
        )
    }

    @Test
    fun `an inherited accumulator is readable with contains`() {
        val diagnostics = validate(
            rules = """
                rule "routes" {
                  description "Routes on a list an earlier file filled."
                  when
                    ${'$'}topics contains "billing"
                  then
                    label "finance"
                }
            """.trimIndent(),
            inherited = mapOf("topics" to AssignmentKindAst.ADD),
        )

        assertEquals(expected = emptyList(), actual = diagnostics)
    }

    @Test
    fun `writing an inherited add name with set is still a kind clash`() {
        val diagnostics = validate(
            rules = """
                rule "overwrites" {
                  description "Replaces a list an earlier file accumulates into."
                  when
                    amount > 0
                  then
                    set topics = "billing"
                }
            """.trimIndent(),
            inherited = mapOf("topics" to AssignmentKindAst.ADD),
        )

        assertEquals(expected = 1, actual = diagnostics.size)
        assertEquals(expected = Severity.ERROR, actual = diagnostics.single().severity)
        assertTrue(
            actual = diagnostics.single().message.contains(other = "written by both a 'set' and an 'add' clause"),
            message = "Expected the set/add clash diagnostic but got: ${diagnostics.single().message}",
        )
    }

    @Test
    fun `re-assigning an inherited set name does not warn about a rule the caller cannot see`() {
        val diagnostics = validate(
            rules = """
                rule "re-assigns" {
                  description "Publishes a name an earlier file also publishes."
                  when
                    amount > 0
                  then
                    set orderTotal = amount
                }
            """.trimIndent(),
            inherited = mapOf("orderTotal" to AssignmentKindAst.SET),
        )

        assertEquals(expected = emptyList(), actual = diagnostics)
    }

    private fun validate(
        rules: String,
        inherited: Map<String, AssignmentKindAst>,
    ): List<ValidationDiagnostic> {
        val result = Validator.validate(
            asts = Parser(input = rules).parseRules(),
            schema = schema,
            inheritedVariables = inherited,
        )
        return result.diagnostics
    }
}
