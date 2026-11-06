package ruleengine.intellij.manifest.model

/**
 * Persisted rule → manifest links, keyed and valued by file URL.
 *
 * Mutable because IntelliJ's XML serializer writes into the bean it is given.
 */
class ManifestLinkState {
    var links: MutableMap<String, String> = mutableMapOf()
}
