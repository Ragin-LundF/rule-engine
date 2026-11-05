package ui

import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asSkiaBitmap
import org.jetbrains.skia.EncodedImageFormat
import ui.project.io.AtomicFiles
import java.awt.Toolkit
import java.awt.datatransfer.StringSelection
import java.nio.file.Path
import org.jetbrains.skia.Image as SkiaImage

// Getting bytes *out* of the workbench: the clipboard, an arbitrary file, and the diagram PNG.
// Separate from the file *pickers* because these write rather than read, and only these need
// image and byte handling.

/** Picks where to export a schema or actions file so several projects can share it. */
fun pickSharedFileSavePath(title: String, suggestedName: String): Path? {
    return nativeSave(title = title, suggestedName = suggestedName)?.toPath()
}

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
    AtomicFiles.write(path = file.toPath(), bytes = bytes)

    return file.name
}

/**
 * Opens the native save dialog pre-filled with "diagram.png", then encodes [bitmap] as PNG and writes
 * the bytes to the selected file.
 *
 * Returns the file name written, or null when the user cancels; a failed encoding throws, so the caller
 * reports it instead of claiming an export that never happened.
 */
fun saveDiagramAsPng(bitmap: ImageBitmap): String? {
    val file = nativeSave(title = "Export Diagram as PNG", suggestedName = "diagram.png") ?: return null
    val skiaImage = SkiaImage.makeFromBitmap(bitmap.asSkiaBitmap())
    val data = checkNotNull(value = skiaImage.encodeToData(format = EncodedImageFormat.PNG, quality = 100)) {
        "the diagram could not be encoded as PNG"
    }
    AtomicFiles.write(path = file.toPath(), bytes = data.bytes)
    return file.name
}
