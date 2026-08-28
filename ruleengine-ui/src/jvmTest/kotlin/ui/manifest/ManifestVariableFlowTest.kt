package ui.manifest

import ruleengine.dsl.parser.Parser
import ui.builder.RuleAstToBuilderMapper
import ui.builder.board.ribbon.RibbonModel
import ui.builder.board.ribbon.model.RibbonGroup
import ui.builder.model.BuilderRule
import ui.workbench.model.catalog.CatalogRule
import ui.workbench.model.catalog.RuleTreeFile
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The one property of a manifest that only the manifest can be wrong about: **the order of its files.**
 *
 * A `$variable` is visible only to files listed after the one that publishes it, so the same two files
 * in the other order turn a working entry into one where a read never resolves — and the rule still
 * parses, still validates, and silently never fires. That is why these assertions are about *reversing
 * the list*, not about either file on its own: nothing a single-file view can see changes.
 *
 * Driven through the real `RibbonModel.groups`, the same derivation the board uses, so the manifest and
 * the board cannot come to different answers about the same two files.
 */
class ManifestVariableFlowTest {

    private val publisher = """
        rule "sets-it" {
          when
            amount >= 1
          then
            set budget = 100
        }
    """.trimIndent()

    private val consumer = """
        rule "reads-it" {
          when
            ${'$'}budget <= amount
          then
            flag "over"
        }
    """.trimIndent()

    private val bystander = """
        rule "no-variables" {
          when
            amount >= 5
          then
            flag "plain"
        }
    """.trimIndent()

    /** [sources] are `relativePath to dsl`, in the order the manifest lists them. */
    private fun groupsOf(vararg sources: Pair<String, String>): List<RibbonGroup> {
        val rules = mutableListOf<BuilderRule>()
        val files = sources.map { (path, dsl) ->
            val parsed = Parser(input = dsl).parseRules().map { ast -> RuleAstToBuilderMapper.map(rule = ast) }
            rules.addAll(elements = parsed)
            RuleTreeFile(
                relativePath = path,
                rules = parsed.map { rule -> CatalogRule(id = (rule as BuilderRule.Supported).id) },
            )
        }
        return RibbonModel.groups(files = files, rules = rules)
    }

    @Test
    fun `a file states what it publishes and what it reads`() {
        val flow = ManifestVariableFlow.of(
            groups = groupsOf("a.rule" to publisher, "b.rule" to consumer),
        ).associateBy { file -> file.relativePath }

        assertEquals(expected = listOf("budget"), actual = flow.getValue(key = "a.rule").sets)
        assertEquals(expected = emptyList(), actual = flow.getValue(key = "a.rule").reads.map { it.name })
        assertEquals(expected = listOf("budget"), actual = flow.getValue(key = "b.rule").reads.map { it.name })
    }

    @Test
    fun `a read after the file that publishes it resolves`() {
        val flow = ManifestVariableFlow.of(
            groups = groupsOf("a.rule" to publisher, "b.rule" to consumer),
        )

        assertTrue(actual = flow.single { file -> file.relativePath == "b.rule" }.reads.single().resolved)
        assertTrue(actual = flow.none { file -> file.hasUnresolvedRead })
    }

    /**
     * The finding this whole feature exists for. Neither file changed — only their order — and the
     * entry stopped working.
     */
    @Test
    fun `reversing the two files leaves the read with nothing to resolve against`() {
        val flow = ManifestVariableFlow.of(
            groups = groupsOf("b.rule" to consumer, "a.rule" to publisher),
        )

        val reader = flow.single { file -> file.relativePath == "b.rule" }
        assertEquals(expected = listOf("budget"), actual = reader.reads.map { read -> read.name })
        assertTrue(actual = !reader.reads.single().resolved)
        assertTrue(actual = reader.hasUnresolvedRead)
    }

    @Test
    fun `an unresolved read is reported against its own file, as its consequence`() {
        val reported = ManifestVariableFlow.unresolvedReads(
            groups = groupsOf("b.rule" to consumer, "a.rule" to publisher),
        )

        val (path, message) = reported.single()
        assertEquals(expected = "b.rule", actual = path)
        assertTrue(actual = message.contains(other = "\$budget"))
        // The author observes the null, not the missing producer.
        assertTrue(actual = message.contains(other = "null on every run"))
    }

    @Test
    fun `the right order reports nothing`() {
        assertTrue(
            actual = ManifestVariableFlow.unresolvedReads(
                groups = groupsOf("a.rule" to publisher, "b.rule" to consumer),
            ).isEmpty(),
        )
    }

    /** A file with no variables at all must produce empty lists, so its row draws no chips. */
    @Test
    fun `a file that exchanges nothing has nothing to show`() {
        val flow = ManifestVariableFlow.of(groups = groupsOf("plain.rule" to bystander)).single()

        assertEquals(expected = emptyList(), actual = flow.sets)
        assertEquals(expected = emptyList(), actual = flow.reads)
        assertTrue(actual = !flow.hasUnresolvedRead)
    }

    /** Two files can name the same variable; each row answers for its own position in the run. */
    @Test
    fun `each file is judged at its own position`() {
        val flow = ManifestVariableFlow.of(
            groups = groupsOf("early.rule" to consumer, "mid.rule" to publisher, "late.rule" to consumer),
        ).associateBy { file -> file.relativePath }

        assertTrue(actual = flow.getValue(key = "early.rule").hasUnresolvedRead)
        assertTrue(actual = !flow.getValue(key = "late.rule").hasUnresolvedRead)
    }
}
