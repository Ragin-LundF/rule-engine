package ruleengine.core.analysis

import ruleengine.dsl.parser.Parser
import kotlin.test.Test
import kotlin.test.assertEquals

class VariableUsageTest {

    @Test
    fun `a variable read in the filter of a joinByKey source is reported`() {
        val rule = Parser(
            input = """
                rule "join-reads-variable" {
                  when
                    count(joinByKey("period", credit: credits[amount > ${'$'}min].amount, debit: debits.amount)) > 0
                  then
                    flag "ok"
                }
            """.trimIndent()
        ).parseRules().single()

        assertEquals(expected = setOf("min"), actual = VariableUsage.readsOf(rule = rule))
    }
}
