package ruleengine.core.errors

import java.io.Serial

sealed class RuleEngineException(message: String, cause: Throwable? = null) : RuntimeException(message, cause) {
    companion object {
        @Serial
        private const val serialVersionUID: Long = -7115250465939943273L
    }
}
