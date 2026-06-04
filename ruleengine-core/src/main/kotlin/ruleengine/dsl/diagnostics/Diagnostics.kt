package ruleengine.dsl.diagnostics

import java.io.Serial

data class ParseException(
    val line: Int,
    val column: Int,
    val messageText: String
) : RuntimeException("Parse error at $line:$column - $messageText") {
    companion object {
        @Serial
        private const val serialVersionUID: Long = 8792492308014731484L
    }
}

