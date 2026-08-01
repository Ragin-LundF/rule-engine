package ui.diagrams

import androidx.compose.ui.graphics.Color
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * The diagram palettes are partly read from the app palettes and partly literal. These pin every
 * resulting colour, so a change to `LightPalette` or `DarkPalette` that would silently restyle the
 * diagrams shows up here instead of in a screenshot.
 */
class DiagramPaletteTest {

    @Test
    fun `light diagram colours are unchanged`() {
        val p = LightDiagramPalette
        assertEquals(expected = Color(0xFFF6F8FA), actual = p.diagramBg)
        assertEquals(expected = Color(0xFFEFF4FF), actual = p.nodeBgRule)
        assertEquals(expected = Color(0xFFEDF3FF), actual = p.nodeBgAnd)
        assertEquals(expected = Color(0xFFEAF7EF), actual = p.nodeBgOr)
        assertEquals(expected = Color(0xFFFDEEEE), actual = p.nodeBgNot)
        assertEquals(expected = Color(0xFFFFFFFF), actual = p.nodeBgCondition)
        assertEquals(expected = Color(0xFFF3EFFE), actual = p.nodeBgActions)
        assertEquals(expected = Color(0xFFC7D9FB), actual = p.borderRule)
        assertEquals(expected = Color(0xFF9EC1F7), actual = p.borderAnd)
        assertEquals(expected = Color(0xFF9BD8B0), actual = p.borderOr)
        assertEquals(expected = Color(0xFFF0B4B4), actual = p.borderNot)
        assertEquals(expected = Color(0xFFD5DAE1), actual = p.borderCondition)
        assertEquals(expected = Color(0xFFC9B8F5), actual = p.borderActions)
        assertEquals(expected = Color(0xFFC4CBD6), actual = p.lineColor)
        assertEquals(expected = Color(0xFF2563EB), actual = p.labelRule)
        assertEquals(expected = Color(0xFF1D4ED8), actual = p.labelAnd)
        assertEquals(expected = Color(0xFF16A34A), actual = p.labelOr)
        assertEquals(expected = Color(0xFFDC2626), actual = p.labelNot)
        assertEquals(expected = Color(0xFF7C3AED), actual = p.labelActions)
        assertEquals(expected = Color(0xFF0891B2), actual = p.labelField)
        assertEquals(expected = Color(0xFFB45309), actual = p.labelOp)
        assertEquals(expected = Color(0xFF16A34A), actual = p.labelValue)
        assertEquals(expected = Color(0xFF7C3AED), actual = p.labelActionName)
        assertEquals(expected = Color(0xFF1A1F2B), actual = p.labelArg)
        assertEquals(expected = Color(0xFF5C6470), actual = p.textDesc)
    }

    @Test
    fun `dark diagram colours are unchanged`() {
        val p = DarkDiagramPalette
        assertEquals(expected = Color(0xFF0D1117), actual = p.diagramBg)
        assertEquals(expected = Color(0xFF1C2333), actual = p.nodeBgRule)
        assertEquals(expected = Color(0xFF1A2035), actual = p.nodeBgAnd)
        assertEquals(expected = Color(0xFF1A2D1A), actual = p.nodeBgOr)
        assertEquals(expected = Color(0xFF2D1A1A), actual = p.nodeBgNot)
        assertEquals(expected = Color(0xFF161B22), actual = p.nodeBgCondition)
        assertEquals(expected = Color(0xFF1A2233), actual = p.nodeBgActions)
        assertEquals(expected = Color(0xFF3B4A6B), actual = p.borderRule)
        assertEquals(expected = Color(0xFF2B5086), actual = p.borderAnd)
        assertEquals(expected = Color(0xFF2B6B2B), actual = p.borderOr)
        assertEquals(expected = Color(0xFF7B2B2B), actual = p.borderNot)
        assertEquals(expected = Color(0xFF30363D), actual = p.borderCondition)
        assertEquals(expected = Color(0xFF3B5A8B), actual = p.borderActions)
        assertEquals(expected = Color(0xFF3D4450), actual = p.lineColor)
        assertEquals(expected = Color(0xFF79C0FF), actual = p.labelRule)
        assertEquals(expected = Color(0xFF58A6FF), actual = p.labelAnd)
        assertEquals(expected = Color(0xFF3FB950), actual = p.labelOr)
        assertEquals(expected = Color(0xFFF85149), actual = p.labelNot)
        assertEquals(expected = Color(0xFFA78BFA), actual = p.labelActions)
        assertEquals(expected = Color(0xFF79C0FF), actual = p.labelField)
        assertEquals(expected = Color(0xFFD29922), actual = p.labelOp)
        assertEquals(expected = Color(0xFF3FB950), actual = p.labelValue)
        assertEquals(expected = Color(0xFFA78BFA), actual = p.labelActionName)
        assertEquals(expected = Color(0xFFE6EDF3), actual = p.labelArg)
        assertEquals(expected = Color(0xFF8B949E), actual = p.textDesc)
    }
}
