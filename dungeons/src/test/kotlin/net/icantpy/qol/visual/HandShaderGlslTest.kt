package net.icantpy.qol.visual

import java.util.Locale
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class HandShaderGlslTest {
    private val red = 0xFF0000
    private val blue = 0x0000FF

    private fun body(
        mode: HandShaderMode = HandShaderMode.CHROMA,
        color1: Int = red,
        color2: Int = blue,
        speed: Float = 1f,
        opacity: Float = 0.85f,
    ): String = HandShaderGlsl.body(mode, color1, color2, speed, opacity)

    @Test
    fun everyModeProducesWellFormedMain() {
        for (mode in HandShaderMode.entries) {
            val text = body(mode)
            assertContains(text, "void main()", message = "mode $mode")
            assertBalanced(text, '{', '}', mode)
            assertBalanced(text, '(', ')', mode)
            assertFalse(text.contains("NaN"), "mode $mode contains NaN")
            assertFalse(text.contains("Infinity"), "mode $mode contains Infinity")
        }
    }

    @Test
    fun floatsUseDotUnderGermanLocale() {
        val previous = Locale.getDefault()
        try {
            Locale.setDefault(Locale.GERMANY)
            for (mode in HandShaderMode.entries) {
                val text = body(mode, color1 = 0x7AF0FF, color2 = 0x6AD5FF, speed = 1.5f, opacity = 0.25f)
                assertFalse(Regex("\\d,\\d").containsMatchIn(text), "decimal comma in mode $mode")
                assertContains(text, "const float OPACITY = 0.250;")
                assertContains(text, "const float SPEED = 1.500;")
            }
        } finally {
            Locale.setDefault(previous)
        }
    }

    @Test
    fun colorsAreEmbeddedAsNormalizedRgb() {
        val text = body(color1 = 0xFF0000, color2 = 0x0000FF)
        assertContains(text, "const vec3 C1 = vec3(1.000, 0.000, 0.000);")
        assertContains(text, "const vec3 C2 = vec3(0.000, 0.000, 1.000);")
    }

    @Test
    fun keyIsStableForEqualInputsAndChangesWithAnyInput() {
        val base = HandShaderGlsl.key(HandShaderMode.SNOW, red, blue, 1f, 0.5f)
        assertEquals(base, HandShaderGlsl.key(HandShaderMode.SNOW, red, blue, 1f, 0.5f))
        assertNotEquals(base, HandShaderGlsl.key(HandShaderMode.SMOKE, red, blue, 1f, 0.5f))
        assertNotEquals(base, HandShaderGlsl.key(HandShaderMode.SNOW, 0x00FF00, blue, 1f, 0.5f))
        assertNotEquals(base, HandShaderGlsl.key(HandShaderMode.SNOW, red, 0x00FF00, 1f, 0.5f))
        assertNotEquals(base, HandShaderGlsl.key(HandShaderMode.SNOW, red, blue, 2f, 0.5f))
        assertNotEquals(base, HandShaderGlsl.key(HandShaderMode.SNOW, red, blue, 1f, 0.6f))
    }

    @Test
    fun nonFiniteSettingsFallBackToDefaults() {
        val text = body(speed = Float.NaN, opacity = Float.POSITIVE_INFINITY)
        assertContains(text, "const float SPEED = 1.000;")
        assertContains(text, "const float OPACITY = 0.850;")
        assertFalse(text.contains("NaN"))
    }

    private fun assertBalanced(text: String, open: Char, close: Char, mode: HandShaderMode) {
        var depth = 0
        for (c in text) {
            if (c == open) depth++
            if (c == close) depth--
            assertTrue(depth >= 0, "unbalanced '$close' in mode $mode")
        }
        assertEquals(0, depth, "unbalanced '$open' in mode $mode")
    }
}
