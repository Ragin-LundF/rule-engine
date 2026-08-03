package ruleengine.manifest

import java.nio.file.Path

/**
 * A file a manifest entry references, in the form the loader that reads it needs.
 *
 * [OnDisk] exists so a filesystem manifest keeps using the `Path`-based loaders unchanged, while
 * [InMemory] carries content that has no path at all — a classpath resource inside a jar being the
 * case that matters.
 */
sealed interface ManifestFile {
    /**
     * A file that was found and may be read.
     *
     * Split out so a builder can reject [Unavailable] once and then branch over the two readable
     * forms exhaustively, without an unreachable case in every `when`.
     */
    sealed interface Available : ManifestFile

    data class OnDisk(val path: Path) : Available

    /** [nameHint] is the file name a loader falls back to when the content declares none. */
    data class InMemory(val content: String, val nameHint: String) : Available

    /** Not found, or refused because it escapes the manifest's base location. */
    data class Unavailable(val message: String) : ManifestFile
}
