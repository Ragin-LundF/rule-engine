package ui.builder.inspector

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import ui.AccentOrange
import ui.BgElevated
import ui.BorderColor
import ui.TextSecondary
import ui.builder.OperandRules
import ui.builder.OperandText
import ui.builder.OperatorOptions
import ui.builder.components.dropdown.DropdownSelector
import ui.builder.components.row.PlainTextField
import ui.builder.model.BuilderPathDecoration
import ui.builder.model.BuilderPathStep
import ui.builder.model.catalog.BuilderCatalog
import ui.builder.model.catalog.CatalogFieldInfo
import ui.builder.model.catalog.fieldAtPath
import ui.builder.model.filter
import ui.builder.model.filters
import ui.builder.model.names
import ui.builder.model.namesAField
import ui.builder.model.slice
import ui.builder.model.sort
import ui.builder.model.withFilters
import ui.builder.model.withSegmentName
import ui.builder.model.withSlice
import ui.builder.model.withSort
import ui.builder.model.withoutSegment
import ui.components.TinyButton
import ui.util.replaceAt

/**
 * A field path as a **vertical list of steps**, one card per level.
 *
 * This replaces a horizontal breadcrumb of pills whose selected pill opened a separate `where` drawer.
 * Two things were wrong with that. The drawer was a second place to look, nested inside an operand
 * panel that was itself nested under a row — a fourth level of indentation for the thing the author
 * came to edit. And the decorations were reduced to the glyphs `↕ ⋯ 3` on a closed pill, so a silent
 * filter or a silent truncation was exactly what you had to open a drawer to discover.
 *
 * Here every step states what it carries. A filter, an ordering and a bound are visible on the card
 * that owns them, and the order-of-operations switch is a control rather than a consequence of where a
 * drawer row happened to sit.
 */
@Suppress("FunctionNaming", "LongParameterList")
@Composable
internal fun PathSteps(
    path: List<BuilderPathStep>,
    fields: BuilderCatalog,
    onPathChanged: (List<BuilderPathStep>) -> Unit,
    onDrillFilter: (depth: Int, filterIndex: Int) -> Unit,
    prefix: List<BuilderPathStep> = emptyList(),
    canAppend: Boolean = true,
) {
    InspectorSection(title = "Path", hint = "one step per level, picked from the schema")

    // Resolution runs over the whole path; only [path] is rendered. A segment opened on its own is
    // still the third level of its path, and offering it the schema's top-level fields is what used to
    // report a declared field as undeclared.
    val whole = prefix + path

    path.forEachIndexed { depth, step ->
        val level = prefix.size + depth
        val members = OperandRules.segmentOptions(fields = fields, path = whole, depth = level)
            .flatMap { field -> listOfNotNull(field.id, field.alias) }
        StepCard(
            step = step,
            depth = level,
            members = members,
            declared = fields.fieldAtPath(segments = whole.take(n = level + 1).names) != null,
            orderable = OperandRules.canSort(fields = fields, path = whole, depth = level),
            memberOptions = OperandRules.filterFieldOptions(fields = fields, path = whole, depth = level),
            canRemove = level > 0 && prefix.isEmpty(),
            onStepChanged = { updated ->
                onPathChanged(path.replaceAt(index = depth, value = updated))
            },
            onNameChanged = { name ->
                onPathChanged(path.withSegmentName(depth = depth, name = name))
            },
            onRemove = { onPathChanged(path.withoutSegment(depth = depth)) },
            onDrillFilter = { index -> onDrillFilter(level, index) },
        )
    }

    // Suppressed when a single segment is being edited on its own: its setter replaces that one step,
    // so an appended segment would be generated into the panel and then silently dropped.
    if (canAppend && OperandRules.canAppendSegment(fields = fields, path = whole)) {
        val next = OperandRules
            .segmentOptions(fields = fields, path = whole, depth = whole.size)
            .firstOrNull()
        if (next != null) {
            InspectorActions {
                TinyButton(
                    text = "+ step into ${whole.lastOrNull()?.name.orEmpty()}",
                    onClick = { onPathChanged(path + BuilderPathStep(name = next.id)) },
                )
            }
        }
    }
}

