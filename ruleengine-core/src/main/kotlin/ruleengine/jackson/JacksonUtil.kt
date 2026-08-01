package ruleengine.jackson

import com.fasterxml.jackson.annotation.JsonInclude
import tools.jackson.databind.DeserializationFeature
import tools.jackson.databind.ObjectMapper
import tools.jackson.databind.cfg.DateTimeFeature
import tools.jackson.databind.json.JsonMapper
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
