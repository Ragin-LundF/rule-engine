package ui.tester

/** Which presentation of the last run the Test panel is showing. */
enum class TestResultTab {
    /** The roster: one row per rule, expandable to its flat condition list. */
    RESULTS,

    /** The decision trees, with the nesting and the stopping point the flat rows cannot show. */
    TRACE,
}
