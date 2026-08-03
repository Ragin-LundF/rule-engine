package ruleengine.manifest.classpath

import ruleengine.core.io.FileInputSupport
import ruleengine.manifest.ManifestFile
import ruleengine.manifest.ManifestFileResolver

/**
 * Resolves a manifest's files as classpath resources, relative to the manifest resource itself.
 *
 * Reads through [ClassLoader.getResourceAsStream] and nothing else — deliberately. That single call
 * behaves the same for exploded classes, a plain jar, a Spring Boot executable jar and a jar nested
 * inside one, because the class loader owns the lookup. Anything that turns a resource into a `URL`
 * or a [java.nio.file.Path] does not: `BOOT-INF/classes` resolves to a `jar:nested:…` or
 * `jar:file:…!/…!/…` URL, and the JDK has no `FileSystemProvider` for either form.
 *
 * Resource names are `/`-separated, never start with `/`, and are always resolved against the
 * manifest's own resource directory. Nothing is scanned: a manifest enumerates every file it uses.
 */
class ClasspathManifestFileResolver(
    manifestResource: String,
    private val classLoader: ClassLoader = defaultClassLoader(),
) : ManifestFileResolver {

    /** The manifest's own resource name, with the leading slash a caller may have copied removed. */
    val manifestName: String = manifestResource.removePrefix(prefix = "/")

    private val baseSegments: List<String> = manifestName.split('/').dropLast(n = 1)

    /** @throws IllegalStateException if the manifest resource is not on the classpath */
    fun readManifestText(): String {
        val stream = classLoader.getResourceAsStream(manifestName)
            ?: throw IllegalStateException("classpath resource '$manifestName' not found")

        return FileInputSupport.readBoundedText(stream = stream, kind = "manifest", name = manifestName)
    }

    override fun resolve(relativePath: String, label: String): ManifestFile {
        val segments = resolveSegments(relativePath = relativePath)
            ?: return ManifestFile.Unavailable(
                message = "Manifest $label path '$relativePath' escapes base directory " +
                    "'${baseSegments.joinToString(separator = "/")}'",
            )

        val resourceName = segments.joinToString(separator = "/")
        val stream = classLoader.getResourceAsStream(resourceName)
            ?: return ManifestFile.Unavailable(
                message = "$label file '$relativePath' not found (resolved to $resourceName)",
            )

        return ManifestFile.InMemory(
            content = FileInputSupport.readBoundedText(stream = stream, kind = label, name = resourceName),
            nameHint = segments.last(),
        )
    }

    /**
     * Resolves [relativePath] against the base segments, or returns `null` when it would leave them.
     *
     * Hand-rolled rather than delegating to [java.nio.file.Path]: a resource name is not a path.
     * `Path.of` rejects names Windows forbids (`:`, `*`, `?`, `|`), treats an empty base specially and
     * yields OS separators that would have to be converted back.
     *
     * The check is not theoretical. `getResourceAsStream` returns `null` for `rules/../x` inside a
     * jar, but on an exploded classpath the same name resolves and would read outside the manifest's
     * own directory — which the filesystem resolver rejects.
     */
    private fun resolveSegments(relativePath: String): List<String>? {
        if (relativePath.startsWith(prefix = "/")) {
            return null
        }

        val segments = baseSegments.toMutableList()
        for (segment in relativePath.split('/')) {
            when (segment) {
                "", "." -> Unit
                ".." -> if (segments.size <= baseSegments.size) return null else segments.removeLast()
                else -> segments.add(element = segment)
            }
        }

        return segments.takeIf { it.isNotEmpty() }
    }

    companion object {
        /**
         * The loader a classpath lookup should use by default.
         *
         * The thread context loader comes first because it is the only one that sees the application's
         * own resources under `spring-boot-devtools`: the app is reloaded by a `RestartClassLoader`
         * while this library stays on its parent, which cannot look back down.
         */
        fun defaultClassLoader(): ClassLoader {
            return Thread.currentThread().contextClassLoader
                ?: ClasspathManifestFileResolver::class.java.classLoader
                ?: ClassLoader.getSystemClassLoader()
        }
    }
}
