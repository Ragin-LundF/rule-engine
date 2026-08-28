package ui.workbench.inspector

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import ruleengine.core.domain.dto.field.FieldType
import ruleengine.core.domain.dto.field.isNormalizable
import ruleengine.core.domain.dto.field.isStructure
import ruleengine.core.domain.dto.field.isTemporal
import ui.components.input.OrderedListEditor
import ui.components.input.ReasonedChipRow
import ui.components.input.model.OrderedListOption
import ui.components.input.model.ReasonedChip
import ui.schema.KnownNormalizers
import ui.schema.model.EditableField
import ui.schema.operatorsFor
import ui.schema.yamlValue
import ui.util.Plurals

/**
 * The editing surface for one schema field.
 *
 * It used to be a read-only summary while the table beside it did the editing, which was the wrong way
 * round in both directions: the table could not be *read* — every cell was a live text box — and the
 * panel with room for an explanation had nothing to explain.
 *
 * Two of the fixes here are correctness rather than layout:
 *
 * - **normalizers are a chain, so they are edited as one.** `NormalizerRegistry.applyAll` applies them
 *   left to right, and its own documentation gives the counter-example. The chip row this replaces left
 *   the order as whatever order the boxes happened to be ticked in — invisible, and unsettable.
 * - **an operator the type forbids stays visible and says why.** The old selector appended it to the row
 *   with a bare `⚠` and no explanation. It may be in the file legitimately — someone wrote it, or the
 *   type changed under it — so hiding it would let the editor disagree with the schema on disk.
 */
@Suppress("FunctionNaming", "LongParameterList")
@Composable
fun FieldInspector(
    /** The dotted path, which is how the selection names it and how a rule reads it. */
    dottedPath: String,
    field: EditableField,
    onFieldChange: (EditableField) -> Unit,
    modifier: Modifier = Modifier,
    /** How many loaded rules read this field. Null while nothing has been parsed to count. */
    usages: Int? = null,
    editable: Boolean = true,
    /** Selects a member, which is how depth stays navigation rather than nesting. */
    onSelectMember: ((String) -> Unit)? = null,
) {
    BoxWithConstraints(modifier = modifier) {
        // The panel is draggable from 260dp to 720dp, so it cannot be laid out for one width. Switched
        // on the panel's own measurement, never the window's.
        val wide = maxWidth >= WIDE_FROM

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(state = rememberScrollState())
                .padding(horizontal = if (wide) 18.dp else 12.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(space = 4.dp),
        ) {
            InspectorHeading(title = dottedPath, kind = "Field")

            IdentitySection(field = field, editable = editable, wide = wide, onFieldChange = onFieldChange)
            TypeSection(field = field, editable = editable, onFieldChange = onFieldChange)
            FormatSection(field = field, editable = editable, wide = wide, onFieldChange = onFieldChange)
            NormalizerSection(field = field, editable = editable, onFieldChange = onFieldChange)
            OperatorSection(field = field, editable = editable, onFieldChange = onFieldChange)
            MemberSection(
                dottedPath = dottedPath,
                field = field,
                editable = editable,
                onFieldChange = onFieldChange,
                onSelectMember = onSelectMember,
            )

            UsageSection(usages = usages)
        }
    }
}

@Suppress("FunctionNaming")
@Composable
private fun IdentitySection(
    field: EditableField,
    editable: Boolean,
    wide: Boolean,
    onFieldChange: (EditableField) -> Unit,
) {
    InspectorGroup(title = "Identity")
    InspectorTextField(
        label = "path",
        value = field.path,
        placeholder = "field",
        enabled = editable,
        wide = wide,
        onValueChange = { text -> onFieldChange(field.copy(path = text)) },
    )
    InspectorTextField(
        label = "alias",
        value = field.alias,
        placeholder = "(optional)",
        enabled = editable,
        wide = wide,
        onValueChange = { text -> onFieldChange(field.copy(alias = text)) },
    )
    InspectorNote(text = "An alias is a second name a rule may use for this field.")
}

