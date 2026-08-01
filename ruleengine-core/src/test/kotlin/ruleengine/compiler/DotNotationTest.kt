package ruleengine.compiler

import org.junit.jupiter.api.Test
import ruleengine.core.domain.dto.field.FieldDefinition
import ruleengine.core.domain.dto.field.FieldId
import ruleengine.core.domain.dto.field.FieldSchema
import ruleengine.core.domain.dto.field.FieldType
import ruleengine.dsl.parser.Parser
import kotlin.test.assertTrue

class DotNotationTest {
    @Test
    fun testDotNotation() {
        val fieldId = FieldId(value = "user.profile.age")
        val fieldDef = FieldDefinition(id = fieldId, type = FieldType.INTEGER)
        val schema = FieldSchema(name = "test", fields = mapOf(fieldId to fieldDef))
        val asts = Parser(input = "rule \"test\" { when user.profile.age equals 25 then label \"x\" }").parseRules()
        val result = Validator.validate(asts = asts, schema = schema)
        assertTrue(
            actual = result.isValid,
            message = "Should be valid: ${result.diagnostics}"
        )
    }
}
