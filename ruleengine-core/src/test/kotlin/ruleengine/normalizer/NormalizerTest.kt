package ruleengine.normalizer

import ruleengine.core.domain.dto.NormalizerId
import ruleengine.core.normalizer.NormalizerRegistry
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

class NormalizerTest {
    @Test
    fun `german fold profile normalizes example`() {
        val profile = NormalizerRegistry.createProfile(
            id = "germanText",
            steps = listOf(
                NormalizerId("trim"),
                NormalizerId("lowercase"),
                NormalizerId("german_umlaut_fold"),
                NormalizerId("collapse_whitespace")
            )
        )

        val input = "Müller  GmbH"
        val normalized = profile.apply(input)

        assertEquals(expected = "mueller gmbh", actual = normalized)
    }

    /**
     * One case per built-in, so a registry entry cannot go untested.
     *
     * `remove_punctuation` had no test at all — it is documented in the language reference and was
     * exercised by nothing.
     */
    @Test
    fun `every built-in normalizer transforms its input`() {
        assertEquals(expected = "abc", actual = apply(id = "trim", value = "  abc  "))
        assertEquals(expected = "abc", actual = apply(id = "lowercase", value = "ABC"))
        assertEquals(expected = "ABC", actual = apply(id = "uppercase", value = "abc"))
        assertEquals(expected = "a b", actual = apply(id = "collapse_whitespace", value = "a   \t b"))
        assertEquals(expected = "aeoeuess", actual = apply(id = "german_umlaut_fold", value = "äöüß"))
        assertEquals(expected = "abc", actual = apply(id = "remove_punctuation", value = "a.b,c!"))
    }

    @Test
    fun `remove_punctuation keeps letters digits and spaces`() {
        assertEquals(
            expected = "Mueller GmbH 42",
            actual = apply(id = "remove_punctuation", value = "Mueller (GmbH), #42!"),
        )
    }

    @Test
    fun `remove_punctuation leaves an unpunctuated value alone`() {
        assertEquals(expected = "plain text", actual = apply(id = "remove_punctuation", value = "plain text"))
    }

    /** The registry's own list is what the schema editor and YAML completions offer. */
    @Test
    fun `every advertised normalizer id resolves`() {
        NormalizerRegistry.ids.forEach { id ->
            assertNotNull(
                actual = NormalizerRegistry.get(id = id),
                message = "'${id.value}' is advertised but does not resolve",
            )
        }
    }

    private fun apply(id: String, value: String): String =
        NormalizerRegistry.applyAll(value = value, normalizers = listOf(NormalizerId(id)))
}

