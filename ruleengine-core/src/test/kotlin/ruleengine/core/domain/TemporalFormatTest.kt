package ruleengine.core.domain

import ruleengine.core.domain.dto.field.FieldType
import java.time.LocalDate
import java.time.LocalDateTime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

class TemporalFormatTest {

    @Test
    fun `reads an ISO date when no pattern is declared`() {
        assertEquals(
            expected = LocalDate.of(2024, 1, 31),
            actual = TemporalFormat.parseDate(text = "2024-01-31", pattern = null),
        )
    }

    @Test
    fun `reads a date in the declared pattern`() {
        assertEquals(
            expected = LocalDate.of(2024, 1, 31),
            actual = TemporalFormat.parseDate(text = "31.01.2024", pattern = "dd.MM.yyyy"),
        )
    }

    @Test
    fun `a declared pattern replaces ISO rather than extending it`() {
        assertNull(actual = TemporalFormat.parseDate(text = "2024-01-31", pattern = "dd.MM.yyyy"))
        assertNull(actual = TemporalFormat.parseDate(text = "31.01.2024", pattern = null))
    }

    @Test
    fun `reads a date time with and without a declared pattern`() {
        assertEquals(
            expected = LocalDateTime.of(2024, 1, 31, 13, 45),
            actual = TemporalFormat.parseDateTime(text = "2024-01-31T13:45", pattern = null),
        )
        assertEquals(
            expected = LocalDateTime.of(2024, 1, 31, 13, 45),
            actual = TemporalFormat.parseDateTime(text = "31.01.2024 13:45", pattern = "dd.MM.yyyy HH:mm"),
        )
    }

    @Test
    fun `returns null instead of throwing on a value that does not match`() {
        assertNull(actual = TemporalFormat.parseDate(text = "not a date", pattern = null))
        assertNull(actual = TemporalFormat.parseDateTime(text = "2024-01-31", pattern = null))
    }

    @Test
    fun `renders a sample in the declared pattern`() {
        assertEquals(
            expected = "31.01.2024",
            actual = TemporalFormat.sample(type = FieldType.DATE, pattern = "dd.MM.yyyy"),
        )
        assertEquals(
            expected = "31.01.2024 13:45",
            actual = TemporalFormat.sample(type = FieldType.DATE_TIME, pattern = "dd.MM.yyyy HH:mm"),
        )
    }

    @Test
    fun `renders an ISO sample when no pattern is declared`() {
        val dateSample = TemporalFormat.sample(type = FieldType.DATE, pattern = null)
        assertNotNull(actual = TemporalFormat.parseDate(text = dateSample, pattern = null))

        val dateTimeSample = TemporalFormat.sample(type = FieldType.DATE_TIME, pattern = null)
        assertNotNull(actual = TemporalFormat.parseDateTime(text = dateTimeSample, pattern = null))
        assertTrue(
            actual = dateTimeSample.contains(other = "T"),
            message = "an ISO date-time sample should carry a time component, was '$dateTimeSample'",
        )
    }

    @Test
    fun `accepts usable patterns`() {
        assertNull(actual = TemporalFormat.unusableReason(type = FieldType.DATE, pattern = "dd.MM.yyyy"))
        assertNull(actual = TemporalFormat.unusableReason(type = FieldType.DATE, pattern = "yyyy/MM/dd"))
        assertNull(actual = TemporalFormat.unusableReason(type = FieldType.DATE_TIME, pattern = "dd.MM.yyyy HH:mm"))
        assertNull(
            actual = TemporalFormat.unusableReason(type = FieldType.DATE_TIME, pattern = "yyyy-MM-dd HH:mm:ss")
        )
    }

    @Test
    fun `rejects a malformed pattern`() {
        assertNotNull(actual = TemporalFormat.unusableReason(type = FieldType.DATE, pattern = "QQQQQQ"))
    }

    /** `ofPattern` accepts these, but no value can ever be read back from them. */
    @Test
    fun `rejects a pattern that cannot represent a complete value`() {
        assertNotNull(actual = TemporalFormat.unusableReason(type = FieldType.DATE, pattern = "MM-dd"))
        assertNotNull(actual = TemporalFormat.unusableReason(type = FieldType.DATE, pattern = "yyyy"))
        assertNotNull(actual = TemporalFormat.unusableReason(type = FieldType.DATE_TIME, pattern = "yyyy-MM-dd"))
        assertNotNull(actual = TemporalFormat.unusableReason(type = FieldType.DATE_TIME, pattern = "HH:mm"))
    }

    @Test
    fun `caches one formatter per pattern`() {
        assertSame(
            expected = TemporalFormat.formatterFor(pattern = "dd.MM.yyyy"),
            actual = TemporalFormat.formatterFor(pattern = "dd.MM.yyyy"),
        )
    }
}
