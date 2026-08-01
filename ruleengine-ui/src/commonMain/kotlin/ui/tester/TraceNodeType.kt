package ui.tester

/**
 * Kind of a [TraceNode], mirroring the core's `NodeType` one-to-one.
 *
 * Mirrored rather than reused because `NodeType` is a JVM-only core type and the trace is rendered
 * from `commonMain`.
 */
enum class TraceNodeType {
    EVALUATION,
    CONDITION,
    AND,
    OR,
    NOT,
    RULE,
}
