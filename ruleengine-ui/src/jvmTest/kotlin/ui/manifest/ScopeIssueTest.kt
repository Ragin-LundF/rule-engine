package ui.manifest

import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * What the editor says about a manifest entry's `scope`.
 *
 * The engine refuses to load a manifest whose scope names nothing or names a non-collection
 * (`ruleengine.builder.RuleEngineBuilder.scopedSchema`), while the editor used to fall back to the
 * unscoped schema and say nothing — so a project could look correct here and fail everywhere else.
 * These pin the verdicts and the wording, which is copied from the engine on purpose.
 */
class ScopeIssueTest {

    private val fieldTypes = mapOf(
        "caseId" to "text",
        "reports" to "collection",
        "customer" to "object",
    )

    @Test
    fun `a collection is a valid scope`() {
        assertEquals(expected = null, actual = scopeIssue(scope = "reports", fieldTypes = fieldTypes))
    }

    @Test
    fun `no scope means whole-document evaluation, which is the default`() {
        assertEquals(expected = null, actual = scopeIssue(scope = "", fieldTypes = fieldTypes))
    }

    @Test
    fun `a name the schema does not declare is reported the way the engine reports it`() {
        assertEquals(
            expected = "scope 'tag' is not a field of the schema",
            actual = scopeIssue(scope = "tag", fieldTypes = fieldTypes),
        )
    }

    @Test
    fun `a scalar cannot be scoped over, and the message names its type`() {
        assertEquals(
            expected = "scope 'caseId' is text, not a collection",
            actual = scopeIssue(scope = "caseId", fieldTypes = fieldTypes),
        )
    }

    /** An object holds one record, so there are no members to run once per. */
    @Test
    fun `an object is rejected too`() {
        assertEquals(
            expected = "scope 'customer' is object, not a collection",
            actual = scopeIssue(scope = "customer", fieldTypes = fieldTypes),
        )
    }

    /**
     * Surrounding whitespace survives YAML quoting, and `ManifestYamlBridge` writes the scope
     * trimmed — so the check has to agree with what actually reaches the file.
     */
    @Test
    fun `a padded scope is judged trimmed`() {
        assertEquals(expected = null, actual = scopeIssue(scope = "  reports  ", fieldTypes = fieldTypes))
    }

    /** No schema loaded is no verdict: there is nothing to check the name against. */
    @Test
    fun `an unknown schema reports nothing`() {
        assertEquals(expected = null, actual = scopeIssue(scope = "tag", fieldTypes = null))
    }

    @Test
    fun `the hint lists only the collections, sorted`() {
        val withMore = fieldTypes + mapOf("audits" to "collection")

        assertEquals(expected = listOf("audits", "reports"), actual = collectionNames(fieldTypes = withMore))
    }

    @Test
    fun `the hint is empty when the schema declares no collection`() {
        assertEquals(expected = emptyList(), actual = collectionNames(fieldTypes = mapOf("caseId" to "text")))
    }
}
