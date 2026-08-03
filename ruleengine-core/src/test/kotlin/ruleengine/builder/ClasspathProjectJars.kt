package ruleengine.builder

import java.net.URLClassLoader
import java.nio.file.Files
import java.nio.file.Path
import java.util.jar.JarEntry
import java.util.jar.JarOutputStream

/**
 * Test support for loading a rule project out of a real jar.
 *
 * A jar is used rather than `src/test/resources` on purpose: an exploded resource directory also has a
 * working `Path`, so it cannot prove that nothing on the load path needs one.
 */
internal object ClasspathProjectJars {
    /**
     * Runs [block] with a loader that can see [entries] and nothing else.
     *
     * `parent = null` matters: without it the test classpath and the working directory could satisfy a
     * lookup, and a green assertion would prove nothing about the jar. Closing the loader matters too —
     * an open one keeps the JDK's cached `JarFile` handle alive, which prevents deleting the temp jar
     * on Windows.
     */
    fun <T> withJarClassLoader(entries: Map<String, String>, block: (ClassLoader) -> T): T {
        val jar = writeJar(entries = entries)
        return try {
            URLClassLoader(arrayOf(jar.toUri().toURL()), null).use { loader -> block(loader) }
        } finally {
            Files.deleteIfExists(jar)
        }
    }

    private fun writeJar(entries: Map<String, String>): Path {
        val jar = Files.createTempFile("rule-engine-classpath", ".jar")
        JarOutputStream(Files.newOutputStream(jar)).use { out ->
            entries.forEach { (name, content) ->
                out.putNextEntry(JarEntry(name))
                out.write(content.toByteArray(Charsets.UTF_8))
                out.closeEntry()
            }
        }
        return jar
    }

    val SCHEMA_YAML: String = """
        schema: classpath-test
        fields:
          p:
            type: text
            operators:
              - equals
    """.trimIndent()

    val ACTIONS_YAML: String = """
        actions:
          label:
            argTypes: [string]
    """.trimIndent()

    fun ruleFile(id: String): String {
        return """
            rule "$id" {
              description "Matches the fixed test input so a warning-free build can be asserted."
              when
                p equals "x"
              then
                label "$id"
            }
        """.trimIndent()
    }

    fun manifest(schema: String = "schema.yaml", rules: List<String> = listOf("rules/a.rule")): String {
        return buildString {
            appendLine("name: classpath-test")
            appendLine("entries:")
            appendLine("  - id: e")
            appendLine("    schema: $schema")
            appendLine("    actions: actions.yaml")
            appendLine("    rules:")
            rules.forEach { appendLine("      - $it") }
        }
    }

    /** The canonical project, with the manifest one directory down so `..` has something to escape. */
    fun nestedProject(): Map<String, String> {
        return mapOf(
            "rules/manifest.yaml" to manifest(),
            "rules/schema.yaml" to SCHEMA_YAML,
            "rules/actions.yaml" to ACTIONS_YAML,
            "rules/rules/a.rule" to ruleFile(id = "a"),
        )
    }
}
