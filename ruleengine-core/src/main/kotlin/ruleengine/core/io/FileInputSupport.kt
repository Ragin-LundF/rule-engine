package ruleengine.core.io

import ruleengine.core.errors.InputTooLargeException
import java.nio.file.Files
import java.nio.file.Path

object FileInputSupport {
    const val DEFAULT_MAX_BYTES: Long = 25_000_000

    fun readBoundedText(path: Path, kind: String, maxBytes: Long = DEFAULT_MAX_BYTES): String {
        val size = Files.size(path)
        if (size > maxBytes) {
            throw InputTooLargeException(
                path = path,
                kind = kind,
                maxBytes = maxBytes,
                actualBytes = size
            )
        }

        return Files.readString(path)
    }

    fun walkRuleFiles(root: Path): List<Path> {
        return Files.walk(root).use { stream ->
            stream.filter { path ->
                Files.isRegularFile(path) && path.toString().endsWith(".rule")
            }.sorted().toList()
            // ponytail: alphabetical path order; manifest mode is the ordered-authoritative path
        }
    }
}


