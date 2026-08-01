package ui.project

import ui.project.manifest.ManifestEntryRemoval
import ui.project.model.ProjectEntry
import ui.project.model.ProjectSession
import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ManifestEntryRemovalTest {

    @Test
    fun `files the entry owns alone are deletable`() {
        val root = Path.of("/projects/demo")
        val session = session(
            root = root,
            ProjectEntry(
                id = "risk",
                schemaLink = "schemas/risk.yaml",
                actionsLink = "schemas/risk-actions.yaml",
                ruleFiles = listOf("rules/risk.rule"),
            ),
            ProjectEntry(id = "other"),
        )

        val deletable = ManifestEntryRemoval.deletableFiles(session = session, entryId = "risk")

        assertEquals(
            expected = listOf("schemas/risk.yaml", "schemas/risk-actions.yaml", "rules/risk.rule"),
            actual = deletable.map { it.relativePath },
        )
        assertTrue(actual = ManifestEntryRemoval.keptFiles(session = session, entryId = "risk").isEmpty())
    }

    /** A schema two entries point at is not the removed entry's to delete. */
    @Test
    fun `a file another entry still references is kept`() {
        val session = session(
            root = Path.of("/projects/demo"),
            ProjectEntry(id = "risk", schemaLink = "schemas/shared.yaml", ruleFiles = listOf("rules/risk.rule")),
            ProjectEntry(id = "other", schemaLink = "schemas/shared.yaml"),
        )

        assertEquals(
            expected = listOf("rules/risk.rule"),
            actual = ManifestEntryRemoval.deletableFiles(session = session, entryId = "risk").map { it.relativePath },
        )
        assertEquals(
            expected = listOf("schemas/shared.yaml"),
            actual = ManifestEntryRemoval.keptFiles(session = session, entryId = "risk"),
        )
    }

    /** Sharing is the whole reason a `../` link exists — other projects read that file. */
    @Test
    fun `a file outside the project is never deleted`() {
        val session = session(
            root = Path.of("/projects/demo"),
            ProjectEntry(id = "risk", schemaLink = "../shared/common.yaml"),
            ProjectEntry(id = "other"),
        )

        assertTrue(actual = ManifestEntryRemoval.deletableFiles(session = session, entryId = "risk").isEmpty())
        assertEquals(
            expected = listOf("../shared/common.yaml"),
            actual = ManifestEntryRemoval.keptFiles(session = session, entryId = "risk"),
        )
    }

    @Test
    fun `deleting removes the files and reports nothing`() {
        val root = Files.createTempDirectory("removal")
        Files.createDirectories(root.resolve("rules"))
        val rule = Files.writeString(root.resolve("rules/gone.rule"), "rule \"gone\" { when then }")
        val session = session(
            root = root,
            ProjectEntry(id = "risk", ruleFiles = listOf("rules/gone.rule")),
            ProjectEntry(id = "other"),
        )

        val failures = ManifestEntryRemoval.delete(
            files = ManifestEntryRemoval.deletableFiles(session = session, entryId = "risk"),
        )

        assertTrue(actual = failures.isEmpty())
        assertTrue(actual = Files.notExists(rule))
    }

    private fun session(root: Path, vararg entries: ProjectEntry): ProjectSession {
        return ProjectSession(
            root = root,
            manifestFileName = "manifest.yaml",
            entries = entries.toList(),
            activeEntryId = entries.first().id,
        )
    }
}
