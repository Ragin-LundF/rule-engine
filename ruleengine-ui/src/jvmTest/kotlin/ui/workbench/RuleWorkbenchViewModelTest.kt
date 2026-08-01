package ui.workbench

import kotlinx.coroutines.runBlocking
import ui.workbench.model.AppArea
import ui.workbench.model.InspectorItem
import ui.workbench.model.RightPanelTab
import ui.workbench.model.RuleMode
import ui.workbench.model.SchemaMode
import ui.workbench.model.WorkbenchAction
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
        viewModel.dispatch(action = WorkbenchAction.SelectInspectorItem(item = InspectorItem.Condition(conditionId = "c1")))

        val state = viewModel.state.value
        assertEquals(expected = InspectorItem.Condition(conditionId = "c1"), actual = state.selectedInspectorItem)
        assertNull(actual = state.selectedFieldId)
        assertNull(actual = state.selectedActionName)
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
