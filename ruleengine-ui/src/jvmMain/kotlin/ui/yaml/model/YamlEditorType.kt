package ui.yaml.model
/**
 * Which YAML the editor is showing, which decides how its keys are coloured and what is completed.
 *
 * [PROJECT_MANIFEST] arrived with the preview dock. Until then the Manifest area's YAML tab was a plain
 * text field with no highlighting at all, so the one file that decides *what runs in what order* was
 * also the only one shown as undifferentiated text.
 */
enum class YamlEditorType { FIELD_SCHEMA, ACTION_SCHEMA, PROJECT_MANIFEST }

// ── YAML cursor context for completions ───────────────────────────────────────
