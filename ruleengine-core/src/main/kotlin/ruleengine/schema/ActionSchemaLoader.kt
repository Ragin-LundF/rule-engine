package ruleengine.schema

// ...existing imports...
import ruleengine.core.domain.ActionArgType
import ruleengine.core.domain.ActionDefinition
import ruleengine.core.domain.ActionSchema
import ruleengine.core.errors.SchemaLoadException
import ruleengine.jackson.JacksonUtil
import tools.jackson.core.StreamReadFeature
import tools.jackson.dataformat.yaml.YAMLFactory
import java.nio.file.Files
import java.nio.file.Path

data class RawActionDef(val argTypes: List<String> = emptyList())
data class RawActionSchema(val actions: Map<String, RawActionDef> = emptyMap())

object ActionSchemaLoader {
    private val mapper = JacksonUtil.jsonMapper

    @Throws(SchemaLoadException::class)
    fun load(path: Path): ActionSchema {
        if (!Files.exists(path)) {
            throw SchemaLoadException(path = path, details = "file does not exist")
        }

        return runCatching {
            Files.newInputStream(path).use { ins ->
                val bytes = ins.readAllBytes()
                val yf = YAMLFactory.builder().configure(StreamReadFeature.IGNORE_UNDEFINED, true).build()
                yf.createParser(java.io.ByteArrayInputStream(bytes))
                    .use { p -> mapper.readValue(p, RawActionSchema::class.java) }
            }
        }.map { raw ->
            val actions = raw.actions.mapValues { (name, def) ->
                val types = def.argTypes.map { parseArgType(it) }
                ActionDefinition(name = name, argTypes = types)
            }
            ActionSchema(actions = actions)
        }.getOrElse { ex ->
            throw SchemaLoadException(path = path, details = ex.message ?: "unknown error")
        }
    }

    @Throws(SchemaLoadException::class)
    fun loadFromReader(reader: java.io.Reader): ActionSchema {
        val content = reader.use { it.readText() }
        return runCatching {
            val raw: RawActionSchema = run {
                val bytes = content.toByteArray(Charsets.UTF_8)
                val yf = YAMLFactory.builder().configure(StreamReadFeature.IGNORE_UNDEFINED, true).build()
                yf.createParser(java.io.ByteArrayInputStream(bytes))
                    .use { p -> mapper.readValue(p, RawActionSchema::class.java) }
            }
            val actions = raw.actions.mapValues { (name, def) ->
                val types = def.argTypes.map { parseArgType(it) }
                ActionDefinition(name = name, argTypes = types)
            }
            ActionSchema(actions = actions)
        }.getOrElse { ex ->
            throw SchemaLoadException(path = Path.of("actions-schema"), details = ex.message ?: "unknown error")
        }
    }

    fun loadFromString(content: String): ActionSchema {
        return loadFromReader(java.io.StringReader(content))
    }

    private fun parseArgType(s: String): ActionArgType {
        return when (s.lowercase()) {
            "string" -> ActionArgType.STRING
            "integer", "int" -> ActionArgType.INTEGER
            "decimal", "number" -> ActionArgType.DECIMAL
            else -> throw IllegalArgumentException("Unknown action arg type: $s")
        }
    }
}

