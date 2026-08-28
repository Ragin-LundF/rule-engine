package ui.project

import kotlinx.coroutines.CoroutineScope
import ui.editor.rules.RuleEditorState
import ui.manifest.model.ManifestPathKind
import java.nio.file.Files
import java.nio.file.Path
import kotlin.coroutines.EmptyCoroutineContext
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

/**
 * Choosing a manifest path with a dialog instead of typing it.
 *
 * The whole subtlety is the frame of reference: **a path in a manifest is relative to the manifest
 * file.** A dialog hands back an absolute path, so something has to relativize it, and only the
 * workspace knows where the manifest lives — which is also why the button is refused outright before the
 * project has ever been saved. Writing the absolute path instead would produce an entry that resolves on
 * this machine and nowhere else.
 */
class ManifestPathPickerTest {

    private fun workspace(root: Path?, picked: Path?): Pair<ProjectWorkspace, RuleEditorState> {
        val state = RuleEditorState(scope = CoroutineScope(context = EmptyCoroutineContext))
        val workspace = ProjectWorkspace(
            state = state,
            chooseManifestToOpen = { root?.resolve("manifest.yaml") },
            chooseFileOfKind = { picked },
        )
        if (root != null) workspace.openProject()
        return workspace to state
    }

    private fun project(): Path {
        val root = Files.createTempDirectory("manifest-picker")
        Files.createDirectories(root.resolve("rules"))
        Files.writeString(root.resolve("rules/main.rule"), "rule \"r\" {\n  when purpose equals \"x\"\n}")
        Files.writeString(
            root.resolve("manifest.yaml"),
            """
                entries:
                  - id: default
                    rules:
                      - rules/main.rule
            """.trimIndent(),
        )
        return root
    }

    @Test
    fun `an unsaved project says why the dialog cannot be used`() {
        val (workspace, _) = workspace(root = null, picked = null)

        val reason = workspace.chosenPathBlockedReason
        assertNotNull(actual = reason)
        // The reason is the fact, not the rule: what is missing is the manifest file to be relative to.
        assertEquals(expected = true, actual = reason.contains(other = "relative to the manifest file"))
    }

    @Test
    fun `an open project has nothing blocking it`() {
        val (workspace, _) = workspace(root = project(), picked = null)

        assertNull(actual = workspace.chosenPathBlockedReason)
    }

    @Test
    fun `a file inside the project becomes a relative path`() {
        val root = project()
        val (workspace, _) = workspace(root = root, picked = root.resolve("rules/main.rule"))

        assertEquals(
            expected = "rules/main.rule",
            actual = workspace.choosePathForManifest(kind = ManifestPathKind.RULE),
        )
    }

    /** A shared schema outside the project stays portable as long as the two move together. */
    @Test
    fun `a file outside the project becomes a relative path that leaves it`() {
        val root = project()
        val shared = root.parent.resolve("shared-schema.yaml")
        Files.writeString(shared, "fields: {}")
        val (workspace, _) = workspace(root = root, picked = shared)

        val chosen = workspace.choosePathForManifest(kind = ManifestPathKind.SCHEMA)

        assertEquals(expected = "../shared-schema.yaml", actual = chosen)
        assertEquals(expected = true, actual = ProjectPaths.isExternal(relativePath = chosen!!))
    }

    @Test
    fun `a cancelled dialog changes nothing`() {
        val root = project()
        val (workspace, _) = workspace(root = root, picked = null)

        assertNull(actual = workspace.choosePathForManifest(kind = ManifestPathKind.RULE))
    }

    /**
     * The guard behind the disabled button. A caller that ignores [ProjectWorkspace.chosenPathBlockedReason]
     * still cannot write an unresolvable path, because there is nothing to resolve it against.
     */
    @Test
    fun `choosing refuses outright while there is no project`() {
        val (workspace, _) = workspace(root = null, picked = Path.of("/somewhere/else/main.rule"))

        assertNull(actual = workspace.choosePathForManifest(kind = ManifestPathKind.RULE))
    }
}
