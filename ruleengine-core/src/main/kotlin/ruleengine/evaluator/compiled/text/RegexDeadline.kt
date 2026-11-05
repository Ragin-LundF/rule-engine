package ruleengine.evaluator.compiled.text

/**
 * Runs a rule-authored regex with a time budget. Java's regex engine backtracks, so a pattern such as
 * `^(\w+\s?)*\1$` can take exponential time on a short input; the budget keeps one value from blocking
 * the evaluating thread. The input is wrapped so every character read checks the deadline.
 */
internal object RegexDeadline {
    const val TIMEOUT_MILLIS = 200L

    /**
     * Returns what [match] returns, or `null` when the pattern ran out of time or overflowed the stack
     * (a deeply recursive pattern such as `(a|b)*` on a long input). Either way the value was not tested.
     */
    fun <T> runOrNull(input: String, match: (CharSequence) -> T): T? {
        val deadline = System.nanoTime() + TIMEOUT_MILLIS * NANOS_PER_MILLI
        return try {
            match(DeadlineCharSequence(text = input, deadlineNanos = deadline))
        } catch (_: RegexTimeoutException) {
            null
        } catch (_: StackOverflowError) {
            null
        }
    }

    private const val NANOS_PER_MILLI = 1_000_000L

    private class RegexTimeoutException : RuntimeException("regex time budget exceeded") {
        override fun fillInStackTrace(): Throwable {
            return this
        }
    }

    private class DeadlineCharSequence(
        private val text: CharSequence,
        private val deadlineNanos: Long
    ) : CharSequence {
        override val length: Int
            get() = text.length

        override fun get(index: Int): Char {
            if (System.nanoTime() > deadlineNanos) {
                throw RegexTimeoutException()
            }
            return text[index]
        }

        override fun subSequence(startIndex: Int, endIndex: Int): CharSequence {
            return DeadlineCharSequence(
                text = text.subSequence(startIndex = startIndex, endIndex = endIndex),
                deadlineNanos = deadlineNanos
            )
        }

        override fun toString(): String {
            return text.toString()
        }
    }
}
