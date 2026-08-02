package ui.builder.model.mutable

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.runtime.toMutableStateList
import ui.builder.model.BuilderAction
import ui.builder.model.BuilderExtraction

/**
 * Mutable editor state for a single action row in Builder mode.
 */
class MutableBuilderAction(
    val id: String,
    name: String,
    arguments: List<String> = emptyList(),
    extraction: BuilderExtraction? = null,
) {
    var name by mutableStateOf(value = name)
    val arguments: SnapshotStateList<String> = arguments.toMutableStateList()

    /**
     * Replaced whole rather than mutated field by field, the same way an operand is: three
     * `mutableStateOf` slots for source/pattern/group would be three recompositions for one edit, and
     * null-versus-absent is the state the row actually branches on.
     */
    var extraction by mutableStateOf(value = extraction)

    fun toImmutable(): BuilderAction = BuilderAction(
        id = id,
        name = name,
        arguments = arguments.toList(),
        extraction = extraction,
    )
}
