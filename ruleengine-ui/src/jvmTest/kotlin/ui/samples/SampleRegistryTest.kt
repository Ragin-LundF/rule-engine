package ui.samples

import ruleengine.manifest.ManifestLoader
import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The gallery loads its samples by resource path, so a renamed or moved file only fails when a user clicks
 * the card. These tests fail at build time instead.
 */
class SampleRegistryTest {

    private val resourcesDir: Path = Path.of("src/commonMain/composeResources")

    @Test
    fun `every registered sample resource exists`() {
        for (descriptor in SampleRegistry.all) {
            val paths = listOf(
                descriptor.manifestResPath,
                descriptor.schemaResPath,
                descriptor.actionsResPath,
            ) + descriptor.ruleResPaths
            for (resourcePath in paths) {
                assertTrue(
                    actual = Files.isRegularFile(resourcesDir.resolve(resourcePath)),
                    message = "Sample '${descriptor.id}' references a missing resource: $resourcePath",
                )
            }
        }
    }

    /**
     * The registry and the manifest have to agree on which rule files a sample has, and in which order.
     *
     * Both bugs this catches were shipped: `financial-transactions` listed three of its five rule files, so
     * the gallery silently loaded a sample without its aggregate and boolean/date rules; and
     * `access-control` listed them in an order the manifest did not use, which matters now that a `stop`
     * in the first file suppresses the rest. Evaluation order is a guarantee — a sample that loads in a
     * different order from its own manifest demonstrates the wrong thing.
     */
    @Test
    fun `every sample lists exactly the manifest's rule files, in manifest order`() {
        for (descriptor in SampleRegistry.all) {
            val manifest = ManifestLoader.load(path = resourcesDir.resolve(descriptor.manifestResPath))
            val sampleDir = descriptor.manifestResPath.substringBeforeLast(delimiter = '/')

            val expected = manifest.entries.flatMap { entry -> entry.rules }.map { path -> "$sampleDir/$path" }
            assertEquals(
                expected = expected,
                actual = descriptor.ruleResPaths,
                message = "Sample '${descriptor.id}': registry rule files differ from manifest.yaml",
            )
        }
    }

    /** The same for the schema and action schema, which the manifest entry also names. */
    @Test
    fun `every sample points at the schema and actions its manifest declares`() {
        for (descriptor in SampleRegistry.all) {
            val manifest = ManifestLoader.load(path = resourcesDir.resolve(descriptor.manifestResPath))
            val entry = manifest.entries.single()
            val sampleDir = descriptor.manifestResPath.substringBeforeLast(delimiter = '/')

            assertEquals(
                expected = "$sampleDir/${entry.schema}",
                actual = descriptor.schemaResPath,
                message = "Sample '${descriptor.id}': registry schema differs from manifest.yaml",
            )
            assertEquals(
                expected = "$sampleDir/${entry.actions}",
                actual = descriptor.actionsResPath,
                message = "Sample '${descriptor.id}': registry actions differ from manifest.yaml",
            )
        }
    }

    /**
     * The warehouse-shipments sample is the worked example the documentation points at, and it is executed
     * against real input data by `WarehouseShipmentsIntegrationTest` in `ruleengine-core`. The two copies
     * must stay identical, otherwise the documented example and the tested example drift apart.
     */
    @Test
    fun `warehouse shipments sample matches the tested core example`() {
        val sampleDir = resourcesDir.resolve("files/samples/warehouse-shipments")
        val coreDir = Path.of("../ruleengine-core/src/test/resources/warehouse-shipments")

        val files = listOf(
            "schema.yaml",
            "actions.yaml",
            "rules/shipment-totals.rule",
            "rules/delivery-quality.rule",
            "rules/parcel-condition.rule",
            "rules/route-risk.rule",
        )
        for (file in files) {
            assertEquals(
                expected = Files.readString(coreDir.resolve(file)),
                actual = Files.readString(sampleDir.resolve(file)),
                message = "UI sample file '$file' differs from the core example bundle",
            )
        }
    }
}
