package ui.workbench

import kotlinx.coroutines.runBlocking
import ui.workbench.model.InspectorItem
import ui.workbench.model.WorkbenchAction
import ui.workbench.model.mode.AppArea
import ui.workbench.model.mode.RightPanelTab
import ui.workbench.model.mode.RuleMode
import ui.workbench.model.mode.SchemaMode
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class RuleWorkbenchViewModelTest {

    @Test
    fun `initial state uses RULES area and CODE mode`() = runModelTest {
        val state = viewModel.state.value

        assertEquals(expected = AppArea.RULES, actual = state.appArea)
        assertEquals(expected = RuleMode.CODE, actual = state.ruleMode)
        assertEquals(expected = RightPanelTab.INSPECTOR, actual = state.rightPanelTab)
        assertNull(actual = state.selectedRuleId)
    }

    @Test
    fun `SelectAppArea changes application area`() = runModelTest {
        viewModel.dispatch(action = WorkbenchAction.SelectAppArea(area = AppArea.SCHEMA))

        assertEquals(expected = AppArea.SCHEMA, actual = viewModel.state.value.appArea)
    }

    @Test
    fun `SelectRuleMode changes rule mode without affecting app area`() = runModelTest {
        viewModel.dispatch(action = WorkbenchAction.SelectRuleMode(mode = RuleMode.BUILDER))

        assertEquals(expected = AppArea.RULES, actual = viewModel.state.value.appArea)
        assertEquals(expected = RuleMode.BUILDER, actual = viewModel.state.value.ruleMode)
    }

    @Test
    fun `Switching app area preserves area-specific mode selection`() = runModelTest {
        viewModel.dispatch(action = WorkbenchAction.SelectRuleMode(mode = RuleMode.DIAGRAM))
        viewModel.dispatch(action = WorkbenchAction.SelectAppArea(area = AppArea.SCHEMA))
        viewModel.dispatch(action = WorkbenchAction.SelectSchemaMode(mode = SchemaMode.YAML))
        viewModel.dispatch(action = WorkbenchAction.SelectAppArea(area = AppArea.RULES))

        assertEquals(expected = AppArea.RULES, actual = viewModel.state.value.appArea)
        assertEquals(expected = RuleMode.DIAGRAM, actual = viewModel.state.value.ruleMode)
        assertEquals(expected = SchemaMode.YAML, actual = viewModel.state.value.schemaMode)
    }

    @Test
    fun `SelectField and SelectAction update dedicated ids and inspector item`() = runModelTest {
        viewModel.dispatch(action = WorkbenchAction.SelectField(fieldId = "amount"))

        val afterField = viewModel.state.value
        assertEquals(expected = "amount", actual = afterField.selectedFieldId)
        assertEquals(expected = InspectorItem.Field(id = "amount"), actual = afterField.selectedInspectorItem)

        viewModel.dispatch(action = WorkbenchAction.SelectAction(actionName = "label"))

        val afterAction = viewModel.state.value
        assertEquals(expected = "label", actual = afterAction.selectedActionName)
        assertEquals(expected = InspectorItem.Action(name = "label"), actual = afterAction.selectedInspectorItem)
    }

    @Test
    fun `SelectInspectorItem with Condition does not set field or action selection`() = runModelTest {
        viewModel.dispatch(
            action = WorkbenchAction.SelectInspectorItem(item = InspectorItem.Condition(conditionId = "c1")),
        )

        val state = viewModel.state.value
        assertEquals(expected = InspectorItem.Condition(conditionId = "c1"), actual = state.selectedInspectorItem)
        assertNull(actual = state.selectedFieldId)
        assertNull(actual = state.selectedActionName)
    }

    /**
     * The branch the Inspector's rule panel hangs off. It had no production dispatcher at all until
     * `RuleEditor` started deriving the selection, so `RuleInspector` was unreachable code — this pins
     * the contract that makes it reachable: one dispatch sets both the id and the inspector item.
     */
    @Test
    fun `SelectRule sets both the rule id and the inspector item`() = runModelTest {
        viewModel.dispatch(action = WorkbenchAction.SelectRule(ruleId = "high-amount"))

        val state = viewModel.state.value
        assertEquals(expected = "high-amount", actual = state.selectedRuleId)
        assertEquals(expected = InspectorItem.Rule(id = "high-amount"), actual = state.selectedInspectorItem)
    }

    /** No rule selected is a real state — an empty buffer — and has to clear the panel, not keep it. */
    @Test
    fun `SelectRule with null clears the rule id and the inspector item`() = runModelTest {
        viewModel.dispatch(action = WorkbenchAction.SelectRule(ruleId = "high-amount"))
        viewModel.dispatch(action = WorkbenchAction.SelectRule(ruleId = null))

        val state = viewModel.state.value
        assertNull(actual = state.selectedRuleId)
        assertNull(actual = state.selectedInspectorItem)
    }

    /**
     * Selecting a rule replaces a selected condition, deliberately: the condition belonged to the
     * rule just left, and leaving it on screen would describe a row that is no longer visible.
     */
    @Test
    fun `SelectRule replaces a previously selected condition`() = runModelTest {
        viewModel.dispatch(action = WorkbenchAction.SelectCondition(conditionId = "c1"))
        viewModel.dispatch(action = WorkbenchAction.SelectRule(ruleId = "other"))

        assertEquals(
            expected = InspectorItem.Rule(id = "other"),
            actual = viewModel.state.value.selectedInspectorItem,
        )
    }

    @Test
    fun `SelectRightPanelTab switches right panel tab`() = runModelTest {
        viewModel.dispatch(action = WorkbenchAction.SelectRightPanelTab(tab = RightPanelTab.SIMULATE))

        assertEquals(expected = RightPanelTab.SIMULATE, actual = viewModel.state.value.rightPanelTab)
    }

    private fun runModelTest(block: suspend TestContext.() -> Unit) = runBlocking {
        val context = TestContext(viewModel = RuleWorkbenchViewModel())
        context.block()
    }

    private class TestContext(val viewModel: RuleWorkbenchViewModel)
}
