package ruleengine.manifest

import ruleengine.compiler.support.Suggestions
import ruleengine.core.io.FileInputSupport
import ruleengine.jackson.JacksonUtil
import java.nio.file.Path

object ManifestLoader {

    /** The keys a manifest may hold. `ManifestLoaderTest` fails when the model gains one not listed here. */
    internal val TOP_LEVEL_KEYS: List<String> = listOf("name", "entries")
    internal val ENTRY_KEYS: List<String> = listOf("id", "schema", "actions", "rules", "scope", "missingCollections")

    fun loadFromString(content: String): ProjectManifest {
        val manifest = parse(content = content, type = ProjectManifest::class.java)
        rejectUnknownKeys(raw = parse(content = content, type = Map::class.java))
        return validated(manifest = manifest)
    }

    private fun <T> parse(content: String, type: Class<T>): T {
        // YAML first, JSON as a fallback: a manifest is documented as YAML, but a hand-written JSON
        // one parses too and there is no reason to reject it.
        return runCatching {
            JacksonUtil.readYaml(content = content, type = type, ignoreUndefined = false)
        }.getOrElse {
            JacksonUtil.jsonMapper.readValue(content, type)
        }
    }

    /**
     * Rejects a key the manifest model does not know, at the top level and in every entry.
     *
     * The shared mapper ignores unknown properties, which is right for input data but not here: a
     * misspelt `scop:` or `missingCollection:` would load and silently do nothing.
     */
    private fun rejectUnknownKeys(raw: Map<*, *>?) {
        raw ?: return
        unknownKey(map = raw, known = TOP_LEVEL_KEYS)?.let { key ->
            throw IllegalArgumentException(
                "manifest has an unknown key '$key'${hint(key = key, known = TOP_LEVEL_KEYS)}"
            )
        }
        (raw["entries"] as? List<*>).orEmpty().filterIsInstance<Map<*, *>>().forEachIndexed { index, entry ->
            val key = unknownKey(map = entry, known = ENTRY_KEYS) ?: return@forEachIndexed
            val name = entry["id"]?.let { id -> "'$id'" } ?: "#${index + 1}"
            throw IllegalArgumentException(
                "manifest entry $name has an unknown key '$key'${hint(key = key, known = ENTRY_KEYS)}"
            )
        }
    }

    private fun unknownKey(map: Map<*, *>, known: List<String>): String? {
        return map.keys.map { key -> key.toString() }.firstOrNull { key -> key !in known }
    }

    private fun hint(key: String, known: List<String>): String {
        val closest = Suggestions.suggestClosest(input = key, candidates = known)
        val suggestion = closest?.let { name -> " — did you mean '$name'?" }.orEmpty()
        return "$suggestion; known keys: ${known.joinToString()}"
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

