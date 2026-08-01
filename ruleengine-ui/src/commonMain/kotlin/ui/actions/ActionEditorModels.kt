package ui.actions

import ruleengine.core.domain.dto.ActionArgType

/** All known action argument type ids exposed in the editor, lowercase, in declaration order. */
val KnownActionArgTypes: List<String> =
    ActionArgType.entries.map { argType -> argType.name.lowercase() }
