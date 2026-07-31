package ruleengine.core.domain

import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.time.temporal.TemporalAccessor
import java.util.concurrent.ConcurrentHashMap

/**
 * Reads and renders the date patterns declared by [FieldDefinition.format].
 *
 * A `null` pattern means ISO-8601, which is the engine's default. Everything that needs to know about
 * patterns goes through here, so the schema loader, the compiler, the evaluator and the UI all agree on
 * what a pattern accepts.
 */
object TemporalFormat {

    /**
     * Reference values used to validate a pattern and to render sample values.
     *
     * The day and month differ so a pattern that mixes them up still round-trips visibly, and the time
     * has no seconds so a legitimate minute-precision pattern such as `dd.MM.yyyy HH:mm` is not
     * rejected for losing information it was never asked to carry.
     */
    private val DATE_REFERENCE: LocalDate = LocalDate.of(2024, 1, 31)
    private val DATE_TIME_REFERENCE: LocalDateTime = LocalDateTime.of(2024, 1, 31, 13, 45)

    private val formatters = ConcurrentHashMap<String, DateTimeFormatter>()

    /** Formatter for [pattern], cached because parsing runs once per input record. */
    fun formatterFor(pattern: String): DateTimeFormatter {
        return formatters.computeIfAbsent(pattern) { DateTimeFormatter.ofPattern(it) }
    }

    /** Reads a calendar date, or `null` when [text] does not match [pattern]. */
    fun parseDate(text: String, pattern: String?): LocalDate? {
        return runCatching {
            if (pattern == null) {
                LocalDate.parse(text)
            } else {
                LocalDate.parse(text, formatterFor(pattern = pattern))
            }
        }.getOrNull()
    }

    /** Reads a date with a time of day, or `null` when [text] does not match [pattern]. */
    fun parseDateTime(text: String, pattern: String?): LocalDateTime? {
        return runCatching {
            if (pattern == null) {
                LocalDateTime.parse(text)
            } else {
                LocalDateTime.parse(text, formatterFor(pattern = pattern))
            }
        }.getOrNull()
    }

    /** An example value in [pattern], for placeholders and error messages. */
    fun sample(type: FieldType, pattern: String?): String {
        val reference = referenceFor(type = type)
        if (pattern == null) {
            return reference.toString()
        }
        return runCatching { formatterFor(pattern = pattern).format(reference) }.getOrElse { reference.toString() }
    }

    /**
     * Why [pattern] cannot be used for a [type] field, or `null` when it is usable.
     *
     * Checked by round-trip rather than by compiling the pattern alone: `DateTimeFormatter.ofPattern`
     * accepts `MM-dd` happily, but no date can ever be read back from it.
     */
    fun unusableReason(type: FieldType, pattern: String): String? {
        val formatter = runCatching { formatterFor(pattern = pattern) }
            .getOrElse { return it.message ?: "not a valid date pattern" }

        val reference = referenceFor(type = type)
        val rendered = runCatching { formatter.format(reference) }
            .getOrElse { return "pattern cannot render a ${describe(type = type)} value" }

        val parsedBack = when (type) {
            FieldType.DATE_TIME -> parseDateTime(text = rendered, pattern = pattern)
            else -> parseDate(text = rendered, pattern = pattern)
        }
        if (parsedBack != reference) {
            return "pattern cannot represent a complete ${describe(type = type)} value"
        }
        return null
    }

    private fun referenceFor(type: FieldType): TemporalAccessor {
        return if (type == FieldType.DATE_TIME) DATE_TIME_REFERENCE else DATE_REFERENCE
    }

    private fun describe(type: FieldType): String {
        return if (type == FieldType.DATE_TIME) "date and time" else "date"
    }
}
