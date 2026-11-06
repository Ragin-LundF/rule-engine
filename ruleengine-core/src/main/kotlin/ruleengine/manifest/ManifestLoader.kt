package ruleengine.manifest

import ruleengine.core.io.FileInputSupport
import ruleengine.jackson.JacksonUtil
import java.nio.file.Path

object ManifestLoader {
    fun loadFromString(content: String): ProjectManifest {
        return validated(manifest = parse(content = content))
    }

    private fun parse(content: String): ProjectManifest {
        // YAML first, JSON as a fallback: a manifest is documented as YAML, but a hand-written JSON
        // one parses too and there is no reason to reject it.
        return runCatching {
            JacksonUtil.readYaml(
                content = content,
                type = ProjectManifest::class.java,
                ignoreUndefined = false,
            )
        }.getOrElse {
            JacksonUtil.jsonMapper.readValue(content, ProjectManifest::class.java)
        }
    }

    /** Rejects a `missingCollections` value other than `empty`, which would otherwise silently mean strict. */
    private fun validated(manifest: ProjectManifest): ProjectManifest {
        val invalid = manifest.entries.firstOrNull { entry ->
            entry.missingCollections != null &&
                entry.missingCollections != ManifestEntry.MISSING_COLLECTIONS_EMPTY
        }
        require(invalid == null) {
            "manifest entry '${invalid?.id}' declares 'missingCollections: ${invalid?.missingCollections}'; " +
                "the only value is '${ManifestEntry.MISSING_COLLECTIONS_EMPTY}'"
        }
        return manifest
    }

    fun load(path: Path): ProjectManifest {
        val content = FileInputSupport.readBoundedText(path = path, kind = "manifest")
        return loadFromString(content = content)
    }
}

