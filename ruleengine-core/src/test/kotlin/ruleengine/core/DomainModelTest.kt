package ruleengine.core

import kotlin.test.Test
import kotlin.test.assertEquals
import ruleengine.core.domain.FieldId

class DomainModelTest {
    @Test
    fun `field id equality and value`() {
        val a = FieldId(value = "purpose")
        val b = FieldId(value = "purpose")
        assertEquals(expected = a, actual = b)
        assertEquals(expected = "purpose", actual = a.value)
    }
}

