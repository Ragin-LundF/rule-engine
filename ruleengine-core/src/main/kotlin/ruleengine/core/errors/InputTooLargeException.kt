package ruleengine.core.errors

import java.io.Serial
import java.nio.file.Path

data class InputTooLargeException(
    val path: Path,
    val kind: String,
    val maxBytes: Long,
    val actualBytes: Long
) : RuleEngineException("Refusing to load $kind from $path: size $actualBytes bytes exceeds limit of $maxBytes bytes") {
    companion object {
        @Serial
        private const val serialVersionUID: Long = 5478167429977004521L
    }
}

