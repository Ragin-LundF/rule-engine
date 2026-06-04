package ruleengine.dsl.diagnostics

import kotlin.RuntimeException

data class ParseException(val line: Int, val column: Int, val messageText: String) : RuntimeException("Parse error at $line:$column - $messageText")


