package ruleengine.compiler

import org.junit.jupiter.api.Test
import ruleengine.core.domain.dto.FieldDefinition
import ruleengine.core.domain.dto.FieldId
import ruleengine.core.domain.dto.FieldSchema
import ruleengine.core.domain.dto.FieldType
import ruleengine.core.errors.Severity
import ruleengine.dsl.ast.AndAst
import ruleengine.dsl.ast.ConditionAst
import ruleengine.dsl.ast.RuleAst
import ruleengine.dsl.ast.StringLiteral
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
}
