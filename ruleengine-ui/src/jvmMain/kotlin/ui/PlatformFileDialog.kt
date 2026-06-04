package ui

import java.io.File
import javax.swing.JFileChooser
import javax.swing.filechooser.FileNameExtensionFilter
import java.nio.file.Files
import java.nio.file.Path
import java.awt.Toolkit
import java.awt.datatransfer.StringSelection

actual suspend fun pickSchemaFile(): String? {
    val chooser = JFileChooser()
    chooser.dialogTitle = "Select schema YAML file"
    chooser.fileFilter = FileNameExtensionFilter("YAML files", "yaml", "yml")
    val res = chooser.showOpenDialog(null)
    if (res == JFileChooser.APPROVE_OPTION) {
        val f = chooser.selectedFile.toPath()
        return Files.readString(f)
    }
    return null
}

actual suspend fun pickRuleFile(): String? {
    val chooser = JFileChooser()
    chooser.dialogTitle = "Select rule file"
    chooser.fileFilter = FileNameExtensionFilter("Rule files", "rule")
    val res = chooser.showOpenDialog(null)
    if (res == JFileChooser.APPROVE_OPTION) {
        val f = chooser.selectedFile.toPath()
        return Files.readString(f)
    }
    return null
}

actual fun saveRuleToFile(filename: String, content: String) {
    // show save dialog
    val chooser = JFileChooser()
    chooser.dialogTitle = "Save rule to file"
    chooser.selectedFile = File(filename)
    val res = chooser.showSaveDialog(null)
    if (res == JFileChooser.APPROVE_OPTION) {
        val f = chooser.selectedFile.toPath()
        Files.writeString(f, content)
    }
}

actual fun copyToClipboard(text: String) {
    val sel = StringSelection(text)
    Toolkit.getDefaultToolkit().systemClipboard.setContents(sel, null)
}

