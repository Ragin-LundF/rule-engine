package ruleengine.builder

import java.net.URL
import java.util.Enumeration

/**
 * A loader that serves [resources] through `getResourceAsStream` and refuses to hand out a [URL].
 *
 * This is the stand-in for Spring Boot's `LaunchedClassLoader`, which is what makes it valuable: it
 * pins the one property a nested-jar loader needs. Boot resolves `BOOT-INF/classes` to a
 * `jar:nested:…` URL that no `FileSystemProvider` understands, so any load path that reaches for a
 * `URL` or a `Path` fails there. Failing loudly here is cheaper than depending on Spring Boot to find
 * that out.
 */
internal class ResourceStreamOnlyClassLoader(
    private val resources: Map<String, String>,
) : ClassLoader(null) {

    override fun getResourceAsStream(name: String): java.io.InputStream? {
        return resources[name]?.byteInputStream(charset = Charsets.UTF_8)
    }

    override fun getResource(name: String): URL {
        throw AssertionError("the loader must not be asked for a URL, but was asked for '$name'")
    }

    override fun findResource(name: String): URL {
        throw AssertionError("the loader must not be asked for a URL, but was asked for '$name'")
    }

    override fun getResources(name: String): Enumeration<URL> {
        throw AssertionError("the loader must not be asked for URLs, but was asked for '$name'")
    }
}
