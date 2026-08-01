package ui.samples

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
            val paths = listOf(descriptor.schemaResPath, descriptor.actionsResPath) + descriptor.ruleResPaths
            for (resourcePath in paths) {
                assertTrue(
                    actual = Files.isRegularFile(resourcesDir.resolve(resourcePath)),
                    message = "Sample '${descriptor.id}' references a missing resource: $resourcePath",
                )
            }
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