/** One level of the path: its name, and everything applied to it. */
@Suppress("FunctionNaming", "LongParameterList", "LongMethod")
@Composable
private fun StepCard(
    step: BuilderPathStep,
    depth: Int,
    members: List<String>,
    declared: Boolean,
    orderable: Boolean,
    memberOptions: List<CatalogFieldInfo>,
    canRemove: Boolean,
    onStepChanged: (BuilderPathStep) -> Unit,
    onNameChanged: (String) -> Unit,
    onRemove: () -> Unit,
    onDrillFilter: (Int) -> Unit,
) {
    // A bare alias resolves without appearing among the members of its level — it stands for a whole
    // path rather than for one of them. Offering its own spelling keeps the dropdown from flagging a
    // legal identifier as off-list.
    val options = if (declared && step.name !in members) members + step.name else members

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 6.dp)
            .clip(shape = RoundedCornerShape(size = 8.dp))
            .background(color = BgElevated)
            .border(width = 1.dp, color = BorderColor, shape = RoundedCornerShape(size = 8.dp))
            .padding(all = 10.dp),
        verticalArrangement = Arrangement.spacedBy(space = 6.dp),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(space = 6.dp),
        ) {
            Text(
                text = "${depth + 1}",
                style = MaterialTheme.typography.caption.copy(fontWeight = FontWeight.Bold),
                color = TextSecondary,
            )
            if (members.isEmpty()) {
                // A segment below one the schema does not declare has nothing valid to offer, and the
                // name the rule text carried is preserved rather than rewritten.
                Text(
                    text = step.name,
                    style = MaterialTheme.typography.body2,
                    color = AccentOrange,
                    modifier = Modifier.weight(weight = 1f),
                )
            } else {
                DropdownSelector(
                    selected = step.name,
                    options = options,
                    onSelected = onNameChanged,
                    modifier = Modifier.weight(weight = 1f),
                )
            }
            if (canRemove) {
                TinyButton(text = "×", onClick = onRemove)
            }
        }

        if (!declared && members.isNotEmpty()) {
            InspectorNote(
                text = "'${step.name}' is not declared in the schema. The rule still evaluates, but the " +
                    "members below it cannot be offered — declare it in Schema to edit this path here.",
                warning = true,
            )
        }

        StepFilters(
            step = step,
            memberOptions = memberOptions,
            onStepChanged = onStepChanged,
            onDrillFilter = onDrillFilter,
        )

        if (orderable) {
            StepOrdering(step = step, memberOptions = memberOptions, onStepChanged = onStepChanged)
            StepBound(step = step, onStepChanged = onStepChanged)
            StepOrderOfOperations(step = step, onStepChanged = onStepChanged)
        }
    }
}

/** The `where` restrictions of one level, `and`-joined in the order they were written. */
@Suppress("FunctionNaming")
@Composable
private fun StepFilters(
    step: BuilderPathStep,
    memberOptions: List<CatalogFieldInfo>,
    onStepChanged: (BuilderPathStep) -> Unit,
    onDrillFilter: (Int) -> Unit,
) {
    val existing = step.filters
    existing.forEachIndexed { index, current ->
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(space = 6.dp),
        ) {
            Text(
                text = if (index == 0) "where" else "and",
                style = MaterialTheme.typography.caption,
                color = TextSecondary,
                modifier = Modifier.width(width = 44.dp),
            )
            Column(modifier = Modifier.weight(weight = 1f)) {
                InspectorLine(
                    text = "[ ${OperandText.filterToDsl(filter = current)} ]",
                    selected = false,
                    onClick = { onDrillFilter(index) },
                )
            }
            TinyButton(
                text = "×",
                onClick = {
                    onStepChanged(
                        step.withFilters(filters = existing.filterIndexed { i, _ -> i != index }),
                    )
                },
            )
        }
        if (!current.namesAField) {
            InspectorNote(
                text = "This restriction names nothing yet, so it contributes nothing to the rule.",
                warning = true,
            )
        }
    }
    // A restriction names a member of the element, so it needs declared members to name.
    if (memberOptions.isNotEmpty()) {
        InspectorActions {
            TinyButton(
                text = if (existing.isEmpty()) "+ where" else "+ and",
                onClick = {
                    onStepChanged(
                        step.withFilters(
                            filters = existing + filter(
                                field = memberOptions.first().id,
                                operator = OperatorOptions.FILTER_OPERATORS.first(),
                                value = "",
                            ),
                        ),
                    )
                },
            )
        }
    }
}

/** Which order to read this level's elements in — what `sortBy` generates. */
@Suppress("FunctionNaming")
@Composable
private fun StepOrdering(
    step: BuilderPathStep,
    memberOptions: List<CatalogFieldInfo>,
    onStepChanged: (BuilderPathStep) -> Unit,
) {
    val ordering = step.sort
    if (ordering == null) {
        InspectorActions {
            TinyButton(
                text = "+ order by",
                onClick = {
                    onStepChanged(
                        step.withSort(
                            sort = BuilderPathDecoration.Sort(
                                member = memberOptions.firstOrNull()?.id,
                                descending = false,
                            ),
                        ),
                    )
                },
            )
        }
        return
    }
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(space = 6.dp),
    ) {
        Text(
            text = "order by",
            style = MaterialTheme.typography.caption,
            color = TextSecondary,
            modifier = Modifier.width(width = 60.dp),
        )
        if (memberOptions.isNotEmpty()) {
            DropdownSelector(
                selected = ordering.member.orEmpty(),
                options = memberOptions.map { member -> member.id },
                onSelected = { member ->
                    onStepChanged(step.withSort(sort = ordering.copy(member = member)))
                },
                modifier = Modifier.weight(weight = 1f),
            )
        } else {
            // A string_set declares no members; ordering it by its own values is why the drawer opens.
            Text(
                text = "own values",
                style = MaterialTheme.typography.body2,
                color = TextSecondary,
                modifier = Modifier.weight(weight = 1f),
            )
        }
        DropdownSelector(
            selected = if (ordering.descending) "descending" else "ascending",
            options = listOf("ascending", "descending"),
            onSelected = { choice ->
                onStepChanged(step.withSort(sort = ordering.copy(descending = choice == "descending")))
            },
            modifier = Modifier.width(width = 120.dp),
        )
        TinyButton(text = "×", onClick = { onStepChanged(step.withSort(sort = null)) })
    }
}

