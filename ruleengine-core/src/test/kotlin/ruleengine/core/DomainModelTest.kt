package ruleengine.core

import ruleengine.core.domain.dto.field.FieldId
import kotlin.test.Test
import kotlin.test.assertEquals

class DomainModelTest {
    @Test
    fun `field id equality and value`() {
        val a = FieldId(value = "purpose")
        val b = FieldId(value = "purpose")
        assertEquals(expected = a, actual = b)
        assertEquals(expected = "purpose", actual = a.value)
    }
}

