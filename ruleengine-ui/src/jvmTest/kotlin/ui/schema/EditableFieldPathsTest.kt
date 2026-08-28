package ui.schema

import ruleengine.core.domain.dto.field.FieldType
import ui.schema.model.EditableField
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

/**
 * A leaf name is not unique, and that is the whole point of these functions.
 *
 * `lender` is a member of two different collections in the fixture below. Anything that looked a field
 * up by name would edit the wrong one — silently, and only for schemas that happen to reuse a name,
 * which is most real ones.
 */
class EditableFieldPathsTest {

    private val fields = listOf(
        EditableField(path = "product", type = FieldType.TEXT, operators = listOf("equals")),
        EditableField(
            path = "existingLoans",
            type = FieldType.COLLECTION,
            fields = listOf(
                EditableField(path = "lender", type = FieldType.TEXT, alias = "loanLender"),
                EditableField(
                    path = "guarantor",
                    type = FieldType.OBJECT,
                    fields = listOf(EditableField(path = "lender", type = FieldType.TEXT, alias = "deep")),
                ),
            ),
        ),
        EditableField(
            path = "applicant",
            type = FieldType.OBJECT,
            fields = listOf(EditableField(path = "lender", type = FieldType.TEXT, alias = "applicantLender")),
        ),
    )

    @Test
    fun `a path resolves to the field that path names, not the first of that name`() {
        assertEquals(expected = "loanLender", actual = fields.findByPath("existingLoans.lender")?.alias)
        assertEquals(expected = "applicantLender", actual = fields.findByPath("applicant.lender")?.alias)
        assertEquals(expected = "deep", actual = fields.findByPath("existingLoans.guarantor.lender")?.alias)
        assertEquals(expected = "product", actual = fields.findByPath("product")?.path)
    }

    @Test
    fun `a path that does not exist resolves to nothing`() {
        assertNull(actual = fields.findByPath("nope"))
        assertNull(actual = fields.findByPath("product.lender"), message = "a leaf has no members")
        assertNull(actual = fields.findByPath("applicant.missing"))
        assertNull(actual = fields.findByPath(""))
    }

    @Test
    fun `an update reaches the nested field and leaves its namesakes alone`() {
        val updated = fields.updateAtPath("applicant.lender") { field -> field.copy(alias = "changed") }

        assertEquals(expected = "changed", actual = updated.findByPath("applicant.lender")?.alias)
        assertEquals(
            expected = "loanLender",
            actual = updated.findByPath("existingLoans.lender")?.alias,
            message = "the namesake under another parent was edited too",
        )
        assertEquals(expected = "deep", actual = updated.findByPath("existingLoans.guarantor.lender")?.alias)
    }

    @Test
    fun `an update three levels down keeps the levels above it intact`() {
        val updated = fields.updateAtPath("existingLoans.guarantor.lender") { it.copy(alias = "x") }

        assertEquals(expected = "x", actual = updated.findByPath("existingLoans.guarantor.lender")?.alias)
        assertEquals(expected = 2, actual = assertNotNull(updated.findByPath("existingLoans")).fields.size)
        assertEquals(expected = FieldType.COLLECTION, actual = updated.findByPath("existingLoans")?.type)
    }

    /** Editing something the user selected: a stale selection must not create a field. */
    @Test
    fun `an update to a path that does not exist changes nothing`() {
        assertEquals(expected = fields, actual = fields.updateAtPath("nope") { it.copy(alias = "x") })
        assertEquals(expected = fields, actual = fields.updateAtPath("applicant.nope") { it.copy(alias = "x") })
    }

    @Test
    fun `a removal takes the field's members with it and spares its namesakes`() {
        val withoutLoans = fields.removeAtPath("existingLoans")
        assertNull(actual = withoutLoans.findByPath("existingLoans"))
        assertNull(actual = withoutLoans.findByPath("existingLoans.lender"))
        assertNotNull(actual = withoutLoans.findByPath("applicant.lender"))

        val withoutMember = fields.removeAtPath("existingLoans.lender")
        assertNull(actual = withoutMember.findByPath("existingLoans.lender"))
        assertNotNull(actual = withoutMember.findByPath("existingLoans.guarantor.lender"))
        assertEquals(expected = 1, actual = withoutMember.findByPath("existingLoans")?.fields?.size)
    }

    @Test
    fun `flatten lists every field with the path it is reached by, parents first`() {
        val paths = fields.flattenPaths().map { (dotted, _) -> dotted }

        assertEquals(
            expected = listOf(
                "product",
                "existingLoans",
                "existingLoans.lender",
                "existingLoans.guarantor",
                "existingLoans.guarantor.lender",
                "applicant",
                "applicant.lender",
            ),
            actual = paths,
        )
    }

    @Test
    fun `a parent path is the segments before the last`() {
        assertEquals(expected = "existingLoans", actual = parentPathOf("existingLoans.lender"))
        assertEquals(expected = "existingLoans.guarantor", actual = parentPathOf("existingLoans.guarantor.lender"))
        assertNull(actual = parentPathOf("product"))
    }
}
