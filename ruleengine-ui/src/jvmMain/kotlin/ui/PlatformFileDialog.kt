package ui

import java.awt.FileDialog
import java.awt.Frame
import java.io.File
import java.io.FilenameFilter
import java.nio.file.Files
import java.awt.Toolkit
import java.awt.datatransfer.StringSelection

// ── Global last-used directory (persists for the whole app session) ───────────
private var lastDirectory: String? = null

// ── Internal helpers ──────────────────────────────────────────────────────────

/**
 * Shows the native OS open-file dialog (macOS sheet / AWT dialog on other OS).
 * Remembers and restores the last visited directory automatically.
 */
private fun nativeOpen(title: String, filter: FilenameFilter? = null): File? {
    val dlg = FileDialog(null as Frame?, title, FileDialog.LOAD)
    filter?.let { dlg.filenameFilter = it }
    lastDirectory?.let { dlg.directory = it }
    dlg.isVisible = true                        // blocks until closed
    val name = dlg.file ?: return null
    lastDirectory = dlg.directory
    return File(dlg.directory, name)
}

/**
 * Shows the native OS save-file dialog.
 * Remembers and restores the last visited directory automatically.
 */
private fun nativeSave(title: String, suggestedName: String): File? {
    val dlg = FileDialog(null as Frame?, title, FileDialog.SAVE)
    dlg.file = suggestedName
    lastDirectory?.let { dlg.directory = it }
    dlg.isVisible = true
    val name = dlg.file ?: return null
    lastDirectory = dlg.directory
    return File(dlg.directory, name)
}

private val yamlFilter = FilenameFilter { _, n -> n.endsWith(".yaml") || n.endsWith(".yml") }
private val ruleFilter = FilenameFilter { _, n -> n.endsWith(".rule") }

// ── Platform implementations ──────────────────────────────────────────────────

actual suspend fun pickSchemaFile(): String? =
    nativeOpen("Open Schema YAML", yamlFilter)?.readText()

actual suspend fun pickRuleFile(): String? =
    nativeOpen("Open Rule File", ruleFilter)?.readText()

actual suspend fun pickManifestFile(): Pair<String, String>? {
    val file = nativeOpen("Open Manifest YAML", yamlFilter) ?: return null
    return Pair(file.readText(), file.parent ?: ".")
}

actual fun saveRuleToFile(filename: String, content: String) {
    val file = nativeSave("Save Rule", filename) ?: return
    file.writeText(content)
}

actual fun saveSchemaToFile(filename: String, content: String) {
    val file = nativeSave("Save Schema YAML", filename) ?: return
    file.writeText(content)
}

actual fun saveActionsToFile(filename: String, content: String) {
    val file = nativeSave("Save Actions YAML", filename) ?: return
    file.writeText(content)
}

actual fun copyToClipboard(text: String) {
    Toolkit.getDefaultToolkit().systemClipboard.setContents(StringSelection(text), null)
}

