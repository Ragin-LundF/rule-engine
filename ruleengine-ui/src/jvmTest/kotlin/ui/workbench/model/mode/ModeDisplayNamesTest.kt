package ui.workbench.model.mode

import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * The four areas must name the same two ideas with the same two words.
 *
 * This is the regression guard for the harmonised header: the app used to offer Builder / Code,
 * Visual / YAML and Builder / YAML for one pair of modes, because each area held its own labels next to
 * its own tab composable. These assertions fail the moment an area invents a third word.
 */
class ModeDisplayNamesTest {

    @Test
    fun `every area calls its model-editing mode Visual`() {
        assertEquals(expected = "Visual", actual = RuleMode.BUILDER.displayName)
        assertEquals(expected = "Visual", actual = SchemaMode.VISUAL.displayName)
        assertEquals(expected = "Visual", actual = ActionMode.VISUAL.displayName)
        assertEquals(expected = "Visual", actual = ManifestMode.BUILDER.displayName)
    }

    @Test
    fun `every area calls its text mode Code`() {
        assertEquals(expected = "Code", actual = RuleMode.CODE.displayName)
        assertEquals(expected = "Code", actual = SchemaMode.YAML.displayName)
        assertEquals(expected = "Code", actual = ActionMode.YAML.displayName)
        assertEquals(expected = "Code", actual = ManifestMode.YAML.displayName)
    }

    @Test
    fun `the board reports the tab it is drawn under`() {
        assertEquals(expected = RuleMode.BUILDER.displayName, actual = RuleMode.BOARD.displayName)
    }

    @Test
    fun `the rules-only views keep their own names`() {
        assertEquals(expected = "Diagram", actual = RuleMode.DIAGRAM.displayName)
        assertEquals(expected = "Test", actual = RuleMode.TEST.displayName)
        assertEquals(expected = "Table", actual = RuleMode.TABLE.displayName)
    }

    @Test
    fun `no area contributes a mode name outside the shared vocabulary`() {
        val sharedNames = SchemaMode.entries.map { mode -> mode.displayName } +
            ActionMode.entries.map { mode -> mode.displayName } +
            ManifestMode.entries.map { mode -> mode.displayName }

        assertEquals(expected = setOf("Visual", "Code"), actual = sharedNames.toSet())
    }
}
