package ruleengine.compiler

import ruleengine.core.domain.dto.action.ActionArgType
import ruleengine.core.domain.dto.action.ActionDefinition
import ruleengine.core.domain.dto.action.ActionSchema
import ruleengine.core.domain.dto.field.FieldDefinition
import ruleengine.core.domain.dto.field.FieldId
import ruleengine.core.domain.dto.field.FieldSchema
import ruleengine.core.domain.dto.field.FieldType
import ruleengine.core.errors.Severity
import ruleengine.core.errors.ValidationDiagnostic
import ruleengine.dsl.parser.Parser
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Validating a manifest entry file by file: what each diagnostic is attributed to, and the checks that
 * only exist once there is more than one file.
 */
class EntryValidatorTest {

    private val schema = FieldSchema(
        name = "orders",
        fields = mapOf(
            FieldId(value = "amount") to FieldDefinition(
                id = FieldId(value = "amount"),
                type = FieldType.DECIMAL,
            ),
        ),
    )

    private val actionSchema = ActionSchema(
        actions = mapOf("label" to ActionDefinition(name = "label", argTypes = listOf(ActionArgType.STRING)))
    )

    @Test
    fun `a variable published by an earlier file resolves in a later one`() {
        val result = EntryValidator.validate(
            files = listOf(
                file(
                    path = "rules/01-totals.rule",
                    text = """
                        rule "totals" {
                          description "d"
                          when
                            amount > 0
                          then
                            set turnover = amount
                        }
                    """.trimIndent(),
                ),
                file(
                    path = "rules/zz-tiers.rule",
                    text = """
                        rule "tiers" {
                          description "d"
                          when
                            ${'$'}turnover >= 100
                          then
                            label "vip"
                        }
                    """.trimIndent(),
                ),
            ),
            schema = schema,
            actions = actionSchema,
        )

        assertEquals(expected = emptyList(), actual = result.diagnostics)
        assertTrue(actual = result.isValid)
    }

    /** The forward reference the engine rejects has to stay rejected — order is the whole contract. */
    @Test
    fun `a variable published by a later file is still a forward reference`() {
        val errors = errorsOf(
            files = listOf(
                file(
                    path = "rules/01-tiers.rule",
                    text = """
                        rule "tiers" {
                          description "d"
                          when
                            ${'$'}turnover >= 100
                          then
                            label "vip"
                        }
                    """.trimIndent(),
                ),
                file(
                    path = "rules/02-totals.rule",
                    text = """
                        rule "totals" {
                          description "d"
                          when
                            amount > 0
                          then
                            set turnover = amount
                        }
                    """.trimIndent(),
                ),
            )
        )

        assertEquals(expected = 1, actual = errors.size)
        assertTrue(actual = errors.single().message.contains(other = "unknown variable"))
        assertEquals(expected = Path.of("rules/01-tiers.rule"), actual = errors.single().file)
    }

    @Test
    fun `a diagnostic names the file it came from, with that file's own line number`() {
        val errors = errorsOf(
            files = listOf(
                file(path = "rules/a.rule", text = oneRule(id = "a", condition = "amount > 0")),
                file(
                    path = "rules/b.rule",
                    text = """
                        rule "b" {
                          description "d"
                          when
                            nosuchfield > 0
                          then
                            label "x"
                        }
                    """.trimIndent(),
                ),
            )
        )

        assertEquals(expected = 1, actual = errors.size)
        assertEquals(expected = Path.of("rules/b.rule"), actual = errors.single().file)
        // Line 4 of b.rule is `nosuchfield > 0`. Relative to b.rule, not to the two files concatenated,
        // which is the whole point of the per-file pass — a.rule is 7 lines long, so a flattened pass
        // would have reported a line past the end of the file the reader is pointed at.
        assertEquals(expected = 4, actual = errors.single().line)
    }

