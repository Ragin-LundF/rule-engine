package ui.builder

import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * The path edits the breadcrumb performs. Both are about what happens to the *rest* of the path, which
 * is where a path editor is easy to get wrong: repointing a segment must not leave a tail that was
 * resolved against the member that is now gone, while removing one must not take the tail with it.
 */
class BuilderPathStepTest {

    private val path = listOf(
        BuilderPathStep(
            name = "orders",
            filters = listOf(BuilderFilter(field = "status", operator = "==", value = "paid")),
        ),
        BuilderPathStep(name = "items"),
        BuilderPathStep(name = "price"),
    )

    @Test
    fun `repointing the root drops the tail`() {
        assertEquals(
            expected = listOf("customer"),
            actual = path.withSegmentName(depth = 0, name = "customer").names,
        )
    }

    @Test
    fun `repointing a middle segment drops only what is below it`() {
        assertEquals(
            expected = listOf("orders", "lines"),
            actual = path.withSegmentName(depth = 1, name = "lines").names,
        )
    }

    @Test
    fun `repointing a segment drops its filters, which belonged to the old member`() {
        assertEquals(
            expected = emptyList(),
            actual = path.withSegmentName(depth = 0, name = "customer").single().filters,
        )
    }

    @Test
    fun `removing a middle segment keeps the tail`() {
        assertEquals(
            expected = listOf("orders", "price"),
            actual = path.withoutSegment(depth = 1).names,
        )
    }

    @Test
    fun `removing a segment keeps the filters of the segments that stay`() {
        assertEquals(
            expected = listOf(BuilderFilter(field = "status", operator = "==", value = "paid")),
            actual = path.withoutSegment(depth = 2).first().filters,
        )
    }
}