@Suppress("FunctionNaming")
@Composable
private fun FormatSection(
    field: EditableField,
    editable: Boolean,
    wide: Boolean,
    onFieldChange: (EditableField) -> Unit,
) {
    if (!field.type.isTemporal) return
    InspectorGroup(title = "Format")
    InspectorTextField(
        label = "pattern",
        value = field.format,
        placeholder = if (field.type == FieldType.DATE_TIME) "dd.MM.yyyy HH:mm" else "dd.MM.yyyy",
        enabled = editable,
        wide = wide,
        onValueChange = { text -> onFieldChange(field.copy(format = text)) },
    )
    InspectorNote(text = "Empty means ISO-8601.")
}

@Suppress("FunctionNaming")
@Composable
private fun UsageSection(usages: Int?) {
    val count = usages ?: return
    InspectorGroup(title = "Read by", note = "$count rule${Plurals.suffix(count = count)}")
    if (count == 0) {
        InspectorNote(
            text = "No loaded rule reads this field. Allowed — the schema describes the document, not " +
                "the rules — but usually a rename that happened on one side only.",
            warning = true,
        )
    }
}

@Suppress("FunctionNaming")
@Composable
private fun TypeSection(
    field: EditableField,
    editable: Boolean,
    onFieldChange: (EditableField) -> Unit,
) {
    InspectorGroup(title = "Type")
    ReasonedChipRow(
        chips = FieldType.entries.map { type ->
            ReasonedChip(value = type.yamlValue, selected = type == field.type)
        },
        enabled = editable,
        onToggle = { value ->
            FieldType.entries.firstOrNull { type -> type.yamlValue == value }?.let { picked ->
                // A format only means something on a temporal type; carrying a stale one over emits
                // YAML the loader rejects.
                onFieldChange(field.copy(type = picked, format = if (picked.isTemporal) field.format else ""))
            }
        },
    )
    InspectorNote(text = typeMeaning(type = field.type))
}

@Suppress("FunctionNaming")
@Composable
private fun NormalizerSection(
    field: EditableField,
    editable: Boolean,
    onFieldChange: (EditableField) -> Unit,
) {
    val applies = field.type.isNormalizable
    if (!applies && field.normalizers.isEmpty()) return

    InspectorGroup(
        title = "Normalizers",
        note = if (applies) "in order" else "not used here",
    )
    if (!applies) {
        InspectorNote(
            text = "This type is not normalized, so the ${field.normalizers.size} declared here are " +
                "written to the file and ignored by the engine.",
            warning = true,
        )
    }
    OrderedListEditor(
        items = field.normalizers,
        options = KnownNormalizers.map { id -> OrderedListOption(value = id, what = normalizerMeaning(id = id)) },
        onMove = { from, to -> onFieldChange(field.copy(normalizers = field.normalizers.moved(from, to))) },
        onRemove = { index ->
            onFieldChange(field.copy(normalizers = field.normalizers.filterIndexed { at, _ -> at != index }))
        },
        onAdd = { value -> onFieldChange(field.copy(normalizers = field.normalizers + value)) },
        emptyText = "No normalizer — the value is compared exactly as it arrives.",
        enabled = editable,
    )
    if (applies) {
        InspectorNote(
            text = "The chain runs left to right, and the order is the meaning: trim then lowercase is " +
                "not the same chain as the other way round once collapse_whitespace is between them.",
        )
    }
}