    @Test
    fun `a rule id repeated in two files is reported against the later one, naming the earlier`() {
        val errors = errorsOf(
            files = listOf(
                file(path = "rules/a.rule", text = oneRule(id = "tier", condition = "amount > 0")),
                file(path = "rules/b.rule", text = oneRule(id = "tier", condition = "amount > 1")),
            )
        )

        assertEquals(expected = 1, actual = errors.size)
        assertEquals(expected = Path.of("rules/b.rule"), actual = errors.single().file)
        assertTrue(
            actual = errors.single().message.contains(other = "also declared in 'rules/a.rule'"),
            message = "got: ${errors.single().message}",
        )
    }

    @Test
    fun `a rule id repeated inside one file is still reported once, by the per-file pass`() {
        val errors = errorsOf(
            files = listOf(
                file(
                    path = "rules/a.rule",
                    text = oneRule(id = "tier", condition = "amount > 0") + "\n" +
                        oneRule(id = "tier", condition = "amount > 1"),
                ),
            )
        )

        assertEquals(expected = 1, actual = errors.size)
        assertTrue(
            actual = errors.single().message.contains(other = "Duplicate rule id: tier"),
            message = "got: ${errors.single().message}",
        )
    }

    /** Once for the entry, not once per file — it is one problem with the schema. */
    @Test
    fun `a schema-level problem is reported once however many files there are`() {
        val duplicateAlias = FieldSchema(
            name = "orders",
            fields = mapOf(
                FieldId(value = "amount") to FieldDefinition(
                    id = FieldId(value = "amount"),
                    type = FieldType.DECIMAL,
                    alias = "value",
                ),
                FieldId(value = "total") to FieldDefinition(
                    id = FieldId(value = "total"),
                    type = FieldType.DECIMAL,
                    alias = "value",
                ),
            ),
        )

        val errors = EntryValidator.validate(
            files = listOf(
                file(path = "rules/a.rule", text = oneRule(id = "a", condition = "amount > 0")),
                file(path = "rules/b.rule", text = oneRule(id = "b", condition = "amount > 1")),
                file(path = "rules/c.rule", text = oneRule(id = "c", condition = "amount > 2")),
            ),
            schema = duplicateAlias,
            actions = actionSchema,
        ).diagnostics.filter { it.severity == Severity.ERROR }

        assertEquals(expected = 1, actual = errors.size, message = "got: $errors")
        assertTrue(actual = errors.single().message.contains(other = "Duplicate alias"))
    }

    @Test
    fun `an unknown action is reported with its file`() {
        val errors = errorsOf(
            files = listOf(
                file(
                    path = "rules/a.rule",
                    text = """
                        rule "a" {
                          description "d"
                          when
                            amount > 0
                          then
                            nosuchaction "x"
                        }
                    """.trimIndent(),
                ),
            )
        )

        assertEquals(expected = 1, actual = errors.size)
        assertTrue(actual = errors.single().message.contains(other = "nosuchaction"))
        assertEquals(expected = Path.of("rules/a.rule"), actual = errors.single().file)
    }

    @Test
    fun `an entry with no files is valid and reports nothing`() {
        val result = EntryValidator.validate(files = emptyList(), schema = schema, actions = actionSchema)

        assertEquals(expected = emptyList(), actual = result.diagnostics)
        assertTrue(actual = result.isValid)
    }

    private fun file(path: String, text: String): RuleFileAsts {
        return RuleFileAsts(path = path, asts = Parser(input = text).parseRules())
    }

    private fun oneRule(id: String, condition: String): String {
        return """
            rule "$id" {
              description "d"
              when
                $condition
              then
                label "x"
            }
        """.trimIndent()
    }

    private fun errorsOf(files: List<RuleFileAsts>): List<ValidationDiagnostic> {
        return EntryValidator.validate(files = files, schema = schema, actions = actionSchema)
            .diagnostics
            .filter { it.severity == Severity.ERROR }
    }
}
