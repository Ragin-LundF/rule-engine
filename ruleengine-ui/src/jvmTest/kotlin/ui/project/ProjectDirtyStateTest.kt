package ui.project

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ProjectDirtyStateTest {

    @Test
    fun `a buffer matching its baseline is clean`() {
        val dirtyState = ProjectDirtyState()
        dirtyState.markClean(key = ProjectDirtyState.SCHEMA, content = "schema: sample")

        assertFalse(actual = dirtyState.isDirty(key = ProjectDirtyState.SCHEMA, content = "schema: sample"))
        assertTrue(actual = dirtyState.isDirty(key = ProjectDirtyState.SCHEMA, content = "schema: edited"))
    }

    /** Editing back to the original is not a change — a flag would still claim it was. */
    @Test
    fun `editing back to the baseline is clean again`() {
        val dirtyState = ProjectDirtyState()
        dirtyState.markClean(key = ProjectDirtyState.SCHEMA, content = "original")

        assertTrue(actual = dirtyState.isDirty(key = ProjectDirtyState.SCHEMA, content = "changed"))
        assertFalse(actual = dirtyState.isDirty(key = ProjectDirtyState.SCHEMA, content = "original"))
    }

    /** With no baseline, content that exists has never been saved and so counts as unsaved work. */
    @Test
    fun `unknown buffers are dirty only when they hold something`() {
        val dirtyState = ProjectDirtyState()

        assertFalse(actual = dirtyState.isDirty(key = ProjectDirtyState.ACTIONS, content = ""))
        assertTrue(actual = dirtyState.isDirty(key = ProjectDirtyState.ACTIONS, content = "actions:"))
    }

    @Test
    fun `rule files are tracked per path`() {
        val dirtyState = ProjectDirtyState()
        dirtyState.markClean(key = ProjectDirtyState.ruleKey(relativePath = "rules/a.rule"), content = "a")

        assertFalse(actual = dirtyState.isDirty(key = ProjectDirtyState.ruleKey(relativePath = "rules/a.rule"), content = "a"))
        assertTrue(actual = dirtyState.isDirty(key = ProjectDirtyState.ruleKey(relativePath = "rules/b.rule"), content = "a"))
    }
}
