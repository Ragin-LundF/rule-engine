package ui.samples

import ruleengine.compiler.Compiler
import ruleengine.core.domain.dto.EvaluationResult
import ruleengine.dsl.parser.Parser
import ruleengine.evaluator.RuleEngine
import ruleengine.evaluator.context.MapRuleContext
import ruleengine.evaluator.context.PreparedRuleContext
import ruleengine.jackson.JacksonUtil
import ruleengine.manifest.ManifestLoader
import ruleengine.schema.FieldSchemaLoader
import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Behaviour of the `kyc-onboarding` sample, which exists to demonstrate one specific property: the
 * requirement checks report **together** rather than one at a time.
 *
 * `SampleProjectBuilderTest` already proves every sample validates and round-trips through the Builder.
 * This goes further for this one sample because its value is behavioural: a `stop` accidentally added to
 * a requirement rule would still validate, still round-trip, and quietly turn the sample into the
 * "further documents required" loop it was written to argue against.
 */
class KycSampleBehaviourTest {

    private val sampleDir: Path = Path.of("src/commonMain/composeResources/files/samples/kyc-onboarding")

    private val schema = FieldSchemaLoader.load(path = sampleDir.resolve("schema.yaml"))

    /** The rules in manifest order, which is what the two guards depend on. */
    private val compiledRules = run {
        val manifest = ManifestLoader.load(path = sampleDir.resolve("manifest.yaml"))
        val dsl = manifest.entries.single().rules
            .joinToString(separator = "\n\n") { path -> Files.readString(sampleDir.resolve(path)) }

        Compiler.compileRules(asts = Parser(input = dsl).parseRules(), schema = schema)
    }

    private fun evaluate(inputJson: String): EvaluationResult {
        @Suppress("UNCHECKED_CAST")
        val input = JacksonUtil.jsonMapper.readValue(inputJson, Map::class.java) as Map<String, Any?>
        val prepared = PreparedRuleContext.prepare(ctx = MapRuleContext(map = input), schema = schema)

        return RuleEngine(compiledRules = compiledRules).evaluate(prepared = prepared)
    }

    private fun EvaluationResult.argumentsOf(action: String): List<String> {
        return matches.flatMap { match -> match.actions }
            .filter { emitted -> emitted.name == action }
            .map { emitted -> emitted.arguments.single().toString() }
    }

    @Test
    fun `an unsubmitted order stops after one message instead of listing documents`() {
        val result = evaluate(inputJson = unsubmitted)

        assertEquals(expected = "order-completed", actual = result.stoppedBy)
        assertEquals(expected = listOf("order-completed"), actual = result.argumentsOf(action = "requirement"))
        assertEquals(expected = listOf("order-incomplete"), actual = result.argumentsOf(action = "status"))
        assertEquals(
            expected = 1,
            actual = result.argumentsOf(action = "message").size,
            message = "an unfinished order gets exactly one sentence, not a checklist",
        )
    }

    /**
     * The property the sample is for: several outstanding requirements come back from **one** run, each
     * named, so a frontend can render the whole checklist instead of one item per submission.
     */
    @Test
    fun `a partially completed case reports every outstanding requirement at once`() {
        val result = evaluate(inputJson = partiallyComplete)

        assertNull(actual = result.stoppedBy, message = "requirement checks must not stop the run")
        assertEquals(
            expected = listOf(
                "shareholder-list",
                "beneficial-owners-identified",
                "transparency-register-checked",
                "reference-account",
            ),
            actual = result.argumentsOf(action = "requirement"),
        )
        assertEquals(expected = listOf("documents-outstanding"), actual = result.argumentsOf(action = "status"))
        // Satisfied items are emitted too, so a checklist can tick them off rather than infer them.
        assertTrue(
            actual = result.argumentsOf(action = "satisfied").containsAll(
                elements = listOf("commercial-register-extract", "articles-of-association", "vat-id"),
            ),
            message = "got: ${result.argumentsOf(action = "satisfied")}",
        )
    }

    @Test
    fun `a complete case is ready for review with nothing outstanding`() {
        val result = evaluate(inputJson = complete)

        assertNull(actual = result.stoppedBy)
        assertEquals(expected = emptyList(), actual = result.argumentsOf(action = "requirement"))
        assertEquals(expected = listOf("ready-for-review"), actual = result.argumentsOf(action = "status"))
    }

