package ui.autocompletion.model
public data class CompletionItem(
    val label: String,
    val insertText: String,
    val kind: CompletionKind,
    val hint: String = "",
)

// Export some small helpers that builder needs
