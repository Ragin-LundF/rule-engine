package ruleengine.schema

import ruleengine.core.domain.dto.action.ActionArgType
import ruleengine.core.errors.SchemaLoadException
import ruleengine.core.io.FileInputSupport
import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class ActionSchemaLoaderTest {
    @Test
    fun `loads sample action schema`() {
        val path = Path.of("src/test/resources/actions.yaml")
        val actions = ActionSchemaLoader.load(path = path)

        assertEquals(expected = 4, actual = actions.actions.size)
        assertEquals(expected = ActionArgType.STRING, actual = actions.actions["label"]?.argTypes?.first())
    }

    @Test
    fun `oversized action schema file throws SchemaLoadException`() {
        val path = Files.createTempFile("oversized-actions", ".yaml")
        Files.writeString(path, oversizedContent())

        val exception = assertFailsWith<SchemaLoadException> {
            ActionSchemaLoader.load(path = path)
        }

        assertTrue(actual = exception.details.contains(other = "exceeds limit"))
    }

    private fun oversizedContent(): String {
        return "a".repeat(n = FileInputSupport.DEFAULT_MAX_BYTES.toInt() + 1)
    }
}

