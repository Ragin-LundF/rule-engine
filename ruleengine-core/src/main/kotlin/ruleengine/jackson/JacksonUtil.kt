package ruleengine.jackson

import com.fasterxml.jackson.annotation.JsonInclude
import tools.jackson.core.ObjectReadContext
import tools.jackson.core.StreamReadFeature
import tools.jackson.databind.DeserializationFeature
import tools.jackson.databind.ObjectMapper
import tools.jackson.databind.cfg.DateTimeFeature
import tools.jackson.databind.json.JsonMapper
import tools.jackson.dataformat.yaml.YAMLFactory
import tools.jackson.module.blackbird.BlackbirdModule
import tools.jackson.module.kotlin.KotlinModule

object JacksonUtil {
    /**
     * A pre-configured instance of `ObjectMapper` used for JSON serialization and deserialization.
     *
     * The `mapper` object mapper is configured with the following settings:
     * - Includes the `JavaTimeModule` to handle Java 8 Date and Time API.
     * - Disables specific features to manage date formats and unknown properties.
     * - Configured to only include non-null values in serialization.
     * - Registers the Kotlin module to support Kotlin-specific features.
     */
    @JvmStatic
    val jsonMapper: ObjectMapper = createObjectMapper()

    /**
     * Reads YAML [content] as [type], using the JSON-configured [jsonMapper] behind a YAML parser.
     *
     * There is no separate YAML `ObjectMapper`: the module registrations and inclusion rules must be
     * the same for both formats, so the one configured mapper is fed a `YAMLFactory` parser instead.
     *
     * [ignoreUndefined] is on for schema files and off for the manifest, which is how those two have
     * always been read. It is a parameter rather than a constant only to keep that difference visible.
     */
    @JvmStatic
    fun <T> readYaml(content: String, type: Class<T>, ignoreUndefined: Boolean = true): T {
        val factory = YAMLFactory.builder()
            .configure(StreamReadFeature.IGNORE_UNDEFINED, ignoreUndefined)
            .build()

        return factory.createParser(ObjectReadContext.empty(), content.toByteArray(Charsets.UTF_8))
            .use { parser -> jsonMapper.readValue(parser, type) }
    }

    @JvmStatic
    fun createObjectMapper(): ObjectMapper {
        val mapper: ObjectMapper = jsonBuilder().build()

        return mapper
    }

    @JvmStatic
    fun jsonBuilder(): JsonMapper.Builder {
        return jsonBuilder(builder = JsonMapper.builder())
    }

    @JvmStatic
    fun jsonBuilder(builder: JsonMapper.Builder): JsonMapper.Builder {
        return builder.addModule(BlackbirdModule())
            .addModule(KotlinModule.Builder().build())
            .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
            .disable(DateTimeFeature.WRITE_DATES_AS_TIMESTAMPS)
            .disable(DateTimeFeature.WRITE_DATE_TIMESTAMPS_AS_NANOSECONDS)
            .changeDefaultPropertyInclusion { incl ->
                incl.withValueInclusion(JsonInclude.Include.NON_EMPTY)
                incl.withValueInclusion(JsonInclude.Include.NON_NULL)
            }
    }
}
