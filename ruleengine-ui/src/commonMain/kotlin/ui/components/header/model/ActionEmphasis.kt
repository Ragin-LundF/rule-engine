package ui.components.header.model

/**
 * How hard a header action fights for room.
 *
 * The ranking is the whole point: the old Rules action row had none, so a narrow window squeezed
 * whatever happened to be last — which is how "Validate" once rendered one letter per line.
 */
enum class ActionEmphasis {
    /** The verb the area is for. Keeps its label at every width and never moves into the overflow. */
    PRIMARY,

    /** Drops to its icon when the bar is tight. */
    STANDARD,

    /** Always in the overflow menu, at every width. */
    OVERFLOW,
}
