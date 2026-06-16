package ruleengine.compiler

import ruleengine.core.domain.FieldDefinition
import ruleengine.core.domain.FieldId
import ruleengine.core.domain.FieldSchema
import ruleengine.core.domain.FieldType
import ruleengine.core.domain.OperatorId
import ruleengine.dsl.ast.ActionAst
import ruleengine.dsl.ast.ConditionAst
import ruleengine.dsl.ast.RuleAst
import ruleengine.dsl.ast.StringLiteral
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class UnsupportedFieldTypeValidationTest {
    @Test
    fun `validator rejects boolean field types early`() {
        val schema = FieldSchema(
            name = "test",
            fields = mapOf(
                FieldId(value = "active") to FieldDefinition(
                    id = FieldId(value = "active"),
                    type = FieldType.BOOLEAN,
                    operators = setOf(OperatorId(value = "equals"))
                )
            )
        )
        val asts = listOf(
            RuleAst(
                id = "boolean-rule",
                condition = ConditionAst(
                    field = "active",
                    operator = "equals",
                    value = StringLiteral(value = "true")
                ),
                actions = listOf(ActionAst(name = "label", arguments = listOf(StringLiteral(value = "x"))))
            )
        )

        val result = Validator.validate(asts = asts, schema = schema)

        assertFalse(actual = result.isValid)
        assertTrue(
            actual = result.diagnostics.any {
                it.message.contains(other = "Field 'active' uses unsupported field type BOOLEAN")
            },
            message = "Expected boolean diagnostic, got: ${result.diagnostics}"
        )
    }

    @Test
    fun `validator rejects date field types early`() {
        val schema = FieldSchema(
            name = "test",
            fields = mapOf(
                FieldId(value = "createdAt") to FieldDefinition(
                    id = FieldId(value = "createdAt"),
                    type = FieldType.DATE,
                    operators = setOf(OperatorId(value = "equals"))
                )
            )
        )
        val asts = listOf(
            RuleAst(
                id = "date-rule",
                condition = ConditionAst(
                    field = "createdAt",
                    operator = "equals",
                    value = StringLiteral(value = "2024-01-01")
                ),
                actions = listOf(ActionAst(name = "label", arguments = listOf(StringLiteral(value = "x"))))
            )
        )

        val result = Validator.validate(asts = asts, schema = schema)

        assertFalse(actual = result.isValid)
        assertTrue(
            actual = result.diagnostics.any {
                it.message.contains(other = "Field 'createdAt' uses unsupported field type DATE")
            },
            message = "Expected date diagnostic, got: ${result.diagnostics}"
        )
    }
}


