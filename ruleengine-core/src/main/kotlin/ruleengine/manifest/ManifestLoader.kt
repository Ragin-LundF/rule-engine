package ruleengine.manifest

import ruleengine.jackson.JacksonUtil
import java.nio.file.Path

object ManifestLoader {
    fun loadFromString(content: String): ProjectManifest {
        val mapper = JacksonUtil.jsonMapper
        val yf = tools.jackson.dataformat.yaml.YAMLFactory()
        // try to parse as YAML into ProjectManifest
        return try {
            // use YAML parser with mapper
            val parser = yf.createParser(content.reader())
            mapper.readValue(parser, ProjectManifest::class.java)
        } catch (ex: Exception) {
            // try JSON fallback
            mapper.readValue(content, ProjectManifest::class.java)
        }
    }

    fun load(path: Path): ProjectManifest {
        val content = java.nio.file.Files.readString(path)
        return loadFromString(content)
    }
}


