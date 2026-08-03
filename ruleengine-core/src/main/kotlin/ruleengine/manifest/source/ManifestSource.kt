package ruleengine.manifest.source

import ruleengine.core.errors.RuleEngineBuildException
import ruleengine.core.io.FileInputSupport
import ruleengine.manifest.FileSystemManifestFileResolver
import ruleengine.manifest.ManifestFileResolver
import ruleengine.manifest.ManifestLoader
import ruleengine.manifest.ProjectManifest
import ruleengine.manifest.classpath.ClasspathManifestFileResolver
import java.nio.file.Path

/**
 * A manifest plus the [ManifestFileResolver] that serves the files it references — the two things
 * every loader needs, resolved together from one location.
 *
 * A location is written as a string so a single entry point covers both supported places: a
 * `classpath:` prefix names a classpath resource, anything else is a filesystem path.
 *
 * ```
 * classpath:rules/manifest.yaml   // packaged inside the application's jar
 * /etc/app/rules/manifest.yaml    // next to the application
 * ```
 *
 * The distinction cannot be inferred and cannot be dropped: a classpath resource inside a jar has no
 * [Path] at all (see [ClasspathManifestFileResolver]), so the two are read by different machinery
 * even though a caller usually only wants to name a location in configuration.
 */
class ManifestSource private constructor(
    /** Labels failures; a real path for a filesystem manifest, the resource name for a packaged one. */
    val location: Path,
    /** Serves the schema, action and rule files the manifest's entries reference. */
    val resolver: ManifestFileResolver,
    private val readText: () -> String,
) {
    /** @throws Exception whatever reading or parsing the manifest fails with; callers attribute it. */
    fun readManifest(): ProjectManifest {
        return ManifestLoader.loadFromString(content = readText())
    }

    companion object {
        /** Marks a location as a classpath resource name rather than a filesystem path. */
        const val CLASSPATH_PREFIX: String = "classpath:"

        /**
         * Reads [location] as a classpath resource when it starts with [CLASSPATH_PREFIX], and as a
         * filesystem path otherwise.
         *
         * @param classLoader loader for a `classpath:` location; ignored for a filesystem one
         * @throws RuleEngineBuildException if a filesystem location has no parent directory
         * @throws java.nio.file.InvalidPathException if a filesystem location is not a valid path
         */
        fun of(
            location: String,
            classLoader: ClassLoader = ClasspathManifestFileResolver.defaultClassLoader(),
        ): ManifestSource {
            if (!location.startsWith(prefix = CLASSPATH_PREFIX)) {
                return ofPath(manifestPath = Path.of(location))
            }

            return ofClasspath(
                manifestResource = location.removePrefix(prefix = CLASSPATH_PREFIX),
                classLoader = classLoader,
            )
        }

        /**
         * A manifest on the local filesystem, whose files resolve against its own directory.
         *
         * @throws RuleEngineBuildException if [manifestPath] has no parent directory
         */
        fun ofPath(manifestPath: Path): ManifestSource {
            val baseDir = manifestPath.toAbsolutePath().normalize().parent
                ?: throw RuleEngineBuildException(
                    manifestPath = manifestPath,
                    entryId = null,
                    details = "manifest path has no parent directory",
                )

            return ManifestSource(
                location = manifestPath,
                resolver = FileSystemManifestFileResolver(baseDir = baseDir),
                readText = { FileInputSupport.readBoundedText(path = manifestPath, kind = "manifest") },
            )
        }

        /**
         * A manifest packaged as a classpath resource, whose files resolve as resource names relative
         * to it.
         *
         * @param manifestResource resource name, for example `rules/manifest.yaml`; a leading `/` is
         *   accepted and ignored
         */
        fun ofClasspath(
            manifestResource: String,
            classLoader: ClassLoader = ClasspathManifestFileResolver.defaultClassLoader(),
        ): ManifestSource {
            val resolver = ClasspathManifestFileResolver(
                manifestResource = manifestResource,
                classLoader = classLoader,
            )

            // RuleEngineBuildException.manifestPath is published as a Path, so the resource name is
            // wrapped rather than the exception widened to a String. Path.of accepts '/' on every
            // platform; a resource name holding a character Windows forbids in a path (':', '*', '?',
            // '|') would throw here instead, which is loud, immediate, and has never described a real
            // rules directory.
            return ManifestSource(
                location = Path.of(resolver.manifestName),
                resolver = resolver,
                readText = { resolver.readManifestText() },
            )
        }
    }
}
