package net.icantpy.dungeon.terminal

import com.mojang.blaze3d.font.GlyphProvider
import com.mojang.blaze3d.font.TrueTypeGlyphProvider
import net.fabricmc.loader.api.FabricLoader
import net.icantpy.Icantpy
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.Font
import net.minecraft.client.gui.GlyphSource
import net.minecraft.client.gui.font.FontOption
import net.minecraft.client.gui.font.FontSet
import net.minecraft.client.gui.font.GlyphStitcher
import net.minecraft.client.gui.font.glyphs.EffectGlyph
import net.minecraft.client.gui.font.providers.FreeTypeUtil
import net.minecraft.network.chat.FontDescription
import net.minecraft.resources.Identifier
import net.minecraft.util.Util
import org.lwjgl.system.MemoryStack
import org.lwjgl.system.MemoryUtil
import org.lwjgl.util.freetype.FT_Face
import org.lwjgl.util.freetype.FreeType
import java.io.IOException
import java.nio.ByteBuffer
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.extension
import kotlin.io.path.fileSize

/**
 * Fonts the player drops into config/icantpy-fonts (.ttf or .otf). The chosen file becomes its own Minecraft [Font],
 * built straight from the file with the game's FreeType glyph provider (the same one resource-pack TTF fonts use), so
 * no resource pack or resource reload is involved. Only the font in use stays open; choosing another one, changing
 * its size, or reloading the folder closes it.
 *
 * Client thread only: glyph textures are created while drawing.
 */
object TerminalUserFonts {
    private const val FOLDER = "icantpy-fonts"
    private const val MAX_FILE_BYTES = 32L * 1024 * 1024
    private const val OVERSAMPLE = 4f
    private val EXTENSIONS = setOf("ttf", "otf")

    private data class Key(val file: String, val size: Float, val shift: Float)

    private class Loaded(val key: Key, val provider: TrueTypeGlyphProvider, val fontSet: FontSet, val font: Font) {
        fun close() {
            fontSet.close()
            provider.close()
        }
    }

    private var loaded: Loaded? = null
    private var names: List<String>? = null

    /** Files that failed, so a broken font is reported once instead of retried every frame. */
    private val failed = HashSet<Key>()
    private var textureIndex = 0

    fun folder(): Path = FabricLoader.getInstance().configDir.resolve(FOLDER)

    /** Font file names in the folder, sorted. The folder is created on first use so players can find it. */
    fun available(): List<String> = names ?: scan().also { names = it }

    /** Forgets the open font and the file list, so new or replaced files are picked up. */
    fun refresh() {
        close()
        names = null
        failed.clear()
    }

    fun openFolder() {
        available()
        Util.getPlatform().openPath(folder())
    }

    /** The font for [settings]' own font, or null when none is chosen or it cannot be read. */
    fun fontFor(settings: TerminalSettings): Font? {
        val key = keyOf(settings) ?: return null
        loaded?.takeIf { it.key == key }?.let { return it.font }
        if (key in failed) return null
        close()
        return try {
            load(key).also { loaded = it }.font
        } catch (error: Exception) {
            failed += key
            Icantpy.LOGGER.warn("Could not load terminal font {}", key.file, error)
            null
        }
    }

    /** True when [settings] picks a font file that is missing or could not be read. */
    fun isBroken(settings: TerminalSettings): Boolean {
        val key = keyOf(settings) ?: return settings.font == TerminalFont.CUSTOM
        return key in failed
    }

    fun close() {
        loaded?.close()
        loaded = null
    }

    /** Only names listed in the folder are opened, so a pasted style can never point outside it. */
    private fun keyOf(settings: TerminalSettings): Key? {
        if (settings.font != TerminalFont.CUSTOM || settings.customFont !in available()) return null
        return Key(settings.customFont, settings.customFontSize, settings.customFontShift)
    }

    private fun scan(): List<String> {
        val dir = folder()
        return try {
            Files.createDirectories(dir)
            Files.list(dir).use { files ->
                files.filter { Files.isRegularFile(it) && it.extension.lowercase() in EXTENSIONS }
                    .map { it.fileName.toString() }
                    .sorted()
                    .toList()
            }
        } catch (error: IOException) {
            Icantpy.LOGGER.warn("Could not list terminal fonts in {}", dir, error)
            emptyList()
        }
    }

    private fun load(key: Key): Loaded {
        val path = folder().resolve(key.file)
        if (path.fileSize() > MAX_FILE_BYTES) throw IOException("${key.file} is larger than ${MAX_FILE_BYTES / 1024 / 1024} MB")
        val bytes = Files.readAllBytes(path)
        val buffer = MemoryUtil.memAlloc(bytes.size).put(bytes).flip()
        val provider = try {
            openProvider(buffer, key)
        } catch (error: Exception) {
            MemoryUtil.memFree(buffer)
            throw error
        }
        val texturePrefix = Identifier.fromNamespaceAndPath("icantpy", "font/user_${textureIndex++}")
        val fontSet = FontSet(GlyphStitcher(Minecraft.getInstance().textureManager, texturePrefix))
        fontSet.reload(listOf(GlyphProvider.Conditional(provider, FontOption.Filter.ALWAYS_PASS)), emptySet())
        val font = Font(object : Font.Provider {
            override fun glyphs(description: FontDescription): GlyphSource = fontSet.source(false)

            override fun effect(): EffectGlyph = fontSet.whiteGlyph()
        })
        return Loaded(key, provider, fontSet, font)
    }

    /** Mirrors vanilla's TTF font definition; the provider owns [buffer] and the face from here on. */
    private fun openProvider(buffer: ByteBuffer, key: Key): TrueTypeGlyphProvider = synchronized(FreeTypeUtil.LIBRARY_LOCK) {
        MemoryStack.stackPush().use { stack ->
            val pointer = stack.mallocPointer(1)
            FreeTypeUtil.assertError(FreeType.FT_New_Memory_Face(FreeTypeUtil.getLibrary(), buffer, 0L, pointer), "Initializing font face")
            val face = FT_Face.create(pointer.get())
            try {
                FreeTypeUtil.assertError(FreeType.FT_Select_Charmap(face, FreeType.FT_ENCODING_UNICODE), "Find unicode charmap")
                TrueTypeGlyphProvider(buffer, face, key.size, OVERSAMPLE, 0f, key.shift, "")
            } catch (error: Exception) {
                FreeType.FT_Done_Face(face)
                throw error
            }
        }
    }
}
