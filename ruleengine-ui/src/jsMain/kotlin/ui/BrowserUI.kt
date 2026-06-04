package ui

import kotlinx.browser.document
import kotlinx.browser.window
import org.w3c.dom.HTMLTextAreaElement

fun main() {
    val root = document.getElementById("root") ?: run {
        val r = document.createElement("div")
        r.id = "root"
        document.body?.appendChild(r)
        r
    }

    val html = """
        <h1>Rule Editor (Web)</h1>
        <div>
            <h3>Schema</h3>
            <textarea id="schema" style="width:100%;height:150px"></textarea>
            <br/>
            <button id="loadSchema">Load (paste)</button>
            <button id="clearSchema">Clear</button>
        </div>
        <div>
            <h3>Rule</h3>
            <textarea id="rule" style="width:100%;height:200px"></textarea>
            <br/>
            <button id="loadRule">Load (paste)</button>
            <button id="saveRule">Save</button>
            <button id="copyRule">Copy</button>
        </div>
        <div id="status">Status: ready</div>
    """.trimIndent()

    root.innerHTML = html

    val loadSchemaBtn = document.getElementById("loadSchema")
    loadSchemaBtn?.addEventListener("click", {
        val v = window.prompt("Paste schema YAML content")
        if (v != null) (document.getElementById("schema") as? HTMLTextAreaElement)?.value = v
    })

    val clearSchemaBtn = document.getElementById("clearSchema")
    clearSchemaBtn?.addEventListener("click", { (document.getElementById("schema") as? HTMLTextAreaElement)?.value = "" })

    val loadRuleBtn = document.getElementById("loadRule")
    loadRuleBtn?.addEventListener("click", {
        val v = window.prompt("Paste rule content")
        if (v != null) (document.getElementById("rule") as? HTMLTextAreaElement)?.value = v
    })

    val saveRuleBtn = document.getElementById("saveRule")
    saveRuleBtn?.addEventListener("click", {
        val content = (document.getElementById("rule") as? HTMLTextAreaElement)?.value ?: ""
        val blob = js("new Blob([content], { type: 'text/plain' })")
        val url = js("URL.createObjectURL(blob)") as String
        val a = document.createElement("a") as org.w3c.dom.HTMLAnchorElement
        a.href = url
        a.download = "rule.rule"
        a.style.display = "none"
        document.body?.appendChild(a)
        a.click()
        a.remove()
        val jsURL = js("URL")
        jsURL.revokeObjectURL(url)
    })

    val copyRuleBtn = document.getElementById("copyRule")
    copyRuleBtn?.addEventListener("click", {
        val text = (document.getElementById("rule") as? HTMLTextAreaElement)?.value ?: ""
        try {
            js("navigator.clipboard.writeText(text)")
        } catch (e: dynamic) {
            window.alert("Clipboard not available - please copy manually")
        }
    })
}

