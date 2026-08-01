package ui.builder.model.mutable

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.runtime.toMutableStateList

/**
 * A mutable node in the builder condition tree.
 */
sealed interface MutableConditionNode {
    val id: String
    var joinToPrevious: String
    var negated: Boolean

    /** Wraps an existing [MutableBuilderCondition] as a leaf node. */
    class Leaf(val inner: MutableBuilderCondition) : MutableConditionNode {
        override val id: String get() = inner.id
        override var joinToPrevious: String
            get() = inner.joinToPrevious
            set(value) {
                inner.joinToPrevious = value
            }
        override var negated: Boolean
            get() = inner.negated
            set(value) {
                inner.negated = value
            }
    }

    /** Wraps a [MutableBuilderComparison] — the leaf kind that can hold computed operands. */
    class ComparisonLeaf(val inner: MutableBuilderComparison) : MutableConditionNode {
        override val id: String get() = inner.id
        override var joinToPrevious: String
            get() = inner.joinToPrevious
            set(value) {
                inner.joinToPrevious = value
            }
        override var negated: Boolean
            get() = inner.negated
            set(value) {
                inner.negated = value
            }
    }

    /** A parenthesized group of child nodes. */
    class Group(
        override val id: String,
        nodes: List<MutableConditionNode> = emptyList(),
        joinToPrevious: String = "",
        negated: Boolean = false,
    ) : MutableConditionNode {
        val nodes: SnapshotStateList<MutableConditionNode> = nodes.toMutableStateList()
        override var joinToPrevious by mutableStateOf(value = joinToPrevious)
        override var negated by mutableStateOf(value = negated)
    }
}
