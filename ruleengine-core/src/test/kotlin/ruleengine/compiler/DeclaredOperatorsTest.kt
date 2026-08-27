package ruleengine.compiler

import ruleengine.core.domain.dto.OperatorId
import ruleengine.core.domain.dto.field.FieldDefinition
import ruleengine.core.domain.dto.field.FieldId
import ruleengine.core.domain.dto.field.FieldSchema
import ruleengine.core.domain.dto.field.FieldType
import ruleengine.core.errors.Severity
import ruleengine.dsl.parser.Parser
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * A field's declared `operators:` list narrows the type's defaults; it never widens them.
 *
 * Before that it *replaced* them, so a schema could name an operator the compiler has no branch for.
 * The validator passed it and the failure arrived as a `CompilationException` at load time — a stack
 * trace instead of a diagnostic pointing at the declaration. Two cases reached evaluation instead:
 * a `boolean` compiled every operator to equality, and a `string_set` compiled `containsAll "x"` as
 * `containsAny`.
 */
class DeclaredOperatorsTest {

    private fun schemaWith(type: FieldType, vararg operators: String) = FieldSchema(
        name = "declared-operators-schema",
        fields = mapOf(
            FieldId(value = "field") to FieldDefinition(
                id = FieldId(value = "field"),
                type = type,
                operators = operators.mapTo(mutableSetOf()) { op -> OperatorId(value = op) }
            )
        )
    )

    // ── an operator the type cannot compile is reported against the schema ────

    @Test
    fun `a boolean field declaring an ordering operator is reported`() {
        val diagnostics = validateSchema(schema = schemaWith(type = FieldType.BOOLEAN, "gt"))

        val errors = diagnostics.filter { it.severity == Severity.ERROR }
        assertEquals(
            expected = 1,
            actual = errors.size,
            message = "exactly one diagnostic, against the declaration: $diagnostics"
        )
        assertTrue(
            actual = errors.single().message.contains(other = "boolean"),
            message = "the message must name the type that cannot support it: ${errors.single().message}"
        )
    }

    @Test
    fun `a string set field declaring a text operator is reported`() {
        val errors = validateSchema(schema = schemaWith(type = FieldType.STRING_SET, "startsWith"))
            .filter { it.severity == Severity.ERROR }

        assertEquals(expected = 1, actual = errors.size, message = "one diagnostic for one bad entry")
    }

    @Test
    fun `a declared operator the type supports is not reported`() {
        val diagnostics = validateSchema(schema = schemaWith(type = FieldType.TEXT, "equals", "contains"))

        assertTrue(
            actual = diagnostics.none { it.severity == Severity.ERROR },
            message = "a legal narrowing must stay silent: $diagnostics"
        )
    }

    /**
     * `!=` is exempt: no type's set names it because the parser routes symbolic inequality through the
     * expression engine, so declaring it is legitimate. See `OperatorUtils.isKnownOperator`.
     */
    @Test
    fun `a declared inequality symbol is not reported`() {
        val diagnostics = validateSchema(schema = schemaWith(type = FieldType.TEXT, "equals", "!="))

        assertTrue(
            actual = diagnostics.none { it.severity == Severity.ERROR },
            message = "'!=' has no place in a type's set by design: $diagnostics"
        )
    }

    // ── the narrowed list is what a rule is checked against ───────────────────

    @Test
    fun `a named operator outside the declared list is rejected`() {
        val diagnostics = validate(
            schema = schemaWith(type = FieldType.TEXT, "equals"),
            condition = """field contains "x""""
        )

        assertTrue(
            actual = diagnostics.any { diagnostic ->
                diagnostic.severity == Severity.ERROR && diagnostic.message.contains(other = "not allowed")
            },
            message = "the declared list must still restrict the named path: $diagnostics"
        )
    }

    /**
     * The symbolic path used to skip the check entirely: `requiresModernPath` routes every `==`
     * through a `ComparisonExpressionAst`, and the whitelist lived only in `validateCondition`.
     */
    @Test
    fun `a symbolic equality outside the declared list is rejected`() {
        val diagnostics = validate(
            schema = schemaWith(type = FieldType.TEXT, "contains"),
            condition = """field == "x""""
        )

        assertTrue(
            actual = diagnostics.any { diagnostic ->
                diagnostic.severity == Severity.ERROR && diagnostic.message.contains(other = "not allowed")
            },
            message = "'==' must be checked against the declared list too: $diagnostics"
        )
    }

    @Test
    fun `a symbolic equality inside the declared list is accepted`() {
        val diagnostics = validate(
            schema = schemaWith(type = FieldType.TEXT, "equals"),
            condition = """field == "x""""
        )

        assertTrue(
            actual = diagnostics.none { it.severity == Severity.ERROR },
            message = "declaring 'equals' must keep '==' legal: $diagnostics"
        )
    }

    /** Inequality is equality negated, so declaring one admits the other. */
    @Test
    fun `a symbolic inequality is admitted by a declared equals`() {
        val diagnostics = validate(
            schema = schemaWith(type = FieldType.TEXT, "equals"),
            condition = """field != "x""""
        )

        assertTrue(
            actual = diagnostics.none { it.severity == Severity.ERROR },
            message = "'!=' must not require its own declaration alongside 'equals': $diagnostics"
        )
    }

    @Test
    fun `a field declaring no operators keeps every symbolic comparison`() {
        val diagnostics = validate(
            schema = FieldSchema(
                name = "unrestricted",
                fields = mapOf(
                    FieldId(value = "field") to FieldDefinition(
                        id = FieldId(value = "field"),
                        type = FieldType.TEXT
                    )
                )
            ),
            condition = """field == "x""""
        )

        assertTrue(
            actual = diagnostics.none { it.severity == Severity.ERROR },
            message = "no declared list means no restriction: $diagnostics"
        )
    }

    private fun validateSchema(schema: FieldSchema) =
        Validator.validate(asts = emptyList(), schema = schema, actions = null).diagnostics

    private fun validate(schema: FieldSchema, condition: String) = Validator.validate(
        asts = Parser(
            input = """
                rule "declared-operators-test" {
                  when
                    $condition
                  then
                    flag "ok"
                }
            """.trimIndent()
        ).parseRules(),
        schema = schema,
        actions = null,
    ).diagnostics
}
