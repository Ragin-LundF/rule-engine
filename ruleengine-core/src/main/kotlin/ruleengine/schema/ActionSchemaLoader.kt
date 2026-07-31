package ruleengine.schema

import ruleengine.core.domain.dto.ActionArgType
import ruleengine.core.domain.dto.ActionDefinition
import ruleengine.core.domain.dto.ActionSchema
import ruleengine.core.errors.SchemaLoadException
import ruleengine.core.io.FileInputSupport
import ruleengine.jackson.JacksonUtil
import ruleengine.schema.dto.RawActionSchema
import tools.jackson.core.ObjectReadContext
import tools.jackson.core.StreamReadFeature
import tools.jackson.dataformat.yaml.YAMLFactory
import java.io.StringReader
import java.nio.file.Files
import java.nio.file.Path

object ActionSchemaLoader {
    private val mapper = JacksonUtil.jsonMapper

    @Throws(SchemaLoadException::class)
    fun load(path: Path): ActionSchema {
        if (!Files.exists(path)) {
            throw SchemaLoadException(path = path, details = "file does not exist")
        }

        return runCatching {
            val content = FileInputSupport.readBoundedText(path = path, kind = "action schema")
            parseSchema(content = content)
        }.map { raw ->
            val actions = raw.actions.mapValues { (name, def) ->
                val types = def.argTypes.map { parseArgType(s = it) }
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
            val raw = parseSchema(content = content)
            val actions = raw.actions.mapValues { (name, def) ->
                val types = def.argTypes.map { parseArgType(s = it) }
                ActionDefinition(name = name, argTypes = types)
            }
            ActionSchema(actions = actions)
        }.getOrElse { ex ->
            throw SchemaLoadException(path = Path.of("actions-schema"), details = ex.message ?: "unknown error")
        }
    }

    fun loadFromString(content: String): ActionSchema {
        return loadFromReader(StringReader(content))
    }

    private fun parseSchema(content: String): RawActionSchema {
        val yf = YAMLFactory.builder().configure(StreamReadFeature.IGNORE_UNDEFINED, true).build()
        val bytes = content.toByteArray(Charsets.UTF_8)
        return yf.createParser(ObjectReadContext.empty(), bytes).use { p ->
            mapper.readValue(p, RawActionSchema::class.java)
        }
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

