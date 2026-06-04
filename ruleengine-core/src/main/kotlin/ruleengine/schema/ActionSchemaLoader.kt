package ruleengine.schema

import ruleengine.core.domain.ActionArgType
import ruleengine.core.domain.ActionDefinition
import ruleengine.core.domain.ActionSchema
import ruleengine.core.errors.SchemaLoadException
import ruleengine.jackson.JacksonUtil
import tools.jackson.dataformat.yaml.YAMLFactory
import tools.jackson.core.StreamReadFeature
import tools.jackson.databind.ObjectMapper
import tools.jackson.databind.DeserializationFeature
import tools.jackson.module.kotlin.KotlinModule
import tools.jackson.module.blackbird.BlackbirdModule
import java.nio.file.Files
import java.nio.file.Path

data class RawActionDef(val argTypes: List<String> = emptyList())
data class RawActionSchema(val actions: Map<String, RawActionDef> = emptyMap())

object ActionSchemaLoader {
    private val mapper = JacksonUtil.jsonMapper

    @Throws(SchemaLoadException::class)
    fun load(path: Path): ActionSchema {
        if (!Files.exists(path)) throw SchemaLoadException(path = path, details = "file does not exist")
        try {
            val raw: RawActionSchema = Files.newInputStream(path).use { ins ->
                val bytes = ins.readAllBytes()
                val yf = YAMLFactory.builder().configure(StreamReadFeature.IGNORE_UNDEFINED, true).build()
                yf.createParser(java.io.ByteArrayInputStream(bytes)).use { p -> mapper.readValue(p, RawActionSchema::class.java) }
            }
            val actions = raw.actions.mapValues { (name, def) ->
                val types = def.argTypes.map { parseArgType(it) }
                ActionDefinition(name = name, argTypes = types)
            }
            return ActionSchema(actions = actions)
        } catch (ex: SchemaLoadException) {
            throw ex
        } catch (ex: Exception) {
            throw SchemaLoadException(path = path, details = ex.message ?: "unknown error")
        }
    }

    @Throws(SchemaLoadException::class)
    fun loadFromReader(reader: java.io.Reader): ActionSchema {
        try {
            val content = reader.use { it.readText() }
            val raw: RawActionSchema = run {
                val bytes = content.toByteArray(Charsets.UTF_8)
                val yf = YAMLFactory.builder().configure(StreamReadFeature.IGNORE_UNDEFINED, true).build()
                yf.createParser(java.io.ByteArrayInputStream(bytes)).use { p -> mapper.readValue(p, RawActionSchema::class.java) }
            }
            val actions = raw.actions.mapValues { (name, def) ->
                val types = def.argTypes.map { parseArgType(it) }
                ActionDefinition(name = name, argTypes = types)
            }
            return ActionSchema(actions = actions)
        } catch (ex: SchemaLoadException) {
            throw ex
        } catch (ex: Exception) {
            throw SchemaLoadException(path = Path.of("actions-schema"), details = ex.message ?: "unknown error")
        }
    }

    fun loadFromString(content: String): ActionSchema = loadFromReader(java.io.StringReader(content))

    private fun parseArgType(s: String): ActionArgType = when (s.lowercase()) {
        "string" -> ActionArgType.STRING
        "integer", "int" -> ActionArgType.INTEGER
        "decimal", "number" -> ActionArgType.DECIMAL
        else -> throw IllegalArgumentException("Unknown action arg type: $s")
    }
}

