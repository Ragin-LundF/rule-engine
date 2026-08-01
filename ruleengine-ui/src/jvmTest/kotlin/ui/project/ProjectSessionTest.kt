package ui.project

import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals

class ProjectSessionTest {

    @Test
    fun `the flat views read the active entry`() {
        val session = session(activeEntryId = "second")

        assertEquals(expected = "second", actual = session.entryId)
        assertEquals(expected = "schemas/second.yaml", actual = session.schemaLink)
        assertEquals(expected = listOf("rules/second.rule"), actual = session.ruleFiles)
    }

    @Test
    fun `withActive replaces only the active entry`() {
        val updated = session(activeEntryId = "second").withActive { entry -> entry.copy(schemaLink = null) }

        assertEquals(expected = "schemas/first.yaml", actual = updated.entry(id = "first")?.schemaLink)
        assertEquals(expected = null, actual = updated.entry(id = "second")?.schemaLink)
    }

    /** Renaming the entry being edited must not leave the buffers pointing at an id that is gone. */
    @Test
    fun `renaming the active entry follows the rename`() {
        val updated = session(activeEntryId = "second").withActive { entry -> entry.copy(id = "renamed") }

        assertEquals(expected = "renamed", actual = updated.activeEntryId)
        assertEquals(expected = listOf("first", "renamed"), actual = updated.entries.map { it.id })
    }

    /** A stale selection beats crashing the workbench, so the first entry stands in. */
    @Test
    fun `an unknown active id falls back to the first entry`() {
        assertEquals(expected = "first", actual = session(activeEntryId = "vanished").entryId)
    }

    private fun session(activeEntryId: String): ProjectSession {
        return ProjectSession(
            root = Path.of("/projects/demo"),
            manifestFileName = "manifest.yaml",
            entries = listOf(
                ProjectEntry(id = "first", schemaLink = "schemas/first.yaml", ruleFiles = listOf("rules/first.rule")),
                ProjectEntry(
                    id = "second",
                    schemaLink = "schemas/second.yaml",
                    ruleFiles = listOf("rules/second.rule"),
                ),
            ),
            activeEntryId = activeEntryId,
        )
    }
}
