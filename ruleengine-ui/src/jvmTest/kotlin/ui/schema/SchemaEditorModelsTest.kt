package ui.schema

import ruleengine.compiler.Validator
import ruleengine.compiler.operators.OperatorUtils
import ruleengine.core.domain.dto.field.FieldType
import ruleengine.core.domain.dto.field.isNormalizable
import ruleengine.core.errors.Severity
import ruleengine.dsl.parser.Parser
import ruleengine.schema.FieldSchemaLoader
import ui.autocompletion.defaultOperatorsForType
import ui.builder.OperatorOptions
import ui.schema.model.EditableField
import ui.schema.model.SchemaEditorState
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Parity between what the visual schema editor offers and what the engine accepts.
 *
 * The editor shares the engine's [FieldType] now, so the type list cannot drift. What is still
 * hand-written is the per-type *operator* table and the normalizer list, and the templates behind
 * "+ Add field" — these tests are what keeps those honest. The editor shipped for a while with three
 * field types missing from the menu, four operators the engine has never had, and two normalizers it
 * could not express.
 */
class SchemaEditorModelsTest {

    // ── field templates ───────────────────────────────────────────────────────

    @Test
    fun `add field templates cover every field type exactly once`() {
        val templated = FieldTemplates.map { (_, template) -> template.type }
            // the blank template intentionally starts as text, like a fresh row
            .drop(n = 1)

        assertEquals(
            expected = FieldType.entries.toList(),
            actual = templated,
            message = "every type needs a template, in enum order, or it is unreachable from the menu",
        )
    }

    @Test
    fun `the first template is a blank row`() {
        val (label, template) = FieldTemplates.first()
        assertEquals(expected = "Blank field", actual = label)
        assertEquals(expected = EditableField(), actual = template)
    }

    @Test
    fun `every template declares only operators valid for its own type`() {
        FieldTemplates.forEach { (label, template) ->
            val supported = operatorsFor(type = template.type)
            val invalid = template.operators.filterNot { it in supported }
            assertTrue(
                actual = invalid.isEmpty(),
                message = "template '$label' declares $invalid, which ${template.type.yamlValue} does not support",
            )
        }
    }

    @Test
    fun `every template declares only normalizers the engine knows, and only where they apply`() {
        FieldTemplates.forEach { (label, template) ->
            val unknown = template.normalizers.filterNot { it in KnownNormalizers }
            assertTrue(actual = unknown.isEmpty(), message = "template '$label' declares unknown $unknown")
            assertTrue(
                actual = template.normalizers.isEmpty() || template.type.isNormalizable,
                message = "template '$label' normalizes a ${template.type.yamlValue}, which the engine never does",
            )
        }
    }

    @Test
    fun `every template round-trips through the yaml bridge unchanged`() {
        FieldTemplates.forEach { (label, template) ->
            if (template.path.isBlank()) return@forEach
            val state = SchemaEditorState(schemaName = "templates", fields = listOf(template))

            val reloaded = FieldSchemaYamlBridge.fromYaml(FieldSchemaYamlBridge.toYaml(state))

            assertTrue(actual = !reloaded.isReadOnly, message = "template '$label' produced yaml that fails to load")
            assertEquals(expected = template, actual = reloaded.fields.single(), message = "template '$label'")
        }
    }

    // ── operators ─────────────────────────────────────────────────────────────

    @Test
    fun `every offered operator is accepted by the engine for that field type`() {
        FieldType.entries.forEach { type ->
            operatorsFor(type = type).forEach { operator ->
                val condition = conditionFor(type = type, operator = operator)
                    ?: error("no sample condition for $type $operator — extend the test, not the list")
                val errors = validate(type = type, operators = listOf(operator), condition = condition)
                assertTrue(
                    actual = errors.isEmpty(),
                    message = "'$condition' on a ${type.yamlValue} field should be valid, got: $errors",
                )
            }
        }
    }

    /**
     * The same parity check, widened to the **other two** operator tables the UI keeps.
     *
     * `SchemaEditorModels.operatorsFor` above is only one of three: the Builder has
     * `OperatorOptions.forField` and the editor has `defaultOperatorsForType`, and all three are
     * deliberately allowed to be *narrower* than the engine. Being **wider** is always a bug — a
     * bundled sample offered `in` on an `integer` field and the rule using it could not compile — and
     * nothing checked those two until now.
     *
     * `!=` is exempt: no type's operator set names it, because the parser routes symbolic inequality
     * through the expression engine rather than the named-operator path.
     */
    @Test
    fun `every operator the builder and the editor offer is accepted by the engine`() {
        val failures = mutableListOf<String>()

        FieldType.entries.forEach { type ->
            val offered = OperatorOptions.forField(fieldType = type.yamlValue) +
                defaultOperatorsForType(fieldType = type)

            offered.distinct()
                .filterNot { operator -> OperatorUtils.normalizeOperator(op = operator) == "!=" }
                .forEach { operator ->
                    val condition = conditionFor(type = type, operator = operator)
                        ?: error("no sample condition for $type $operator — extend the test, not the list")
                    // Declared empty, so the field keeps its type defaults: this asks what the *type*
                    // allows, not what one schema narrowed it to.
                    val errors = validate(type = type, operators = emptyList(), condition = condition)
                    if (errors.isNotEmpty()) {
                        failures += "'$condition' on a ${type.yamlValue} field is offered by the UI " +
                            "but rejected by the engine: $errors"
                    }
                }
        }

        assertTrue(actual = failures.isEmpty(), message = failures.joinToString(separator = "\n"))
    }

