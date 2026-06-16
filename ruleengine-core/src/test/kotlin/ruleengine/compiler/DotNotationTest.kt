package ruleengine.compiler

import org.junit.jupiter.api.Test
import ruleengine.core.domain.*
import ruleengine.dsl.ast.*
import ruleengine.dsl.parser.Parser
import kotlin.test.assertTrue
import kotlin.test.assertFalse

class DotNotationTest {
    @Test
    fun testDotNotation() {
        val fieldId = FieldId("user.profile.age")
        val fieldDef = FieldDefinition(id = fieldId, type = FieldType.INTEGER)
        val schema = FieldSchema("test", mapOf(fieldId to fieldDef))
        val asts = Parser("rule \"test\" { when user.profile.age equals 25 then label \"x\" }").parseRules()
        val result = Validator.validate(asts, schema)
        assertTrue(
            result.isValid,
            "Should be valid: ${result.diagnostics}"
        )
    }
}
