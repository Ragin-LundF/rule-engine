package ui.editor.rules

import ruleengine.dsl.ast.AssignmentKindAst
import ruleengine.dsl.parser.Parser
import ui.diagrams.model.RuleSource
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Which variables the editor treats as already published when it validates one file of an entry.
 *
 * Manifest order is the whole point: a `set` in a later file must stay out of scope, or the editor
 * would accept a forward reference the engine rejects at load time.
 */
class EntryVariableScopeTest {

    private val totals = ruleFile(
        path = "rules/01-totals.rule",
        text = """
            rule "totals" {
              description "Publishes the turnover once."
              when
                amount > 0
              then
                set turnover = amount
            }
        """.trimIndent(),
    )

    private val checks = ruleFile(
        path = "rules/02-checks.rule",
        text = """
            rule "checks" {
              description "Records a failed check."
              when
                amount < 0
              then
                add "negative" to failedChecks
            }
        """.trimIndent(),
    )

    private val assessment = ruleFile(
        path = "rules/zz-assessment.rule",
        text = """
            rule "assessment" {
              description "Reads what the files before it published."
              when
                not ${'$'}failedChecks contains "negative"
              then
                label "ok"
            }
        """.trimIndent(),
    )

    private val later = ruleFile(
        path = "rules/zzz-later.rule",
        text = """
            rule "later" {
              description "Publishes something nobody before it may read."
              when
                amount > 0
              then
                set epilogue = amount
            }
        """.trimIndent(),
    )

    @Test
    fun `the writers of every earlier file are inherited, with their clause kind`() {
        val inherited = inheritedVariablesBefore(
            openPath = assessment.relativePath,
            files = listOf(totals, checks, assessment, later),
        )

        assertEquals(
            expected = mapOf(
                "turnover" to AssignmentKindAst.SET,
                "failedChecks" to AssignmentKindAst.ADD,
            ),
            actual = inherited,
        )
    }

    @Test
    fun `a writer in a later file stays out of scope`() {
        val inherited = inheritedVariablesBefore(
            openPath = checks.relativePath,
            files = listOf(totals, checks, assessment, later),
        )

        assertEquals(expected = mapOf("turnover" to AssignmentKindAst.SET), actual = inherited)
    }

    @Test
    fun `the first file of an entry inherits nothing`() {
        val inherited = inheritedVariablesBefore(
            openPath = totals.relativePath,
            files = listOf(totals, checks),
        )

        assertEquals(expected = emptyMap(), actual = inherited)
    }

    @Test
    fun `a buffer holding the whole entry inherits nothing`() {
        val inherited = inheritedVariablesBefore(openPath = null, files = listOf(totals, checks))

        assertEquals(expected = emptyMap(), actual = inherited)
    }

    @Test
    fun `a file the manifest does not list inherits nothing`() {
        val inherited = inheritedVariablesBefore(
            openPath = "rules/scratch.rule",
            files = listOf(totals, checks),
        )

        assertEquals(expected = emptyMap(), actual = inherited)
    }

    @Test
    fun `the first writer of a name decides the kind`() {
        val secondWriter = ruleFile(
            path = "rules/03-more.rule",
            text = """
                rule "more" {
                  description "Publishes a name the first file already publishes."
                  when
                    amount > 1
                  then
                    set turnover = amount
                }
            """.trimIndent(),
        )

        val inherited = inheritedVariablesBefore(
            openPath = assessment.relativePath,
            files = listOf(totals, secondWriter, assessment),
        )

        assertEquals(expected = mapOf("turnover" to AssignmentKindAst.SET), actual = inherited)
    }

    private fun ruleFile(path: String, text: String): RuleSource {
        return RuleSource(relativePath = path, rules = Parser(input = text).parseRules())
    }
}