/** How many elements to keep, and from which end — what `take` / `takeLast` generate. */
@Suppress("FunctionNaming")
@Composable
private fun StepBound(
    step: BuilderPathStep,
    onStepChanged: (BuilderPathStep) -> Unit,
) {
    val bound = step.slice
    if (bound == null) {
        InspectorActions {
            TinyButton(
                text = "+ first / last n",
                onClick = {
                    onStepChanged(
                        step.withSlice(slice = BuilderPathDecoration.Slice(fromEnd = false, count = "10")),
                    )
                },
            )
        }
        return
    }
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(space = 6.dp),
    ) {
        Text(
            text = "keep",
            style = MaterialTheme.typography.caption,
            color = TextSecondary,
            modifier = Modifier.width(width = 60.dp),
        )
        DropdownSelector(
            selected = if (bound.fromEnd) "last" else "first",
            options = listOf("first", "last"),
            onSelected = { choice ->
                onStepChanged(step.withSlice(slice = bound.copy(fromEnd = choice == "last")))
            },
            modifier = Modifier.width(width = 90.dp),
        )
        PlainTextField(
            value = bound.count,
            placeholder = "10",
            onValueChange = { count -> onStepChanged(step.withSlice(slice = bound.copy(count = count))) },
            modifier = Modifier.width(width = 70.dp),
        )
        TinyButton(text = "×", onClick = { onStepChanged(step.withSlice(slice = null)) })
    }
}

/**
 * Filter first, or keep first — stated as a control, because the order *is* the meaning.
 *
 * `take(orders, 3)[paid == true]` selects paid orders among the first three;
 * `take(orders[paid == true], 3)` selects the first three paid orders. Those are different questions,
 * and the old drawer answered whichever one the row's position happened to imply.
 */
@Suppress("FunctionNaming")
@Composable
private fun StepOrderOfOperations(
    step: BuilderPathStep,
    onStepChanged: (BuilderPathStep) -> Unit,
) {
    val bound = step.slice ?: return
    if (step.filters.isEmpty()) {
        return
    }
    val filterFirst = step.decorations.indexOfFirst { it is BuilderPathDecoration.Filter } <
        step.decorations.indexOfFirst { it is BuilderPathDecoration.Slice }

    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(space = 6.dp),
    ) {
        Text(
            text = "order",
            style = MaterialTheme.typography.caption,
            color = TextSecondary,
            modifier = Modifier.width(width = 60.dp),
        )
        DropdownSelector(
            selected = if (filterFirst) "filter, then keep" else "keep, then filter",
            options = listOf("filter, then keep", "keep, then filter"),
            onSelected = { choice ->
                onStepChanged(reorderDecorations(step = step, filterFirst = choice == "filter, then keep"))
            },
            modifier = Modifier.weight(weight = 1f),
        )
    }
    InspectorNote(
        text = if (filterFirst) {
            "\"the ${if (bound.fromEnd) "last" else "first"} ${bound.count} that match\" — ${
                OperandText.pathToDsl(path = listOf(step))
            }"
        } else {
            "\"among the ${if (bound.fromEnd) "last" else "first"} ${bound.count}, the ones that match\" — ${
                OperandText.pathToDsl(path = listOf(step))
            }"
        },
    )
}

/** Rebuilds a step's decorations so the filters sit before or after the bound, as asked. */
private fun reorderDecorations(step: BuilderPathStep, filterFirst: Boolean): BuilderPathStep {
    val filters = step.decorations.filterIsInstance<BuilderPathDecoration.Filter>()
    val sorts = step.decorations.filterIsInstance<BuilderPathDecoration.Sort>()
    val slices = step.decorations.filterIsInstance<BuilderPathDecoration.Slice>()
    // Ordering stays before the bound either way: sorting then keeping three gives the three largest,
    // where keeping three first puts an arbitrary three in order.
    val rebuilt = if (filterFirst) {
        filters + sorts + slices
    } else {
        sorts + slices + filters
    }
    return step.copy(decorations = rebuilt)
}
