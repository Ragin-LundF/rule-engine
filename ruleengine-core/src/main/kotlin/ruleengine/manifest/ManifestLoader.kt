package ruleengine.manifest

import ruleengine.core.io.FileInputSupport
import ruleengine.jackson.JacksonUtil
import java.nio.file.Path

object ManifestLoader {
    fun loadFromString(content: String): ProjectManifest {
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

    fun load(path: Path): ProjectManifest {
        val content = FileInputSupport.readBoundedText(path = path, kind = "manifest")
        return loadFromString(content = content)
    }
}

