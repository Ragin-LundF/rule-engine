package ruleengine.evaluator.compiled.value.path

/**
 * One step of a compiled field path: a named member, a filter, slice or sort over a collection, or the
 * keyed join a path can start from.
 */
sealed interface CompiledPathSegment
