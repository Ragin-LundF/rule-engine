package ui.builder.components.path

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import ui.AccentOrange
import ui.BgInput
import ui.BorderColor
import ui.PrimaryBlueLight
import ui.TextSecondary
import ui.builder.OperandRules
import ui.builder.OperatorOptions
import ui.builder.components.dropdown.DropdownSelector
import ui.builder.components.dropdown.UNKNOWN_MARKER
import ui.builder.components.row.FilterConditionRow
import ui.builder.components.row.PlainTextField
import ui.builder.model.BuilderPathDecoration
import ui.builder.model.BuilderPathStep
import ui.builder.model.catalog.CatalogFieldInfo
import ui.builder.model.filter
import ui.builder.model.filters
import ui.builder.model.namesAField
import ui.builder.model.slice
import ui.builder.model.sort
import ui.builder.model.withFilters
import ui.builder.model.withSegmentName
import ui.builder.model.withSlice
import ui.builder.model.withSort
import ui.builder.model.withoutSegment
import ui.components.TinyButton

/** Widest the `where` drawer grows to, so its rows stay readable next to a short path. */
private val DrawerMaxWidth = 620.dp

/**
 * A field path as a row of breadcrumb pills, with the restrictions of the selected segment in a
 * drawer below it.
 *
 * Every segment is picked from the members the schema declares at that depth — see
 * [OperandRules.segmentOptions] — so the Builder cannot produce a path the schema does not describe.
 * A path whose root is undeclared (which the engine tolerates on a multi-segment path) is shown
 * read-only and marked rather than rewritten, and the caption points at the Schema editor.
 *
 * Depth is unbounded: pills, drawer and warning are all derived from the path list.
 */
// 70 lines against a threshold of 60. The body is one FlowRow of pills followed by the filter
// drawer for whichever pill is selected; there is no second concern in here to lift out, and a
// composable per pill-and-drawer pair would be read by jumping between two declarations instead of
// one. The parts that did decompose — the pill itself and its option menu — are already their own
// composables.
@Suppress("LongMethod")
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun PathBreadcrumb(
    path: List<BuilderPathStep>,
    fields: List<CatalogFieldInfo>,
    onPathChanged: (List<BuilderPathStep>) -> Unit,
    modifier: Modifier = Modifier,
) {
    var selectedDepth by remember { mutableStateOf<Int?>(value = null) }
    val optionsPerDepth = path.indices.map { depth ->
        OperandRules.segmentOptions(fields = fields, path = path, depth = depth)
            .flatMap { field -> listOfNotNull(field.id, field.alias) }
    }
    // Only the first break matters: every segment below an undeclared one has nothing to offer.
    val firstUnknown = path.indices.firstOrNull { depth -> path[depth].name !in optionsPerDepth[depth] }

    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(space = 10.dp)) {
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(space = 6.dp),
            verticalArrangement = Arrangement.spacedBy(space = 4.dp),
        ) {
            path.forEachIndexed { depth, step ->
                if (depth > 0) PathSeparator()

                PathSegmentPill(
                    name = step.name,
                    options = optionsPerDepth[depth],
                    filterCount = step.filters.count { filter -> filter.namesAField },
                    sliced = step.slice != null,
                    ordered = step.sort != null,
                    selected = selectedDepth == depth,
                    onNameSelected = { name ->
                        if (name != step.name) {
                            onPathChanged(path.withSegmentName(depth = depth, name = name))
                            selectedDepth = depth
                        }
                    },
                    onSelected = { selectedDepth = if (selectedDepth == depth) null else depth },
                    // The first segment is the collection itself and cannot be dropped.
                    onRemove = if (depth == 0) {
                        null
                    } else {
                        {
                            onPathChanged(path.withoutSegment(depth = depth))
                            selectedDepth = null
                        }
                    },
                )
            }

            if (OperandRules.canAppendSegment(fields = fields, path = path)) {
                PathSeparator()
                AppendSegmentPill(
                    onClick = {
                        val next = OperandRules
                            .segmentOptions(fields = fields, path = path, depth = path.size)
                            .firstOrNull()?.id ?: return@AppendSegmentPill
                        onPathChanged(path + BuilderPathStep(name = next))
                        selectedDepth = path.size
                    },
                )
            }
        }

        val depth = selectedDepth
        val step = depth?.let { path.getOrNull(index = it) }
        if (depth != null && step != null) {
            WhereDrawer(
                step = step,
                fieldOptions = OperandRules.filterFieldOptions(fields = fields, path = path, depth = depth),
                filterFields = OperandRules.filterCatalog(fields = fields, path = path, depth = depth),
                orderable = OperandRules.canSort(fields = fields, path = path, depth = depth),
                onStepChanged = { updated ->
                    onPathChanged(path.toMutableList().also { it[depth] = updated })
                },
            )
        }

        if (firstUnknown != null) {
            Text(
                text = "$UNKNOWN_MARKER '${path[firstUnknown].name}' is not declared in the schema. " +
                    "The rule still evaluates, but the Builder cannot offer members below it — " +
                    "declare it in Schema to edit this path here.",
                style = MaterialTheme.typography.caption,
                color = AccentOrange,
            )
        }
    }
}

