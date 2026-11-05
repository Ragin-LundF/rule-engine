package ruleengine.evaluator.context

import ruleengine.evaluator.context.NumericInput.MAX_SCALE
import java.math.BigDecimal
import java.math.BigInteger
import kotlin.math.abs

/**
 * Reads a number from record input — the one conversion every numeric read of the engine shares.
 *
 * `null` means the value is not readable as a number, which a field reads as undecided and an
 * aggregate skips: text that is no number, a structure, a non-finite double (JSON `1e400` arrives as
 * infinity), and a number whose exponent lies outside ±[MAX_SCALE]. `1e999999999` parses in no time,
 * but the first `+ 1` would expand it to a billion digits — an input-driven denial of service.
 */
object NumericInput {

    private const val MAX_SCALE = 1000

    fun decimalOrNull(raw: Any?): BigDecimal? {
        val value = when (raw) {
            is BigDecimal -> raw
            // valueOf skips the string round trip; this runs once per numeric leaf of a collection.
            is Int, is Long, is Short, is Byte -> return BigDecimal.valueOf((raw as Number).toLong())
            is BigInteger -> BigDecimal(raw)
            is Number, is String -> raw.toString().toBigDecimalOrNull()
            else -> null
        } ?: return null
        return value.takeIf { decimal -> abs(decimal.scale()) <= MAX_SCALE }
    }

    /** A whole number within the `Long` range; a fraction or anything larger is not readable. */
    fun integerOrNull(raw: Any?): Long? {
        val value = decimalOrNull(raw = raw) ?: return null
        return runCatching { value.longValueExact() }.getOrNull()
    }
}
