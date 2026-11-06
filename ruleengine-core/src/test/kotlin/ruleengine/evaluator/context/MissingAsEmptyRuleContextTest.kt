package ruleengine.evaluator.context

import ruleengine.builder.LoadedRuleEngine
import ruleengine.compiler.Compiler
import ruleengine.core.domain.dto.field.FieldDefinition
import ruleengine.core.domain.dto.field.FieldId
import ruleengine.core.domain.dto.field.FieldSchema
import ruleengine.core.domain.dto.field.FieldType
import ruleengine.dsl.parser.Parser
import ruleengine.evaluator.RuleEngine
import ruleengine.evaluator.ScopedEvaluation
import kotlin.test.Test
import kotlin.test.assertEquals

/** `missing: empty` on a member of collection elements, and in a scoped run. */
class MissingAsEmptyRuleContextTest {

    private fun field(
        name: String,
        type: FieldType,
        missingAsEmpty: Boolean = false,
        members: List<FieldDefinition> = emptyList(),
    ): FieldDefinition {
        return FieldDefinition(
            id = FieldId(value = name),
            type = type,
            missingAsEmpty = missingAsEmpty,
            fields = members.associateBy { it.id },
        )
    }

    private val accounts = field(
        name = "accounts",
        type = FieldType.COLLECTION,
        members = listOf(
            field(name = "id", type = FieldType.TEXT),
            field(name = "labels", type = FieldType.STRING_SET, missingAsEmpty = true),
        ),
    )

    private val schema = FieldSchema(
        name = "accounts",
        fields = listOf(accounts, field(name = "watch", type = FieldType.STRING_SET, missingAsEmpty = true))
            .associateBy { it.id },
    )

    private val input = mapOf(
        "accounts" to listOf(mapOf("id" to "a"), mapOf("id" to "b", "labels" to listOf("vip"))),
    )

    @Test
    fun `a member of every element reads as empty where the element does not carry it`() {
        val asts = Parser(input = rule(condition = "count(accounts[count(labels) == 0]) == 1")).parseRules()
        val compiled = Compiler.compileRules(asts = asts, schema = schema)
        val prepared = PreparedRuleContext.prepare(ctx = MapRuleContext(map = input), schema = schema)

        val matched = RuleEngine(compiledRules = compiled).evaluate(prepared = prepared).matches.isNotEmpty()

        assertEquals(expected = true, actual = matched)
    }

    /** A scoped run sees the member's own default and the document's, per member. */
    @Test
    fun `a scoped run reads member and document defaults for every member`() {
        val memberSchema = ScopedEvaluation.memberSchema(schema = schema, scope = "accounts")!!
        val asts = Parser(input = rule(condition = "count(labels) == 0 and count(watch) == 0")).parseRules()
        val engine = LoadedRuleEngine(
            entryId = "accounts",
            engine = RuleEngine(compiledRules = Compiler.compileRules(asts = asts, schema = memberSchema)),
            schema = schema,
            scope = "accounts",
        )

        val matchedMembers = engine.evaluate(input = input).matches.map { it.scopeMember }

        assertEquals(expected = listOf("a"), actual = matchedMembers)
    }

    private fun rule(condition: String): String {
        return """
            rule "empty-labels" {
              description "reads labels that may be missing"
              when
                $condition
              then
                flag "ok"
            }
        """.trimIndent()
    }
}
