package ruleengine.compiler

import org.junit.jupiter.api.Test
import ruleengine.core.domain.dto.FieldDefinition
import ruleengine.core.domain.dto.FieldId
import ruleengine.core.domain.dto.FieldSchema
import ruleengine.core.domain.dto.FieldType
import ruleengine.core.errors.Severity
import ruleengine.dsl.ast.ConditionAst
import ruleengine.dsl.ast.StringLiteral
import kotlin.test.assertTrue

class ValidatorAliasResolutionTest {

    @Test
    fun `should resolve alias in condition`() {
        val fieldId = FieldId("user.profile.age")
        val fieldDef = FieldDefinition(
            id = fieldId,
            type = FieldType.INTEGER,
            alias = "age"
        )
        val schema = FieldSchema(
            name = "test_schema",
            fields = mapOf(fieldId to fieldDef)
        )

        val condition = ConditionAst(
            field = "age",
            operator = "equals",
            value = ruleengine.dsl.ast.NumberLiteral("25")
        )
        val rule = ruleengine.dsl.ast.RuleAst(
            id = "rule1",
            condition = condition,
            actions = emptyList()
        )

        val result = Validator.validate(listOf(rule), schema)
        assertTrue(result.isValid, "Rule with alias should be valid. Diagnostics: ${result.diagnostics}")
    }

    @Test
    fun `should fail validation when alias refers to non-existent field`() {
        val fieldId = FieldId("user.profile.age")
        val fieldDef = FieldDefinition(
            id = fieldId,
            type = FieldType.INTEGER,
            alias = "age"
        )
        val schema = FieldSchema(
            name = "test_schema",
            fields = mapOf(fieldId to fieldDef)
        )

        val condition = ConditionAst(
            field = "unknown_alias",
            operator = "equals",
            value = StringLiteral("25")
        )
        val rule = ruleengine.dsl.ast.RuleAst(
            id = "rule1",
            condition = condition,
            actions = emptyList()
        )

        val result = Validator.validate(listOf(rule), schema)
        assertTrue(result.diagnostics.any { it.severity == Severity.ERROR }, "Should have error for unknown alias")
    }
}
