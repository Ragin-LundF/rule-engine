package ui.builder

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertSame
import kotlin.test.assertTrue

/**
 * The Builder's selection and per-rule state, previously two `LaunchedEffect` bodies and four click
 * handlers inlined in the editor screen.
 *
 * These are characterization tests: every assertion pins what the inlined code already did, including
 * the parts that look like bugs — the naive rename substitution, and the caret-resetting text writes
 * the screen performs. The frozen contract in the refactor plan names both.
 */
class BuilderRulesControllerTest {

    private var text: String = ""

    private fun controller() = BuilderRulesController(
        ruleText = { text },
        onRuleTextChange = { text = it },
    )

    private fun rule(id: String) = BuilderRule.Supported(
        id = id,
        conditionNodes = emptyList(),
        actions = emptyList(),
    )

    // ── syncSelection ─────────────────────────────────────────────────────────

    @Test
    fun `with nothing selected the first available rule wins`() {
        val c = controller()

        c.syncSelection(rules = listOf(rule(id = "a"), rule(id = "b")), preferredId = null)

        assertEquals(expected = "a", actual = c.selectedId.value)
    }

    @Test
    fun `the preferred rule wins over the first when nothing is selected yet`() {
        val c = controller()

        c.syncSelection(rules = listOf(rule(id = "a"), rule(id = "b")), preferredId = "b")

        assertEquals(expected = "b", actual = c.selectedId.value)
    }

    @Test
    fun `an existing selection outranks the preferred rule`() {
        val c = controller()
        c.select(ruleId = "b")

        c.syncSelection(rules = listOf(rule(id = "a"), rule(id = "b")), preferredId = "a")

        assertEquals(expected = "b", actual = c.selectedId.value)
    }

    /**
     * A parse that fails mid-keystroke yields no rules at all. Clearing the selection there would
     * blank the Builder on every syntax error, so the last selection is held instead.
     */
    @Test
    fun `a failed parse keeps the current selection rather than clearing it`() {
        val c = controller()
        c.select(ruleId = "a")

        c.syncSelection(rules = emptyList(), preferredId = null)

        assertEquals(expected = "a", actual = c.selectedId.value)
    }

    /**
     * The point of `pendingId`: a rule that was just added does not exist in the parse yet, so
     * without the hold the sync would fall through to "first available" and the selection would snap
     * off the new rule the moment it was created.
     */
    @Test
    fun `a pending rule is held until the parse catches up, then adopted`() {
        val c = controller()
        c.select(ruleId = "a")
        c.selectWhenParsed(ruleId = "new")

        c.syncSelection(rules = listOf(rule(id = "a")), preferredId = "a")
        assertEquals(expected = "new", actual = c.selectedId.value, message = "must not snap back to a")
        assertEquals(expected = "new", actual = c.pendingId.value, message = "still waiting")

        c.syncSelection(rules = listOf(rule(id = "a"), rule(id = "new")), preferredId = "a")
        assertEquals(expected = "new", actual = c.selectedId.value)
        assertEquals(expected = "", actual = c.pendingId.value, message = "consumed once it resolves")
    }

    @Test
    fun `a blank rule id is not a selectable rule`() {
        val c = controller()

        c.syncSelection(rules = listOf(BuilderRule.None, rule(id = "a")), preferredId = null)

        assertEquals(expected = "a", actual = c.selectedId.value)
    }

    // ── rebuildStateMap ───────────────────────────────────────────────────────

    @Test
    fun `a new rule gets a state`() {
        val c = controller()

        c.rebuildStateMap(rules = listOf(rule(id = "a")))

        assertEquals(expected = setOf("a"), actual = c.stateMap.value.keys)
    }

    @Test
    fun `an unchanged rule keeps the very same state instance`() {
        text = "rule \"a\" {\n  when\n  then\n}"
        val c = controller()
        c.rebuildStateMap(rules = listOf(rule(id = "a")))
        val first = c.stateMap.value.getValue("a")

        c.rebuildStateMap(rules = listOf(rule(id = "a")))

        assertSame(
            expected = first,
            actual = c.stateMap.value.getValue("a"),
            message = "rebuilding would discard in-progress edits",
        )
    }

    /**
     * How a Code-mode edit reaches the Builder: the cached state would generate DSL the buffer no
     * longer contains, so it is rebuilt from the fresh parse.
     */
    @Test
    fun `a state whose dsl is no longer in the buffer is rebuilt`() {
        text = "rule \"a\" {\n  when\n  then\n}"
        val c = controller()
        c.rebuildStateMap(rules = listOf(rule(id = "a")))
        val first = c.stateMap.value.getValue("a")

        text = "rule \"a\" {\n  when\n    amount > 5\n  then\n}"
        c.rebuildStateMap(rules = listOf(rule(id = "a")))

        assertFalse(
            actual = first === c.stateMap.value.getValue("a"),
            message = "an external edit must invalidate the cached state",
        )
    }

    @Test
    fun `a rule that becomes unsupported is rebuilt as locked`() {
        val c = controller()
        c.rebuildStateMap(rules = listOf(rule(id = "a")))
        assertFalse(actual = c.stateMap.value.getValue("a").isLocked)

        c.rebuildStateMap(rules = listOf(BuilderRule.Unsupported(id = "a", reason = "syntax")))

        assertTrue(actual = c.stateMap.value.getValue("a").isLocked)
    }

