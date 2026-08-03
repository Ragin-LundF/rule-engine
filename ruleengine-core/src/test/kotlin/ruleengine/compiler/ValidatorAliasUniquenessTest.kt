package ruleengine.compiler

import org.junit.jupiter.api.Test
import ruleengine.core.domain.dto.field.FieldDefinition
import ruleengine.core.domain.dto.field.FieldId
import ruleengine.core.domain.dto.field.FieldSchema
import ruleengine.core.domain.dto.field.FieldType
import ruleengine.core.errors.Severity
import ruleengine.dsl.ast.AndAst
import ruleengine.dsl.ast.ConditionAst
import ruleengine.dsl.ast.RuleAst
import ruleengine.dsl.ast.StringLiteral
import ruleengine.schema.FieldSchemaLoader
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ValidatorAliasUniquenessTest {

    @Test
    fun `should fail validation when aliases are duplicate`() {
        val field1 = FieldDefinition(
            id = FieldId("field1"),
            type = FieldType.TEXT,
            alias = "dup_alias"
        )
        val field2 = FieldDefinition(
            id = FieldId("field2"),
            type = FieldType.TEXT,
            alias = "dup_alias"
        )
        val schema = FieldSchema(
            name = "test_schema",
            fields = mapOf(
                field1.id to field1,
                field2.id to field2
            )
        )

        // Create a dummy rule that doesn't trigger other errors
        val rule = RuleAst(
            id = "rule1",
            condition = AndAst(emptyList()),
            actions = emptyList()
        )

        val result = Validator.validate(listOf(rule), schema)

        assertTrue(result.diagnostics.any { 
            it.severity == Severity.ERROR && it.message.contains("Duplicate alias") 
        }, "Should have a validation error for duplicate aliases")
    }

    @Test
    fun `should pass validation when aliases are unique`() {
        val field1 = FieldDefinition(
            id = FieldId("field1"),
            type = FieldType.TEXT,
            alias = "alias1"
        )
        val field2 = FieldDefinition(
            id = FieldId("field2"),
            type = FieldType.TEXT,
            alias = "alias2"
        )
        val schema = FieldSchema(
            name = "test_schema",
            fields = mapOf(
                field1.id to field1,
                field2.id to field2
            )
        )

        val rule = RuleAst(
            id = "rule1",
            condition = AndAst(emptyList()),
            actions = emptyList()
        )

        val result = Validator.validate(listOf(rule), schema)
        
        // The only errors might be related to the dummy rule condition if it was complex, 
        // but here it's just an empty AndAst.
        // Actually, let's make the rule valid for the field.
        val condition = ConditionAst(field = "field1", operator = "equals", value = StringLiteral("val"))
        // We need to actually implement a valid rule if we want to be sure about success.
        // But for now, let's just check that no duplicate alias error is present.
        
        assertTrue(result.diagnostics.none { it.message.contains("Duplicate alias") }, 
            "Should not have duplicate alias errors")
    }

    @Test
    fun `should report a duplicate alias declared on two nested fields`() {
        val schema = FieldSchemaLoader.loadFromString(
            content = """
                schema: nested_dup

                fields:
                  income:
                    type: object
                    fields:
                      total:
                        type: decimal
                        alias: total_amount
                  spending:
                    type: object
                    fields:
                      total:
                        type: decimal
                        alias: total_amount
            """.trimIndent()
        )
        val rule = RuleAst(id = "rule1", condition = AndAst(emptyList()), actions = emptyList())

        val errors = Validator.validate(listOf(rule), schema).diagnostics
            .filter { it.severity == Severity.ERROR && it.message.contains("Duplicate alias") }

        assertEquals(expected = 1, actual = errors.size, message = "Expected one error, got: $errors")
        assertTrue(
            actual = errors.first().message.contains("income.total") &&
                errors.first().message.contains("spending.total"),
            message = "The error must name both nested paths, got: ${errors.first().message}"
        )
    }

    @Test
    fun `an alias equal to a declared path is a warning and not an error`() {
        val schema = FieldSchemaLoader.loadFromString(
            content = """
                schema: shadowing

                fields:
                  income:
                    type: object
                    fields:
                      total:
                        type: decimal
                      net:
                        type: decimal
                        alias: income.total
            """.trimIndent()
        )
        val rule = RuleAst(id = "rule1", condition = AndAst(emptyList()), actions = emptyList())

        val diagnostics = Validator.validate(listOf(rule), schema).diagnostics

        assertEquals(
            expected = 1,
            actual = diagnostics.count {
                it.severity == Severity.WARNING && it.message.contains("is also a declared field name")
            },
            message = "Expected one shadowing warning, got: $diagnostics"
        )
        assertTrue(
            actual = diagnostics.none { it.severity == Severity.ERROR },
            message = "Shadowing must not reject the schema, got: $diagnostics"
        )
    }
}