@Composable
private fun PathSeparator() {
    Text(
        text = "›",
        style = MaterialTheme.typography.body2,
        color = TextSecondary,
        modifier = Modifier.padding(top = 7.dp),
    )
}

/** Offered only while the leaf is a declared structure, so the path can only grow into the schema. */
@Composable
private fun AppendSegmentPill(onClick: () -> Unit) {
    TinyButton(text = "+", onClick = onClick, modifier = Modifier.padding(top = 3.dp))
}

/**
 * The restrictions of one path segment, e.g. the `[accountType == "CHECKING"]` in
 * `accountData[accountType == "CHECKING"]`.
 *
 * Several restrictions on one segment are `and`-joined, which is what `OperandText` emits. The drawer
 * is titled with the segment it belongs to so a filter is never read against the wrong element.
 */
@Composable
private fun WhereDrawer(
    step: BuilderPathStep,
    fieldOptions: List<CatalogFieldInfo>,
    filterFields: List<CatalogFieldInfo>,
    orderable: Boolean,
    onStepChanged: (BuilderPathStep) -> Unit,
) {
    // Nothing to show for a scalar segment that carries nothing of its own. An orderable segment
    // always has something: a `string_set` declares no members to filter on, and ordering its
    // values is the whole reason the drawer opens on one.
    if (fieldOptions.isEmpty() && step.decorations.isEmpty() && !orderable) return

    Column(
        modifier = Modifier
            .widthIn(max = DrawerMaxWidth)
            .fillMaxWidth()
            .clip(shape = RoundedCornerShape(size = 8.dp))
            .background(color = BgInput)
            .border(width = 1.dp, color = BorderColor, shape = RoundedCornerShape(size = 8.dp))
            .padding(all = 10.dp),
        verticalArrangement = Arrangement.spacedBy(space = 6.dp),
    ) {
        Text(
            text = "where on ${step.name}",
            style = MaterialTheme.typography.caption.copy(fontWeight = FontWeight.SemiBold),
            color = TextSecondary,
            modifier = Modifier.padding(bottom = 2.dp),
        )

        FilterRows(
            step = step,
            fieldOptions = fieldOptions,
            filterFields = filterFields,
            onStepChanged = onStepChanged,
        )

        // A restriction names a member of the element, so it needs declared members to name.
        if (fieldOptions.isNotEmpty()) {
            TinyButton(
                text = if (step.filters.isEmpty()) "⊕ where" else "⊕ and",
                onClick = {
                    onStepChanged(
                        step.withFilters(
                            filters = step.filters + filter(
                                field = fieldOptions.first().id,
                                operator = OperatorOptions.FILTER_OPERATORS.first(),
                                value = "",
                            )
                        )
                    )
                },
            )
        }

        if (orderable) {
            SortRow(step = step, memberOptions = fieldOptions, onStepChanged = onStepChanged)
        }

        SliceRow(step = step, onStepChanged = onStepChanged)
    }
}

