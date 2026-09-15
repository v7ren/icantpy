package net.icantpy.gui.configUI

import org.jetbrains.skia.Data
import org.jetbrains.skia.FontMgr
import org.jetbrains.skia.FontStyle
import org.jetbrains.skia.Typeface

internal data class GuiTypefaces(
    val regular: Typeface,
    val semibold: Typeface,
    val mono: Typeface,
)

internal object GuiFonts {
    private val cache = HashMap<GuiFontId, GuiTypefaces>()

    fun of(id: GuiFontId): GuiTypefaces = cache.getOrPut(id) { load(id) }

    private fun load(id: GuiFontId): GuiTypefaces {
        if (id == GuiFontId.NUNITO) {
            bundled("Nunito-Regular.ttf")?.let { regular ->
                val bold = bundled("Nunito-SemiBold.ttf") ?: bundled("Nunito-Bold.ttf") ?: regular
                return GuiTypefaces(regular, bold, monoTypeface(regular))
            }
        }
        val names = families(id)
        val regular = match(names, FontStyle.NORMAL) ?: fallback()
        val semibold = match(names, FontStyle.BOLD) ?: match(names, FontStyle.NORMAL) ?: regular
        return GuiTypefaces(regular, semibold, monoTypeface(regular))
    }

    private fun monoTypeface(fallback: Typeface): Typeface =
        match(families(GuiFontId.CASCADIA), FontStyle.NORMAL)
            ?: match(families(GuiFontId.CONSOLAS), FontStyle.NORMAL)
            ?: fallback

    private fun families(id: GuiFontId): List<String> = when (id) {
        GuiFontId.SEGOE -> listOf("Segoe UI", "SegoeUI")
        GuiFontId.TAHOMA -> listOf("Tahoma")
        GuiFontId.VERDANA -> listOf("Verdana")
        GuiFontId.ARIAL -> listOf("Arial")
        GuiFontId.CALIBRI -> listOf("Calibri")
        GuiFontId.CONSOLAS -> listOf("Consolas")
        GuiFontId.CASCADIA -> listOf("Cascadia Mono", "Cascadia Code", "Cascadia Mono PL")
        GuiFontId.YAHEI -> listOf("Microsoft JhengHei UI", "Microsoft JhengHei", "Microsoft YaHei UI", "Microsoft YaHei")
        GuiFontId.NUNITO -> listOf("Nunito", "Segoe UI")
    }

    private fun match(names: List<String>, style: FontStyle): Typeface? {
        names.forEach { name ->
            FontMgr.default.matchFamilyStyle(name, style)?.let { return it }
            FontMgr.default.legacyMakeTypeface(name, style)?.let { return it }
        }
        return null
    }

    private fun bundled(fileName: String): Typeface? {
        val bytes = GuiFonts::class.java.getResourceAsStream("/assets/icantpy/font/$fileName")?.use { stream ->
            stream.readBytes()
        } ?: return null
        if (bytes.isEmpty()) return null
        return FontMgr.default.makeFromData(Data.makeFromBytes(bytes))
    }

    private fun fallback(): Typeface =
        FontMgr.default.legacyMakeTypeface("Segoe UI", FontStyle.NORMAL)
            ?: FontMgr.default.legacyMakeTypeface("Arial", FontStyle.NORMAL)
            ?: FontMgr.default.legacyMakeTypeface("sans-serif", FontStyle.NORMAL)
            ?: error("No typeface available")
}
