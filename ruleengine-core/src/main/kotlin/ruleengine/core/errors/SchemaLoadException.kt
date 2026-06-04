package ruleengine.core.errors

import java.io.Serial
import java.nio.file.Path

data class SchemaLoadException(
    val path: Path,
    val details: String
) : RuleEngineException("Failed to load schema from $path: $details") {
    companion object {
        @Serial
        private const val serialVersionUID: Long = 8371538950540441360L
    }
}
