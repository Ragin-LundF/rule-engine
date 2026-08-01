package ruleengine.normalizer

import ruleengine.core.domain.dto.NormalizerId
import ruleengine.core.normalizer.NormalizerRegistry
import kotlin.test.Test
import kotlin.test.assertEquals

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
}

