package ui

import kotlinx.browser.window
import kotlinx.coroutines.await
import org.w3c.dom.url.URL
import org.w3c.files.File
import org.w3c.dom.HTMLAnchorElement

actual suspend fun pickSchemaFile(): String? {
    val res = window.prompt("Paste schema YAML content here (or cancel)")
    return res
}

actual suspend fun pickRuleFile(): String? {
    val res = window.prompt("Paste rule content here (or cancel)")
    return res
}

actual suspend fun pickManifestFile(): Pair<String, String>? {
    val res = window.prompt("Paste manifest YAML content here (or cancel)")
    return if (res != null) Pair(res, ".") else null
}

actual fun saveRuleToFile(filename: String, content: String) {
    // create blob and download
    val blob = js("new Blob([content], { type: 'text/plain' })")
    val url = js("URL.createObjectURL(blob)") as String
    val a = window.document.createElement("a") as HTMLAnchorElement
    a.href = url
    a.download = filename
    a.style.display = "none"
    window.document.body?.appendChild(a)
    a.click()
    a.remove()
    val jsURL = js("URL")
    jsURL.revokeObjectURL(url)
}

actual fun copyToClipboard(text: String) {
    try {
        val nav = js("navigator")
        if (nav.clipboard != undefined) {
            js("navigator.clipboard.writeText(text)")
        } else {
            window.alert("Clipboard not available - please copy manually")
        }
    } catch (ex: dynamic) {
        // ignore
    }
}

