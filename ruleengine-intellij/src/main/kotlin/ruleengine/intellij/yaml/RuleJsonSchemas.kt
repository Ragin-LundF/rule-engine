package ruleengine.intellij.yaml

import ruleengine.core.domain.OperatorNames
import ruleengine.core.normalizer.NormalizerRegistry
import ruleengine.intellij.yaml.model.RuleYamlKind

/**
 * JSON Schemas for the rule project YAML files, which give completion and validation in the editor.
 *
 * Operators and normalizers come from core, so the lists cannot drift from what the engine accepts.
 * Field types and argument types are private spellings of core's loaders and are restated here.
 */
object RuleJsonSchemas {
    // The spellings FieldSchemaLoader.parseFieldType accepts.
    private val FIELD_TYPES = listOf(
        "text", "string", "integer", "int", "long", "decimal", "bigdecimal", "number", "boolean", "bool",
        "stringset", "string_set", "set", "date", "date_time", "datetime", "timestamp",
        "collection", "list", "array", "object", "map",
    )

    // The spellings ActionSchemaLoader.parseArgType accepts.
    private val ARG_TYPES = listOf(
        "string", "integer", "int", "decimal", "number",
        "variable_string", "variableString", "variable_list", "variableList",
    )

    fun schemaFor(kind: RuleYamlKind): String {
        return when (kind) {
            RuleYamlKind.MANIFEST -> MANIFEST
            RuleYamlKind.FIELD_SCHEMA -> fieldSchema()
            RuleYamlKind.ACTION_SCHEMA -> actionSchema()
        }
    }

    private fun fieldSchema(): String {
        val operators = (OperatorNames.ALL + OperatorNames.CANONICAL.keys + OperatorNames.SYMBOL_NOT_EQUALS).distinct()
        val normalizers = NormalizerRegistry.ids.map { id -> id.value }
        return """
            {
              "${'$'}schema": "http://json-schema.org/draft-07/schema#",
              "title": "Rule engine field schema",
              "type": "object",
              "additionalProperties": false,
              "properties": {
                "schema": { "type": "string", "description": "Name of the schema." },
                "normalizers": {
                  "type": "object",
                  "description": "Named normalizer chains.",
                  "additionalProperties": { "type": "array", "items": { "enum": ${jsonArray(normalizers)} } }
                },
                "fields": { "${'$'}ref": "#/definitions/fields" }
              },
              "required": ["fields"],
              "definitions": {
                "fields": { "type": "object", "additionalProperties": { "${'$'}ref": "#/definitions/field" } },
                "field": {
                  "type": "object",
                  "additionalProperties": false,
                  "required": ["type"],
                  "properties": {
                    "type": { "enum": ${jsonArray(FIELD_TYPES)} },
                    "alias": {
                      "type": "string", "minLength": 1,
                      "description": "Second name a rule may use for the field."
                    },
                    "format": {
                      "type": "string", "minLength": 1,
                      "description": "Date pattern of a date or date_time field, e.g. dd.MM.yyyy."
                    },
                    "normalizers": { "type": "array", "items": { "enum": ${jsonArray(normalizers)} } },
                    "operators": { "type": "array", "items": { "enum": ${jsonArray(operators)} } },
                    "fields": {
                      "${'$'}ref": "#/definitions/fields",
                      "description": "Members of a collection or object field."
                    }
                  }
                }
              }
            }
        """.trimIndent()
    }

    private fun actionSchema(): String {
        return """
            {
              "${'$'}schema": "http://json-schema.org/draft-07/schema#",
              "title": "Rule engine action schema",
              "type": "object",
              "additionalProperties": false,
              "required": ["actions"],
              "properties": {
                "actions": {
                  "type": "object",
                  "additionalProperties": {
                    "type": "object",
                    "additionalProperties": false,
                    "properties": { "argTypes": { "type": "array", "items": { "enum": ${jsonArray(ARG_TYPES)} } } }
                  }
                }
              }
            }
        """.trimIndent()
    }

    private fun jsonArray(values: List<String>): String {
        return values.joinToString(prefix = "[", postfix = "]", separator = ", ") { value -> "\"$value\"" }
    }

    // ManifestLoader rejects unknown keys, so the schema does too.
    private val MANIFEST = """
        {
          "${'$'}schema": "http://json-schema.org/draft-07/schema#",
          "title": "Rule engine manifest",
          "type": "object",
          "additionalProperties": false,
          "properties": {
            "name": { "type": "string" },
            "entries": {
              "type": "array",
              "items": {
                "type": "object",
                "additionalProperties": false,
                "required": ["id"],
                "properties": {
                  "id": { "type": "string" },
                  "schema": { "type": "string", "description": "Field schema, relative to this manifest." },
                  "actions": { "type": "string", "description": "Action schema, relative to this manifest." },
                  "rules": {
                    "type": "array", "items": { "type": "string" },
                    "description": "Rule files in execution order."
                  },
                  "scope": { "type": "string", "description": "Collection evaluated once per member." }
                }
              }
            }
          }
        }
    """.trimIndent()
}
