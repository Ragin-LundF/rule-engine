package ruleengine.manifest.source

import ruleengine.builder.ClasspathProjectJars.ACTIONS_YAML
import ruleengine.builder.ClasspathProjectJars.SCHEMA_YAML
import ruleengine.builder.ClasspathProjectJars.manifest
import ruleengine.builder.ClasspathProjectJars.nestedProject
import ruleengine.builder.ResourceStreamOnlyClassLoader
import ruleengine.manifest.FileSystemManifestFileResolver
import ruleengine.manifest.ManifestFile
import ruleengine.manifest.classpath.ClasspathManifestFileResolver
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.writeText
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

/**
 * Pins how a location string is routed: the `classpath:` prefix is the only thing that decides
 * between a packaged manifest and one on disk, and a caller writes it in configuration.
 */
class ManifestSourceTest {
    @Test
    fun `a classpath prefixed location reads through the class loader`() {
        val loader = ResourceStreamOnlyClassLoader(resources = nestedProject())

        val source = ManifestSource.of(location = "classpath:rules/manifest.yaml", classLoader = loader)

        assertIs<ClasspathManifestFileResolver>(value = source.resolver)
        assertEquals(expected = listOf("e"), actual = source.readManifest().entries.map { it.id })
    }

    @Test
    fun `the classpath prefix is stripped from the resource name that labels failures`() {
        val loader = ResourceStreamOnlyClassLoader(resources = nestedProject())

        val source = ManifestSource.of(location = "classpath:/rules/manifest.yaml", classLoader = loader)

        assertEquals(expected = Path.of("rules/manifest.yaml"), actual = source.location)
    }

    @Test
    fun `a location without the prefix is read from the filesystem`() {
        val dir = writeProject()

        val source = ManifestSource.of(location = dir.resolve("manifest.yaml").toString())

        assertIs<FileSystemManifestFileResolver>(value = source.resolver)
        assertEquals(expected = listOf("e"), actual = source.readManifest().entries.map { it.id })
    }

    @Test
    fun `a filesystem location resolves entry files against the manifest directory`() {
        val dir = writeProject()

        val source = ManifestSource.of(location = dir.resolve("manifest.yaml").toString())
        val file = source.resolver.resolve(relativePath = "schema.yaml", label = "schema")

        assertEquals(expected = ManifestFile.OnDisk(path = dir.resolve("schema.yaml")), actual = file)
    }

    private fun writeProject(): Path {
        val dir = Files.createTempDirectory("manifest-source-test")
        dir.resolve("manifest.yaml").writeText(text = manifest())
        dir.resolve("schema.yaml").writeText(text = SCHEMA_YAML)
        dir.resolve("actions.yaml").writeText(text = ACTIONS_YAML)
        return dir
    }
}