/** The segment's restrictions, `and`-joined in the order they were written. */
@Composable
private fun FilterRows(
    step: BuilderPathStep,
    fieldOptions: List<CatalogFieldInfo>,
    filterFields: List<CatalogFieldInfo>,
    onStepChanged: (BuilderPathStep) -> Unit,
) {
    step.filters.forEachIndexed { index, filter ->
        if (index > 0) {
            Text(
                text = "and",
                style = MaterialTheme.typography.caption,
                color = PrimaryBlueLight,
            )
        }
        FilterConditionRow(
            filter = filter,
            fieldOptions = fieldOptions,
            fields = filterFields,
            onFilterChanged = { updated ->
                onStepChanged(
                    step.withFilters(filters = step.filters.toMutableList().also { it[index] = updated })
                )
            },
            onRemove = {
                onStepChanged(step.withFilters(filters = step.filters.filterIndexed { i, _ -> i != index }))
            },
        )
    }
}

/**
 * Which order to read this segment's elements in, and by which member.
 *
 * Shown above the slice because that is where it takes effect and where an author almost always
 * wants it: ordering first and keeping three gives the three largest, while keeping three first
 * puts an arbitrary three in order. [BuilderPathStep.withSort] is what pins it there.
 *
 * The member dropdown is absent when the elements are values that order by themselves — a
 * `string_set`, or a collection whose members the schema does not describe — which is the same
 * distinction `sortBy`'s two- and three-argument forms draw.
 */
@Composable
private fun SortRow(
    step: BuilderPathStep,
    memberOptions: List<CatalogFieldInfo>,
    onStepChanged: (BuilderPathStep) -> Unit,
) {
    val sort = step.sort
    if (sort == null) {
        TinyButton(
            text = "⊕ order by",
            onClick = {
                onStepChanged(
                    step.withSort(
                        sort = BuilderPathDecoration.Sort(
                            member = memberOptions.firstOrNull()?.id,
                            descending = false,
                        )
                    )
                )
            },
        )
        return
    }

    Row(
        horizontalArrangement = Arrangement.spacedBy(space = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = "order by",
            style = MaterialTheme.typography.caption,
            color = TextSecondary,
        )
        if (memberOptions.isNotEmpty()) {
            DropdownSelector(
                selected = sort.member.orEmpty(),
                options = memberOptions.map { member -> member.id },
                onSelected = { member -> onStepChanged(step.withSort(sort = sort.copy(member = member))) },
                modifier = Modifier.width(width = 150.dp),
            )
        }
        DropdownSelector(
            selected = if (sort.descending) DESCENDING_LABEL else ASCENDING_LABEL,
            options = listOf(ASCENDING_LABEL, DESCENDING_LABEL),
            onSelected = { choice ->
                onStepChanged(step.withSort(sort = sort.copy(descending = choice == DESCENDING_LABEL)))
            },
            modifier = Modifier.width(width = 130.dp),
        )
        TinyButton(text = "×", onClick = { onStepChanged(step.withSort(sort = null)) })
    }
}

/** Spelled out rather than as `asc` / `desc`: the row is prose, and the DSL echo shows the call. */
private const val ASCENDING_LABEL: String = "ascending"
private const val DESCENDING_LABEL: String = "descending"

/**
 * How many elements of this segment to keep, and from which end.
 *
 * Shown below the restrictions because that is where it takes effect: the slice sees whatever the
 * restrictions above it left, which is the difference between "failures among the last ten events"
 * and "the last ten failures".
 */
@Composable
private fun SliceRow(step: BuilderPathStep, onStepChanged: (BuilderPathStep) -> Unit) {
    val slice = step.slice
    if (slice == null) {
        TinyButton(
            text = "⊕ first / last n",
            onClick = {
                onStepChanged(
                    step.withSlice(slice = BuilderPathDecoration.Slice(fromEnd = false, count = "10"))
                )
            },
        )
        return
    }

    Row(
        horizontalArrangement = Arrangement.spacedBy(space = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = "keep",
            style = MaterialTheme.typography.caption,
            color = TextSecondary,
        )
        DropdownSelector(
            selected = if (slice.fromEnd) "last" else "first",
            options = listOf("first", "last"),
            onSelected = { choice ->
                onStepChanged(step.withSlice(slice = slice.copy(fromEnd = choice == "last")))
            },
            modifier = Modifier.width(width = 80.dp),
        )
        PlainTextField(
            value = slice.count,
            placeholder = "10",
            onValueChange = { count -> onStepChanged(step.withSlice(slice = slice.copy(count = count))) },
            modifier = Modifier.width(width = 70.dp),
        )
        TinyButton(text = "×", onClick = { onStepChanged(step.withSlice(slice = null)) })
    }
}