    @Test
    fun `a sanctions hit stops the run and produces no document checklist`() {
        val result = evaluate(inputJson = sanctioned)

        assertEquals(expected = "sanctions-hit", actual = result.stoppedBy)
        assertEquals(expected = listOf("rejected"), actual = result.argumentsOf(action = "status"))
        assertEquals(
            expected = emptyList(),
            actual = result.argumentsOf(action = "requirement"),
            message = "there is nothing the customer can upload to clear a sanctions hit",
        )
    }

    /**
     * The contrast the sample is built around: enhanced due diligence is *not* a rejection, so a PEP case
     * keeps going and still reaches a verdict.
     */
    @Test
    fun `a politically exposed owner raises the risk level without stopping the run`() {
        val result = evaluate(inputJson = politicallyExposed)

        assertNull(actual = result.stoppedBy)
        assertEquals(expected = listOf("enhanced"), actual = result.argumentsOf(action = "riskLevel"))
        assertEquals(expected = listOf("politically-exposed-person"), actual = result.argumentsOf(action = "review"))
        assertEquals(expected = listOf("ready-for-review"), actual = result.argumentsOf(action = "status"))
    }

    // ── inputs ────────────────────────────────────────────────────────────────

    private val unsubmitted = """
        {
          "orderStatus": "in-progress",
          "sanctionsListMatch": false,
          "legalForm": "gmbh",
          "providedDocuments": [],
          "commercialRegisterExtractAgeDays": 999,
          "representativeIdentification": "none",
          "powerOfAttorneyRequired": false,
          "transparencyRegisterChecked": false,
          "sourceOfFundsDeclared": false,
          "expectedMonthlyVolume": 10000,
          "beneficialOwners": [],
          "representatives": []
        }
    """.trimIndent()

    private val partiallyComplete = """
        {
          "orderStatus": "completed",
          "sanctionsListMatch": false,
          "companyName": "Muster Handels GmbH",
          "legalForm": "gmbh",
          "commercialRegisterNumber": "HRB 123456",
          "commercialRegisterExtractAgeDays": 20,
          "countryOfIncorporation": "de",
          "operatingCountries": ["de", "at"],
          "businessPurpose": "payment-acceptance",
          "expectedMonthlyVolume": 20000,
          "providedDocuments": ["commercial-register-extract", "articles-of-association"],
          "representativeIdentification": "video-ident",
          "powerOfAttorneyRequired": false,
          "transparencyRegisterChecked": false,
          "sourceOfFundsDeclared": false,
          "vatId": "DE123456789",
          "referenceIban": "",
          "beneficialOwners": [
            { "name": "A. Muster", "ownershipPercent": 60, "identityVerified": true, "politicallyExposed": false },
            { "name": "B. Beispiel", "ownershipPercent": 30, "identityVerified": false, "politicallyExposed": false }
          ],
          "representatives": [
            { "name": "A. Muster", "identityVerified": true, "politicallyExposed": false }
          ]
        }
    """.trimIndent()

    private val complete = """
        {
          "orderStatus": "completed",
          "sanctionsListMatch": false,
          "companyName": "Muster Handels GmbH",
          "legalForm": "gmbh",
          "commercialRegisterNumber": "HRB 123456",
          "commercialRegisterExtractAgeDays": 20,
          "countryOfIncorporation": "de",
          "operatingCountries": ["de", "at"],
          "businessPurpose": "payment-acceptance",
          "expectedMonthlyVolume": 20000,
          "providedDocuments": ["commercial-register-extract", "articles-of-association", "shareholder-list"],
          "representativeIdentification": "video-ident",
          "powerOfAttorneyRequired": false,
          "transparencyRegisterChecked": true,
          "sourceOfFundsDeclared": false,
          "vatId": "DE123456789",
          "referenceIban": "DE89370400440532013000",
          "beneficialOwners": [
            { "name": "A. Muster", "ownershipPercent": 60, "identityVerified": true, "politicallyExposed": false }
          ],
          "representatives": [
            { "name": "A. Muster", "identityVerified": true, "politicallyExposed": false }
          ]
        }
    """.trimIndent()

    /** The complete case with the sole owner flagged as a PEP — everything else identical. */
    private val politicallyExposed = complete
        .replace(oldValue = """"politicallyExposed": false""", newValue = """"politicallyExposed": true""")

    private val sanctioned = partiallyComplete
        .replace(oldValue = """"sanctionsListMatch": false""", newValue = """"sanctionsListMatch": true""")

}