@Suppress("FunctionNaming")
@Composable
private fun OperatorSection(
    field: EditableField,
    editable: Boolean,
    onFieldChange: (EditableField) -> Unit,
) {
    val allowed = operatorsFor(type = field.type)
    InspectorGroup(
        title = "Operators",
        note = if (allowed.isEmpty()) "none — a structure is navigated, not compared" else "what a rule may ask",
    )

    // Allowed first, then anything declared that is not allowed — which keeps a schema someone wrote
    // visible instead of quietly disagreeing with it.
    val declaredButNot = field.operators.filterNot { op -> op in allowed }
    if (allowed.isEmpty() && declaredButNot.isEmpty()) {
        InspectorNote(text = "A ${field.type.yamlValue} is navigated into or aggregated over, never compared.")
        return
    }

    ReasonedChipRow(
        chips = allowed.map { op -> ReasonedChip(value = op, selected = op in field.operators) } +
            declaredButNot.map { op ->
                ReasonedChip(
                    value = op,
                    selected = true,
                    blockedReason = "not allowed on ${field.type.yamlValue} — a rule using it will not compile",
                )
            },
        enabled = editable,
        onToggle = { op ->
            val updated = if (op in field.operators) field.operators - op else field.operators + op
            onFieldChange(field.copy(operators = updated))
        },
    )
    if (field.operators.none { op -> op in allowed } && allowed.isNotEmpty()) {
        InspectorNote(text = "None declared — no rule can compare this field at all.", warning = true)
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Suppress("FunctionNaming", "LongParameterList")
@Composable
private fun MemberSection(
    dottedPath: String,
    field: EditableField,
    editable: Boolean,
    onFieldChange: (EditableField) -> Unit,
    onSelectMember: ((String) -> Unit)?,
) {
    if (!field.type.isStructure) return

    InspectorGroup(title = "Members", note = "${field.fields.size}")
    if (field.fields.isEmpty()) {
        InspectorNote(text = "No members, so there is nothing to navigate into.", warning = true)
    }
    FlowRow(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(space = 4.dp),
        verticalArrangement = Arrangement.spacedBy(space = 4.dp),
    ) {
        field.fields.forEach { member ->
            InspectorDrillChip(
                label = member.path.ifBlank { "…" },
                note = member.type.yamlValue,
                onClick = { onSelectMember?.invoke("$dottedPath.${member.path}") },
                enabled = onSelectMember != null && member.path.isNotBlank(),
            )
        }
    }
    if (editable) {
        InspectorAddButton(
            label = "+ member",
            onClick = {
                val taken = field.fields.map { member -> member.path }.toSet()
                var name = "member"
                var index = 2
                while (name in taken) {
                    name = "member$index"
                    index++
                }
                onFieldChange(field.copy(fields = field.fields + EditableField(path = name)))
            },
        )
    }
    InspectorNote(
        text = "A member is a field in its own right, edited here the same way — so depth is " +
            "navigation and the list never grows a nested form.",
    )
}

/** [this] with the item at [from] moved to [to]. Out-of-range indices leave the list alone. */
private fun List<String>.moved(from: Int, to: Int): List<String> {
    if (from !in indices || to !in indices || from == to) return this
    val copy = toMutableList()
    copy.add(index = to, element = copy.removeAt(index = from))
    return copy
}

private fun typeMeaning(type: FieldType): String = when (type) {
    FieldType.TEXT -> "A string. Normalized before it is compared."
    FieldType.INTEGER -> "A whole number. Ordered, so it takes the comparisons."
    FieldType.DECIMAL -> "A number with decimal places. Ordered."
    FieldType.BOOLEAN -> "True or false, so only equality means anything."
    FieldType.STRING_SET -> "A set of strings — tags, flags, markers. Asked about membership, never order."
    FieldType.DATE -> "A date. ISO unless a format is declared."
    FieldType.DATE_TIME -> "A date and a time. ISO unless a format is declared."
    FieldType.COLLECTION -> "A list of members, navigated into or aggregated over."
    FieldType.OBJECT -> "A nested object, navigated into."
}

private fun normalizerMeaning(id: String): String = when (id) {
    "trim" -> "Drops leading and trailing whitespace."
    "lowercase" -> "Folds to lower case."
    "uppercase" -> "Folds to upper case."
    "collapse_whitespace" -> "Every run of whitespace becomes one space."
    "remove_punctuation" -> "Strips punctuation."
    "german_umlaut_fold" -> "ä → ae, ö → oe, ü → ue, ß → ss."
    else -> ""
}

/** Above this the label sits beside its control rather than over it. */
private val WIDE_FROM = 480.dp
