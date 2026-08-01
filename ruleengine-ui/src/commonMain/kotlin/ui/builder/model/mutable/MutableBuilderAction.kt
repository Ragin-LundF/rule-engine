package ui.builder.model.mutable

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.runtime.toMutableStateList
import ui.builder.model.BuilderAction

/**
 * Mutable editor state for a single action row in Builder mode.
 */
class MutableBuilderAction(
    val id: String,
    name: String,
    arguments: List<String> = emptyList(),
) {
    var name by mutableStateOf(value = name)
    val arguments: SnapshotStateList<String> = arguments.toMutableStateList()

    fun toImmutable(): BuilderAction = BuilderAction(
        id = id,
        name = name,
        arguments = arguments.toList(),
    )
}
