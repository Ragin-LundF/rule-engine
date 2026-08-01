package ui
/**
 * Describes what the cursor is "inside" within a YAML document.
 *
 * @property currentKey    The key whose value is being edited (e.g., `"type"`, `"schema"`).
 * @property parentKey     The enclosing key (e.g., `"purpose"`, `"normalizers"`, `"operators"`).
 * @property isValue       True when the cursor is on the value side of a `key: ` line.
 * @property isListItem    True when the cursor is on a `- ` list item line.
 * @property currentIndent The number of leading spaces on the cursor's line.
 */
data class YamlCursorContext(
    val currentKey: String? = null,
    val parentKey: String? = null,
    val isValue: Boolean = false,
    val isListItem: Boolean = false,
    val currentIndent: Int = 0,
)

// ── YAML completion candidates ────────────────────────────────────────────────
