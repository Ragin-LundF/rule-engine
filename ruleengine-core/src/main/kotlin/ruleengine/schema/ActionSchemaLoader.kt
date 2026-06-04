package ruleengine.schema

import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import ruleengine.core.domain.ActionArgType
import ruleengine.core.domain.ActionDefinition
import ruleengine.core.domain.ActionSchema
import ruleengine.core.errors.SchemaLoadException
import java.nio.file.Files
import java.nio.file.Path

data class RawActionDef(val argTypes: List<String> = emptyList())
data class RawActionSchema(val actions: Map<String, RawActionDef> = emptyMap())

object ActionSchemaLoader {
    private val mapper = ObjectMapper(YAMLFactory())
        .registerKotlinModule()
        .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)

    @Throws(SchemaLoadException::class)
    fun load(path: Path): ActionSchema {
        if (!Files.exists(path)) throw SchemaLoadException(path = path, details = "file does not exist")
        try {
            val raw: RawActionSchema = Files.newBufferedReader(path).use { r -> mapper.readValue(r) }
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

    private fun parseArgType(s: String): ActionArgType = when (s.lowercase()) {
        "string" -> ActionArgType.STRING
        "integer", "int" -> ActionArgType.INTEGER
        "decimal", "number" -> ActionArgType.DECIMAL
        else -> throw IllegalArgumentException("Unknown action arg type: $s")
    }
}

