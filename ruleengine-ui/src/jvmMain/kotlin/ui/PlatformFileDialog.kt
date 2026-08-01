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
import java.nio.file.Path
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

actual suspend fun pickRuleFile(): String? =
    nativeOpen(title = "Open Rule File", filter = ruleFilter)?.readText()

actual suspend fun pickInputJsonFile(): String? =
    nativeOpen(title = "Open Input JSON", filter = jsonFilter)?.readText()

// ── Project dialogs ───────────────────────────────────────────────────────────
//
// These return the chosen Path and read or write nothing themselves: a project touches several
// files, and the loader and saver own that so a half-written project can be reported as such.

/** Picks the manifest of an existing project. Its parent directory is the project root. */
fun pickProjectManifestPath(): Path? =
    nativeOpen(title = "Open Project Manifest", filter = yamlFilter)?.toPath()

/**
 * Asks where to put the manifest of a project that has never been saved.
 *
 * The user chooses the location and may rename the file; whichever name they pick becomes the
 * project's manifest name. The parent directory becomes the project root, and `rules/` and
 * `schemas/` are created inside it by the saver without asking.
 */
fun pickProjectManifestSavePath(suggestedName: String = "manifest.yaml"): Path? =
    nativeSave(title = "Save Project Manifest", suggestedName = suggestedName)?.toPath()

/** Picks a schema file to link. The path matters, not just the content — it goes into the manifest. */
fun pickSchemaFilePath(): Path? =
    nativeOpen(title = "Link Schema YAML", filter = yamlFilter)?.toPath()

/** As [pickSchemaFilePath], for the actions file. */
fun pickActionsFilePath(): Path? =
    nativeOpen(title = "Link Actions YAML", filter = yamlFilter)?.toPath()

/** Picks where to export a schema or actions file so several projects can share it. */
fun pickSharedFileSavePath(title: String, suggestedName: String): Path? =
    nativeSave(title = title, suggestedName = suggestedName)?.toPath()

actual fun copyToClipboard(text: String) {
    Toolkit.getDefaultToolkit().systemClipboard.setContents(StringSelection(text), null)
}

/**
 * Opens the native save dialog and writes [bytes] to the chosen file.
 *
 * Returns the file name written, or null when the user cancels — the caller needs to tell those two
 * apart to report "Exported to x.docx" rather than claiming success for a dialog that was dismissed.
 */
fun saveBytesToFile(title: String, suggestedName: String, bytes: ByteArray): String? {
    val file = nativeSave(title = title, suggestedName = suggestedName) ?: return null
    file.writeBytes(bytes)

    return file.name
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

