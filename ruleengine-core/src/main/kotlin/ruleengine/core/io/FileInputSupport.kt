package ruleengine.core.io

import ruleengine.core.errors.InputTooLargeException
import java.io.InputStream
import java.nio.ByteBuffer
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

    /**
     * Reads [stream] fully as UTF-8 text, refusing anything larger than [maxBytes].
     *
     * The stream variant exists for content that has no [Path] — a classpath resource inside a jar
     * being the case that matters. [name] only labels the failure; it is never opened.
     */
    fun readBoundedText(
        stream: InputStream,
        kind: String,
        name: String,
        maxBytes: Long = DEFAULT_MAX_BYTES,
    ): String {
        val limit = (maxBytes + 1).coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
        val bytes = stream.use { it.readNBytes(limit) }
        if (bytes.size.toLong() > maxBytes) {
            // ponytail: a stream has no size up front, so actualBytes is a lower bound (maxBytes + 1)
            // rather than the true size; upgrade path is to keep draining and count.
            throw InputTooLargeException(
                path = Path.of(name),
                kind = kind,
                maxBytes = maxBytes,
                actualBytes = bytes.size.toLong()
            )
        }

        // Decoded strictly, matching Files.readString: the String(bytes, UTF_8) constructor
        // substitutes U+FFFD instead, which would turn malformed input into a mangled parse error.
        return Charsets.UTF_8.newDecoder().decode(ByteBuffer.wrap(bytes)).toString()
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

