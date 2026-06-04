package ruleengine.core.errors

import java.io.Serial

data class CompilationException(val ruleId: String?, val details: String) :
    RuleEngineException("Compilation failed for rule ${ruleId ?: "<unknown>"}: $details") {
    companion object {
        @Serial
        private const val serialVersionUID: Long = 2725699143590444473L
    }
}
