package ruleengine.manifest

import ruleengine.jackson.JacksonUtil
import tools.jackson.core.ObjectReadContext
import tools.jackson.dataformat.yaml.YAMLFactory
import java.nio.file.Files
import java.nio.file.Path

object ManifestLoader {
    fun loadFromString(content: String): ProjectManifest {
        val mapper = JacksonUtil.jsonMapper
        val yf = YAMLFactory()
        // try to parse as YAML into ProjectManifest
        return runCatching {
            // use YAML parser with mapper
            val parser = yf.createParser(ObjectReadContext.empty(), content.reader())
            mapper.readValue(parser, ProjectManifest::class.java)
        }.fold(
            onSuccess = { it },
            onFailure = {
                // try JSON fallback
                mapper.readValue(content, ProjectManifest::class.java)
            }
        )
    }

    fun load(path: Path): ProjectManifest {
        val content = Files.readString(path)
        return loadFromString(content = content)
    }
}


