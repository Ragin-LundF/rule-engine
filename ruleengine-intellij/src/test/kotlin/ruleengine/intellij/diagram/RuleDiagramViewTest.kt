package ruleengine.intellij.diagram

import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.use
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import ruleengine.dsl.parser.Parser
import ruleengine.intellij.diagram.model.DiagramViewState

/** Renders the UI's diagram off-screen on the IDE's own Compose: proves the reused sources run there. */
class RuleDiagramViewTest : BasePlatformTestCase() {
    fun testRuleDiagramRenders() {
        val text = "rule \"vip\" { when score > 1 and not tier == \"x\" then label \"vip\" }"
        val state = DiagramViewState(rule = Parser(input = text).parseRules().first())

        val image = ImageComposeScene(width = 800, height = 600) { RuleDiagramView(state = state) }
            .use { scene -> scene.render() }

        assertEquals(800, image.width)
    }
}
