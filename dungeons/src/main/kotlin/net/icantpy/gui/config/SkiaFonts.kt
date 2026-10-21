package net.icantpy.gui.config

import org.jetbrains.skia.FontMgr
import org.jetbrains.skia.FontSlant
import org.jetbrains.skia.FontStyle

internal object SkiaFonts {
    val normal: FontStyle = FontStyle(400, 5, FontSlant.UPRIGHT)
    val bold: FontStyle = FontStyle(700, 5, FontSlant.UPRIGHT)

    val manager: FontMgr by lazy { resolveFontMgr() }

    private fun resolveFontMgr(): FontMgr {
        val clazz = FontMgr::class.java
        clazz.staticField("default")?.let { return it as FontMgr }
        val companion = clazz.staticField("Companion")
        if (companion != null) {
            companion.javaClass.methods
                .firstOrNull { it.name == "getDefault" && it.parameterCount == 0 }
                ?.invoke(companion)
                ?.let { return it as FontMgr }
        }
        error("FontMgr.default is unavailable")
    }

    private fun Class<*>.staticField(name: String): Any? =
        runCatching { getField(name).get(null) }.getOrNull()
}
