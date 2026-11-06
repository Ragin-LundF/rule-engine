package ruleengine.intellij.diagram

import androidx.compose.runtime.mutableStateOf
import com.intellij.openapi.Disposable
import com.intellij.openapi.editor.EditorFactory
import com.intellij.openapi.editor.event.CaretEvent
import com.intellij.openapi.editor.event.CaretListener
import com.intellij.openapi.editor.event.DocumentEvent
import com.intellij.openapi.editor.event.DocumentListener
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.fileEditor.FileEditorManagerEvent
import com.intellij.openapi.fileEditor.FileEditorManagerListener
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.ui.JBColor
import com.intellij.util.Alarm
import org.jetbrains.jewel.bridge.JewelComposePanel
import ruleengine.dsl.ast.rule.RuleAst
import ruleengine.dsl.parser.Parser
import ruleengine.intellij.diagram.model.DiagramViewState
import ruleengine.intellij.lang.RuleFileType
import ui.theme.ThemeController
import javax.swing.JComponent

/**
 * Keeps the diagram on the rule at the caret of the selected rule editor.
 *
 * Re-parses shortly after the caret moves or the text changes. While the rule does not parse, the
 * last version that did stays on screen with the parse error above it.
 */
class RuleDiagramPanel(private val project: Project, parentDisposable: Disposable) {
    private val state = mutableStateOf(DiagramViewState(message = NO_RULE_FILE))
    private val alarm = Alarm(Alarm.ThreadToUse.SWING_THREAD, parentDisposable)
    private var lastGood: Pair<VirtualFile, RuleAst>? = null

    val component: JComponent = JewelComposePanel { RuleDiagramView(state = state.value) }

    init {
        project.messageBus.connect(parentDisposable).subscribe(
            FileEditorManagerListener.FILE_EDITOR_MANAGER,
            object : FileEditorManagerListener {
                override fun selectionChanged(event: FileEditorManagerEvent) {
                    scheduleUpdate()
                }
            },
        )
        val multicaster = EditorFactory.getInstance().eventMulticaster
        multicaster.addCaretListener(object : CaretListener {
            override fun caretPositionChanged(event: CaretEvent) {
                scheduleUpdate()
            }
        }, parentDisposable)
        multicaster.addDocumentListener(object : DocumentListener {
            override fun documentChanged(event: DocumentEvent) {
                scheduleUpdate()
            }
        }, parentDisposable)
        scheduleUpdate()
    }

    private fun scheduleUpdate() {
        alarm.cancelAllRequests()
        alarm.addRequest(::update, UPDATE_DELAY_MS)
    }

    private fun update() {
        ThemeController.isDark = !JBColor.isBright()
        val editor = FileEditorManager.getInstance(project).selectedTextEditor
        val file = editor?.let { FileDocumentManager.getInstance().getFile(it.document) }
        if (editor == null || file?.fileType != RuleFileType) {
            return
        }
        val ruleText = RuleSegments.ruleTextAt(text = editor.document.charsSequence, offset = editor.caretModel.offset)
        state.value = if (ruleText == null) {
            DiagramViewState(message = NO_RULE)
        } else {
            stateOf(file = file, ruleText = ruleText)
        }
    }

    private fun stateOf(file: VirtualFile, ruleText: String): DiagramViewState {
        return runCatching { Parser(input = ruleText).parseRules().first() }
            .map { rule ->
                lastGood = file to rule
                DiagramViewState(rule = rule)
            }
            .getOrElse { error ->
                val previous = lastGood?.takeIf { (lastFile, _) -> lastFile == file }?.second
                val reason = error.message ?: error.javaClass.simpleName
                if (previous == null) {
                    DiagramViewState(message = "The rule does not parse yet: $reason")
                } else {
                    DiagramViewState(rule = previous, message = "Showing the last version that parsed. Now: $reason")
                }
            }
    }

    private companion object {
        const val UPDATE_DELAY_MS = 300
        const val NO_RULE_FILE = "Open a rule file to see the rule at the caret."
        const val NO_RULE = "This file holds no rule yet."
    }
}
