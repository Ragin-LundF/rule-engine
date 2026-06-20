package ui.builder

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.width
import androidx.compose.material.OutlinedTextField
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/**
 * Inline value editor composables for Builder mode condition rows.
 *
 * Each editor writes directly into [MutableBuilderCondition] fields so that
 * [BuilderToRuleDsl.generate] can pick up the changes immediately.
 */

/** Renders the appropriate value editor based on the current operator. */
@Composable
fun ConditionValueEditor(
    condition: MutableBuilderCondition,
    modifier: Modifier = Modifier,
) {
    when {
        OperatorOptions.isBetween(condition.operator) -> BetweenEditor(condition = condition, modifier = modifier)
        OperatorOptions.isList(condition.operator) -> ListEditor(condition = condition, modifier = modifier)
        else -> SingleValueEditor(condition = condition, modifier = modifier)
    }
}

/** Single text/number input for simple operators. */
@Composable
fun SingleValueEditor(
    condition: MutableBuilderCondition,
    modifier: Modifier = Modifier,
) {
    OutlinedTextField(
        value = condition.value,
        onValueChange = { condition.value = it },
        singleLine = true,
        label = { Text("value") },
        modifier = modifier.width(160.dp),
    )
}

/** Two inputs for the `between` operator (low .. high). */
@Composable
fun BetweenEditor(
    condition: MutableBuilderCondition,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        OutlinedTextField(
            value = condition.value,
            onValueChange = { condition.value = it },
            singleLine = true,
            label = { Text("from") },
            modifier = Modifier.width(100.dp),
        )
        Text("…")
        OutlinedTextField(
            value = condition.valueTo,
            onValueChange = { condition.valueTo = it },
            singleLine = true,
            label = { Text("to") },
            modifier = Modifier.width(100.dp),
        )
    }
}

/**
 * Comma-separated list editor for `in` / `containsAny` / `containsAll` operators.
 * Items are stored in [MutableBuilderCondition.listItems]; the text field shows them
 * joined by ", " and re-parses on every change.
 */
@Composable
fun ListEditor(
    condition: MutableBuilderCondition,
    modifier: Modifier = Modifier,
) {
    val text = condition.listItems.joinToString(", ")
    OutlinedTextField(
        value = text,
        onValueChange = { raw ->
            val items = raw.split(",").map { it.trim() }.filter { it.isNotEmpty() }
            condition.listItems.clear()
            condition.listItems.addAll(items)
        },
        singleLine = true,
        label = { Text("values (comma-separated)") },
        modifier = modifier.width(240.dp),
    )
}
