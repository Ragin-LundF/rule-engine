package ruleengine.compiler

import ruleengine.compiler.support.OperatorSupport
import ruleengine.core.domain.OperatorNames
import ruleengine.core.domain.dto.field.FieldDefinition
import ruleengine.core.domain.dto.field.FieldId
import ruleengine.core.domain.dto.field.FieldSchema
import ruleengine.core.domain.dto.field.FieldType
import ruleengine.core.domain.dto.field.isTemporal
import ruleengine.core.errors.CompilationException
import ruleengine.core.errors.Severity
import ruleengine.dsl.parser.Parser
import kotlin.test.Test
import kotlin.test.assertTrue
import kotlin.test.fail

/**
 * Every [FieldType] against every operator in [OperatorNames.ALL], in one table.
 *
 * The bugs this guards against all had the same shape: two tables that disagreed and nothing that
 * compared them. A sample schema declared `in` on an `integer` field — legal to the loader, legal to
 * the validator, and a `CompilationException` at load time. Three types accepted an operator at
 * validation and then threw in the compiler. A point test finds one such cell; only a matrix finds
 * the class.
 *
 * Two invariants, asserted for all 117 cells:
 *
 * 1. **`OperatorSupport` is the whole truth.** An operator the type's set names must validate clean
 *    *and* compile; one it does not name must be reported as a [Severity.ERROR].
 * 2. **The compiler is never stricter than the validator.** Nothing that validates clean may throw
 *    a [CompilationException] — a rule that passed validation is a rule the engine promised to run.
 *
 * Driven from `FieldType.entries` and `OperatorNames.ALL`, so a new type or a new operator joins the
 * matrix without anyone remembering to add it.
 */
class OperatorMatrixTest {

    @Test
    fun `every operator is either supported by a type or rejected for it`() {
        val failures = mutableListOf<String>()

        for (type in FieldType.entries) {
            for (operator in OperatorNames.ALL) {
                val supported = operator in OperatorSupport.supportedOperatorsFor(type = type)
                val errors = validationErrorsFor(type = type, operator = operator)

                if (supported && errors.isNotEmpty()) {
                    failures += "$type + '$operator' is in supportedOperatorsFor but the validator " +
                        "rejects it: $errors"
                }
                if (!supported && errors.isEmpty()) {
                    failures += "$type + '$operator' is not in supportedOperatorsFor but the " +
                        "validator accepts it — it would reach the compiler unguarded"
                }
            }
        }

        assertTrue(actual = failures.isEmpty(), message = failures.joinToString(separator = "\n"))
    }

    /**
     * Nothing the validator passes may throw when compiled.
     *
     * The direction that actually bit: a `boolean` field declaring `gt`, a `text` field declaring
     * `!=`, and `string_set` with a bare string all validated and then died in `Compiler`, so the
     * author saw a stack trace instead of a diagnostic naming their schema.
     */
    @Test
    fun `nothing that validates clean throws when compiled`() {
        val failures = mutableListOf<String>()

        for (type in FieldType.entries) {
            for (operator in OperatorNames.ALL) {
                if (validationErrorsFor(type = type, operator = operator).isNotEmpty()) {
                    continue
                }
                val schema = schemaFor(type = type)
                val asts = Parser(input = ruleFor(type = type, operator = operator)).parseRules()
                runCatching { Compiler.compileRules(asts = asts, schema = schema) }
                    .onFailure { cause ->
                        failures += "$type + '$operator' validates clean but compilation threw: " +
                            "${cause::class.simpleName}: ${cause.message}"
                    }
            }
        }

        assertTrue(actual = failures.isEmpty(), message = failures.joinToString(separator = "\n"))
    }

    /** Every supported operator must actually produce a compiled expression, not merely not throw. */
    @Test
    fun `every supported operator compiles to an expression`() {
        for (type in FieldType.entries) {
            for (operator in OperatorSupport.supportedOperatorsFor(type = type)) {
                val schema = schemaFor(type = type)
                val asts = Parser(input = ruleFor(type = type, operator = operator)).parseRules()
                val compiled = runCatching { Compiler.compileRules(asts = asts, schema = schema) }
                    .getOrElse { cause -> fail("$type + '$operator' failed to compile: ${cause.message}") }

                assertTrue(
                    actual = compiled.single().expression.cost.ordinal >= 0,
                    message = "$type + '$operator' produced no usable expression",
                )
            }
        }
    }

    /** Structure types are navigated or aggregated, never compared, so they support nothing. */
    @Test
    fun `structure types support no operator at all`() {
        for (type in listOf(FieldType.COLLECTION, FieldType.OBJECT)) {
            assertTrue(
                actual = OperatorSupport.supportedOperatorsFor(type = type).isEmpty(),
                message = "$type must support no direct comparison",
            )
        }
    }

    private fun validationErrorsFor(type: FieldType, operator: String): List<String> {
        val asts = Parser(input = ruleFor(type = type, operator = operator)).parseRules()
        return Validator.validate(asts = asts, schema = schemaFor(type = type))
            .diagnostics
            .filter { diagnostic -> diagnostic.severity == Severity.ERROR }
            .map { diagnostic -> diagnostic.message }
    }

    private fun schemaFor(type: FieldType) = FieldSchema(
        name = "matrix-schema",
        fields = mapOf(
            FIELD to FieldDefinition(
                id = FIELD,
                type = type,
                // Left undeclared on purpose: an empty list means "the type's defaults", which is the
                // set this matrix is about. A declared list would test the intersection instead.
                fields = if (type == FieldType.COLLECTION || type == FieldType.OBJECT) {
                    mapOf(MEMBER to FieldDefinition(id = MEMBER, type = FieldType.TEXT))
                } else {
                    emptyMap()
                },
            )
        )
    )

    private fun ruleFor(type: FieldType, operator: String) = """
        rule "matrix" {
          description "one cell of the operator matrix"
          when
            ${FIELD.value} $operator ${literalFor(type = type, operator = operator)}
          then
            flag "ok"
        }
    """.trimIndent()

    /**
     * A right-hand side that is well formed for the pair, so a rejection can only be about the
     * operator itself and never about a malformed literal.
     */
    private fun literalFor(type: FieldType, operator: String): String {
        if (operator == OperatorNames.BETWEEN) {
            // The parser accepts only a number or a quoted date as a bound, whatever the field's type
            // — so a boolean's `true` cannot stand in here and the pair must be numeric.
            return if (type.isTemporal) "${itemFor(type = type)} ${itemFor(type = type)}" else "1 2"
        }
        val item = itemFor(type = type)
        return if (operator in LIST_OPERATORS) "[$item]" else item
    }

    /** One well-formed literal of [type], in the spelling the DSL uses. */
    private fun itemFor(type: FieldType): String = when (type) {
        FieldType.INTEGER -> "1"
        FieldType.DECIMAL -> "1.0"
        FieldType.BOOLEAN -> "true"
        FieldType.DATE -> """"2024-01-01""""
        FieldType.DATE_TIME -> """"2024-01-01T00:00:00""""
        FieldType.TEXT, FieldType.STRING_SET, FieldType.COLLECTION, FieldType.OBJECT -> """"a""""
    }

    private companion object {
        val LIST_OPERATORS = setOf(
            OperatorNames.IN,
            OperatorNames.CONTAINS_ANY,
            OperatorNames.CONTAINS_ALL,
        )
        val FIELD = FieldId(value = "field")
        val MEMBER = FieldId(value = "member")
    }
}
