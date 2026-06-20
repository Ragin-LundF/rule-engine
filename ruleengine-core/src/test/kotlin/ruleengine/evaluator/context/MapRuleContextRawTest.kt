package ruleengine.evaluator.context

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class MapRuleContextRawTest {

    @Test
    fun `resolve scalar field`() {
        val context = RuleContext.of("amount" to 100)
        val result = context.getRaw(fieldPath = listOf("amount"))
        assertEquals(100, result)
    }

    @Test
    fun `resolve nested object field`() {
        val context = RuleContext.of("customer" to mapOf("age" to 30))
        val result = context.getRaw(fieldPath = listOf("customer", "age"))
        assertEquals(30, result)
    }

    @Test
    fun `resolve array root`() {
        val transactions = listOf(
            mapOf("amount" to 10),
            mapOf("amount" to 20)
        )
        val context = RuleContext.of("transactions" to transactions)
        val result = context.getRaw(fieldPath = listOf("transactions"))
        assertEquals(transactions, result)
    }

    @Test
    fun `resolve array projection`() {
        val transactions = listOf(
            mapOf("amount" to 10),
            mapOf("amount" to 20)
        )
        val context = RuleContext.of("transactions" to transactions)
        val result = context.getRaw(fieldPath = listOf("transactions", "amount"))
        assertEquals(listOf(10, 20), result)
    }

    @Test
    fun `resolve array projection skips missing values`() {
        val transactions = listOf(
            mapOf("amount" to 10),
            mapOf("purpose" to "no amount here"),
            mapOf("amount" to 30)
        )
        val context = RuleContext.of("transactions" to transactions)
        val result = context.getRaw(fieldPath = listOf("transactions", "amount"))
        assertEquals(listOf(10, 30), result)
    }

    @Test
    fun `resolve missing field returns null`() {
        val context = RuleContext.of("amount" to 100)
        val result = context.getRaw(fieldPath = listOf("unknown"))
        assertNull(result)
    }

    @Test
    fun `resolve empty array projection returns null`() {
        val transactions = listOf(
            mapOf("purpose" to "no amount"),
            mapOf("purpose" to "also no amount")
        )
        val context = RuleContext.of("transactions" to transactions)
        val result = context.getRaw(fieldPath = listOf("transactions", "amount"))
        assertNull(result)
    }
}