    @Test
    fun `structure types offer no operators`() {
        assertEquals(expected = emptyList(), actual = operatorsFor(type = FieldType.COLLECTION))
        assertEquals(expected = emptyList(), actual = operatorsFor(type = FieldType.OBJECT))
    }

    @Test
    fun `the operator list no longer offers names the engine does not have`() {
        listOf("not_equals", "not_contains", "isEmpty", "isNotEmpty").forEach { bogus ->
            assertTrue(actual = bogus !in KnownOperators, message = "'$bogus' is not an engine operator")
        }
    }

    @Test
    fun `regex is offered for text fields`() {
        assertTrue(actual = "regex" in operatorsFor(type = FieldType.TEXT))
    }

    // ── normalizers ───────────────────────────────────────────────────────────

    @Test
    fun `every offered normalizer is accepted by the engine`() {
        val yaml = buildString {
            appendLine("schema: normalizers")
            appendLine("fields:")
            appendLine("  purpose:")
            appendLine("    type: text")
            appendLine("    normalizers:")
            KnownNormalizers.forEach { appendLine("      - $it") }
        }

        // an unknown normalizer is a load error, so a clean load is the assertion
        val schema = FieldSchemaLoader.loadFromString(content = yaml, nameHint = "normalizers")

        assertEquals(
            expected = KnownNormalizers,
            actual = schema.fields.values.single().normalizers.map { it.value },
        )
    }

    // ── helpers ───────────────────────────────────────────────────────────────

    private fun validate(
        type: FieldType,
        operators: List<String>,
        condition: String,
    ): List<String> {
        val yaml = buildString {
            appendLine("schema: parity")
            appendLine("fields:")
            appendLine("  value:")
            appendLine("    type: ${type.yamlValue}")
            appendLine("    operators:")
            operators.forEach { appendLine("      - $it") }
        }
        val schema = FieldSchemaLoader.loadFromString(content = yaml, nameHint = "parity")
        val rule = """
            rule "parity" {
              when
                $condition
              then
                flag "ok"
            }
        """.trimIndent()

        return Validator.validate(asts = Parser(input = rule).parseRules(), schema = schema)
            .diagnostics
            .filter { it.severity == Severity.ERROR }
            .map { it.message }
    }

    /** A condition exercising [operator] on a `value` field of [type], or null when none applies. */
    private fun conditionFor(type: FieldType, operator: String): String? {
        val single = singleLiteralFor(type = type) ?: return null
        return when (operator) {
            "between" -> "value between ${rangeLiteralFor(type = type)}"
            // The Builder's tables spell the comparisons symbolically; the engine reads either.
            "==", "!=", ">", ">=", "<", "<=" -> "value $operator $single"
            // `in` reaches every scalar type now, so the sample list has to be typed like the field.
            "in" -> "value in [$single, $single]"
            "containsAny", "containsAll" -> """value $operator ["a", "b"]"""
            "regex" -> """value regex "^a""""
            else -> "value $operator $single"
        }
    }

    private fun singleLiteralFor(type: FieldType): String? {
        return when (type) {
            FieldType.TEXT, FieldType.STRING_SET -> "\"a\""
            FieldType.INTEGER -> "1"
            FieldType.DECIMAL -> "1.5"
            FieldType.BOOLEAN -> "true"
            FieldType.DATE -> "\"2024-01-31\""
            FieldType.DATE_TIME -> "\"2024-01-31T09:00:00\""
            FieldType.COLLECTION, FieldType.OBJECT -> null
        }
    }

    private fun rangeLiteralFor(type: FieldType): String {
        return when (type) {
            FieldType.INTEGER -> "1 10"
            FieldType.DECIMAL -> "1.5 10.5"
            FieldType.DATE -> "\"2024-01-01\" \"2024-12-31\""
            FieldType.DATE_TIME -> "\"2024-01-01T00:00:00\" \"2024-12-31T23:59:59\""
            else -> "1 10"
        }
    }
}
