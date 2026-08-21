package ui.project.manifest

import ruleengine.manifest.ManifestEntry
import ruleengine.manifest.ProjectManifest
import ui.project.model.ProjectEntry
import ui.project.model.ProjectSession
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * What the top bar's entry picker shows.
 *
 * The picker used to read the session and nothing else, so a sample — which has no session — either
 * emptied it or, with a project still open, left it naming that project's entry. These pin both
 * sources and which one wins.
 */
class ManifestEntrySelectionTest {

    private val manifest = ProjectManifest(
        name = "sample",
        entries = listOf(
            ManifestEntry(id = "from-manifest"),
            ManifestEntry(id = "second-from-manifest"),
        ),
    )

    private fun session(vararg ids: String, active: String) = ProjectSession(
        root = Path.of("/tmp/project"),
        manifestFileName = "manifest.yaml",
        entries = ids.map { id -> ProjectEntry(id = id) },
        activeEntryId = active,
    )

    @Test
    fun `a session supplies the entries and is editable`() {
        val selection = manifestEntrySelection(
            session = session("first", "second", active = "second"),
            parsedManifest = null,
            selectedEntryId = null,
        )

        assertEquals(expected = listOf("first", "second"), actual = selection?.entryIds)
        assertEquals(expected = "second", actual = selection?.activeEntryId)
        assertTrue(actual = selection?.editable == true, message = "switching and adding need a session")
    }

    /**
     * The session wins where both exist: a freshly added entry lives on the session until the manifest
     * buffers are regenerated from it, so preferring the parsed manifest would briefly lose it.
     */
    @Test
    fun `the session wins over the parsed manifest`() {
        val selection = manifestEntrySelection(
            session = session("from-session", active = "from-session"),
            parsedManifest = manifest,
            selectedEntryId = "from-manifest",
        )

        assertEquals(expected = listOf("from-session"), actual = selection?.entryIds)
        assertEquals(expected = "from-session", actual = selection?.activeEntryId)
    }

    /** The sample case: a real manifest on screen with no project behind it. */
    @Test
    fun `without a session the parsed manifest supplies the entries, read-only`() {
        val selection = manifestEntrySelection(
            session = null,
            parsedManifest = manifest,
            selectedEntryId = "second-from-manifest",
        )

        assertEquals(
            expected = listOf("from-manifest", "second-from-manifest"),
            actual = selection?.entryIds,
        )
        assertEquals(expected = "second-from-manifest", actual = selection?.activeEntryId)
        assertFalse(
            actual = selection?.editable == true,
            message = "selectEntry and addEntry both return early with no session",
        )
    }

    @Test
    fun `a selection naming no entry falls back to the first`() {
        val selection = manifestEntrySelection(
            session = null,
            parsedManifest = manifest,
            selectedEntryId = "renamed-away",
        )

        assertEquals(expected = "from-manifest", actual = selection?.activeEntryId)
    }

    @Test
    fun `no session and no entries means no picker`() {
        assertNull(
            actual = manifestEntrySelection(session = null, parsedManifest = null, selectedEntryId = null),
        )
        assertNull(
            actual = manifestEntrySelection(
                session = null,
                parsedManifest = ProjectManifest(name = "empty"),
                selectedEntryId = null,
            ),
        )
    }
}
