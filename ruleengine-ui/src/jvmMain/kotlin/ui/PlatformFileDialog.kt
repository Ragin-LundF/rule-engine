package ui

import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asSkiaBitmap
import org.jetbrains.skia.EncodedImageFormat
import java.awt.FileDialog
import java.awt.Frame
import java.awt.Toolkit
import java.awt.datatransfer.StringSelection
import java.io.File
import java.io.FilenameFilter
import org.jetbrains.skia.Image as SkiaImage

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
private val jsonFilter = FilenameFilter { _, n -> n.endsWith(".json") }

// ── Platform implementations ──────────────────────────────────────────────────

actual suspend fun pickSchemaFile(): String? =
    nativeOpen(title = "Open Schema YAML", filter = yamlFilter)?.readText()

actual suspend fun pickRuleFile(): String? =
    nativeOpen(title = "Open Rule File", filter = ruleFilter)?.readText()

actual suspend fun pickActionsFile(): String? =
    nativeOpen(title = "Open Actions YAML", filter = yamlFilter)?.readText()

actual suspend fun pickInputJsonFile(): String? =
    nativeOpen(title = "Open Input JSON", filter = jsonFilter)?.readText()

actual suspend fun pickManifestFile(): Pair<String, String>? {
    val file = nativeOpen(title = "Open Manifest YAML", filter = yamlFilter) ?: return null
    return Pair(file.readText(), file.parent ?: ".")
}

actual fun saveRuleToFile(filename: String, content: String) {
    val file = nativeSave(title = "Save Rule", suggestedName = filename) ?: return
    file.writeText(content)
}

actual fun saveSchemaToFile(filename: String, content: String) {
    val file = nativeSave(title = "Save Schema YAML", suggestedName = filename) ?: return
    file.writeText(content)
}

actual fun saveActionsToFile(filename: String, content: String) {
    val file = nativeSave(title = "Save Actions YAML", suggestedName = filename) ?: return
    file.writeText(content)
}

actual fun saveManifestToFile(filename: String, content: String) {
    val file = nativeSave(title = "Save Manifest YAML", suggestedName = filename) ?: return
    file.writeText(content)
}

actual fun copyToClipboard(text: String) {
    Toolkit.getDefaultToolkit().systemClipboard.setContents(StringSelection(text), null)
}

/**
 * Opens the native save dialog pre-filled with "diagram.png", then encodes
 * [bitmap] as PNG and writes the bytes to the selected file.
 * Does nothing silently if the user cancels the dialog or encoding fails.
 */
fun saveDiagramAsPng(bitmap: ImageBitmap) {
    val file = nativeSave(title = "Export Diagram as PNG", suggestedName = "diagram.png") ?: return
    val skiaImage = SkiaImage.makeFromBitmap(bitmap.asSkiaBitmap())
    val data = skiaImage.encodeToData(format = EncodedImageFormat.PNG, quality = 100) ?: return
    file.writeBytes(data.bytes)
}