    /**
     * A just-added rule has a state but is not in the parse yet. Dropping it here would make the new
     * rule vanish from the Builder for as long as the debounce takes.
     */
    @Test
    fun `a state with no matching parsed rule is carried over`() {
        val c = controller()
        c.add()
        val addedId = c.selectedId.value

        c.rebuildStateMap(rules = listOf(rule(id = "other")))

        assertTrue(actual = addedId in c.stateMap.value, message = "the new rule must survive the reparse")
        assertTrue(actual = "other" in c.stateMap.value)
    }

    // ── activeState ───────────────────────────────────────────────────────────

    @Test
    fun `a missing selection yields a fresh empty state each read`() {
        val c = controller()

        val first = c.activeState()
        val second = c.activeState()

        assertEquals(expected = "", actual = first.ruleId)
        assertFalse(
            actual = first === second,
            message = "memoising the fallback would let edits accumulate in a shared object",
        )
    }

    @Test
    fun `the selected rule's state is returned`() {
        val c = controller()
        c.rebuildStateMap(rules = listOf(rule(id = "a")))
        c.select(ruleId = "a")

        assertEquals(expected = "a", actual = c.activeState().ruleId)
    }

    // ── add ───────────────────────────────────────────────────────────────────

    @Test
    fun `adding to an empty buffer writes the skeleton with no leading newline`() {
        text = ""
        val c = controller()

        c.add()

        assertEquals(expected = "rule \"rule-1\" {\n  when\n  then\n}", actual = text)
    }

    @Test
    fun `adding to a non-empty buffer appends after a blank line`() {
        text = "rule \"a\" {\n  when\n  then\n}"
        val c = controller()
        c.rebuildStateMap(rules = listOf(rule(id = "a")))

        c.add()

        assertEquals(
            expected = "rule \"a\" {\n  when\n  then\n}\nrule \"rule-2\" {\n  when\n  then\n}",
            actual = text,
        )
    }

    @Test
    fun `an added rule is selected and parked as pending`() {
        val c = controller()

        c.add()

        assertEquals(expected = "rule-1", actual = c.selectedId.value)
        assertEquals(expected = "rule-1", actual = c.pendingId.value)
        assertTrue(actual = "rule-1" in c.stateMap.value)
    }

    // ── rename ────────────────────────────────────────────────────────────────

    @Test
    fun `renaming moves the state, the selection, and rewrites the header`() {
        text = "rule \"a\" {\n  when\n  then\n}"
        val c = controller()
        c.rebuildStateMap(rules = listOf(rule(id = "a")))

        c.rename(oldId = "a", newId = "b")

        assertEquals(expected = setOf("b"), actual = c.stateMap.value.keys)
        assertEquals(expected = "b", actual = c.selectedId.value)
        assertEquals(expected = "b", actual = c.pendingId.value)
        assertEquals(expected = "rule \"b\" {\n  when\n  then\n}", actual = text)
    }

    /**
     * Both guards exist because the caller is a text field: every intermediate keystroke of a rename
     * arrives here, and one of them is always the blank string.
     */
    @Test
    fun `a blank or already-taken name is ignored`() {
        text = "rule \"a\" {}\nrule \"b\" {}"
        val c = controller()
        c.rebuildStateMap(rules = listOf(rule(id = "a"), rule(id = "b")))

        c.rename(oldId = "a", newId = "")
        c.rename(oldId = "a", newId = "b")

        assertEquals(expected = setOf("a", "b"), actual = c.stateMap.value.keys)
        assertEquals(expected = "rule \"a\" {}\nrule \"b\" {}", actual = text)
    }

    @Test
    fun `renaming a rule with no state does nothing`() {
        text = "rule \"a\" {}"
        val c = controller()

        c.rename(oldId = "a", newId = "b")

        assertEquals(expected = "rule \"a\" {}", actual = text)
    }

    /**
     * Documented in the frozen contract as a known wart: the substitution is a plain global replace,
     * so the id also changes anywhere else the exact string `rule "old"` appears — a comment, or a
     * second rule whose id merely starts the same way is safe, but a comment is not. Pinned here so a
     * future fix is a deliberate change rather than an accident.
     */
    @Test
    fun `rename replaces every occurrence of the rule header text, including in comments`() {
        text = "// supersedes rule \"a\"\nrule \"a\" {\n  when\n  then\n}"
        val c = controller()
        c.rebuildStateMap(rules = listOf(rule(id = "a")))

        c.rename(oldId = "a", newId = "b")

        assertEquals(expected = "// supersedes rule \"b\"\nrule \"b\" {\n  when\n  then\n}", actual = text)
    }

    // ── applyDsl ──────────────────────────────────────────────────────────────

    @Test
    fun `applying dsl replaces only the named rule's block`() {
        text = "rule \"a\" {\n  when\n  then\n}\n\nrule \"b\" {\n  when\n  then\n}"
        val c = controller()

        c.applyDsl(ruleId = "a", newDsl = "rule \"a\" {\n  when\n    x > 1\n  then\n}")

        assertTrue(actual = "x > 1" in text)
        assertTrue(actual = "rule \"b\" {\n  when\n  then\n}" in text, message = "sibling rules stay intact")
    }
}
