package net.icantpy.compat

import com.mojang.blaze3d.GpuFormat
import com.mojang.blaze3d.systems.RenderSystem
import com.mojang.blaze3d.textures.GpuTexture
import com.mojang.blaze3d.textures.GpuTextureView
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.client.renderer.RenderPipelines
import net.minecraft.client.renderer.texture.AbstractTexture
import net.minecraft.resources.Identifier
import kotlin.math.roundToInt

/**
 * Persistent GPU copy of the last finished main-target frame, used by motion blur.
 * Minecraft 26.2 adapter; the 26.1.2 twin in payload-2612 has the same API.
 */
object FrameHistory {
    /** Render-target textures are stored bottom-up; sample them with flipped V so the copy draws upright. Unverified in-game. */
    private const val FLIP_V = true

    private val textureId = Identifier.fromNamespaceAndPath("icantpy", "motion_blur_history")
    private val holder = HistoryTexture()
    private var texture: GpuTexture? = null
    private var view: GpuTextureView? = null
    private var format: GpuFormat? = null
    private var registered = false

    /**
     * Copies the main render target into the history texture. Returns true when the copy holds a
     * previous frame that is safe to draw; false right after the texture was (re)created.
     */
    fun capture(mc: Minecraft): Boolean {
        val source = mc.gameRenderer.mainRenderTarget().colorTexture ?: return false
        val width = source.getWidth(0)
        val height = source.getHeight(0)
        if (ensureTexture(source.format, width, height)) return false
        val destination = texture ?: return false
        RenderSystem.getDevice().createCommandEncoder()
            .copyTextureToTexture(source, destination, 0, 0, 0, 0, 0, width, height)
        return true
    }

    /** Blits the history over the current GUI frame. [alpha] is the weight of the history (0..1). */
    fun draw(graphics: GuiGraphicsExtractor, alpha: Float) {
        if (texture == null) return
        val width = graphics.guiWidth()
        val height = graphics.guiHeight()
        val argb = ((alpha.coerceIn(0f, 1f) * 255f).roundToInt() shl 24) or 0x00FFFFFF
        if (FLIP_V) {
            // Negative height with V starting at the bottom edge maps the texture upside down.
            graphics.blit(
                RenderPipelines.GUI_TEXTURED, textureId, 0, height, 0f, height.toFloat(),
                width, -height, width, height, argb,
            )
        } else {
            graphics.blit(RenderPipelines.GUI_TEXTURED, textureId, 0, 0, 0f, 0f, width, height, width, height, argb)
        }
    }

    fun close() {
        if (registered) {
            Minecraft.getInstance().textureManager.release(textureId)
            registered = false
        }
        view?.close()
        texture?.close()
        view = null
        texture = null
        format = null
        holder.clearRefs()
    }

    /** Returns true when a new texture was created (contents invalid for this frame). */
    private fun ensureTexture(sourceFormat: GpuFormat, width: Int, height: Int): Boolean {
        val existing = texture
        if (existing != null && format == sourceFormat &&
            existing.getWidth(0) == width && existing.getHeight(0) == height
        ) {
            return false
        }
        close()
        val device = RenderSystem.getDevice()
        val created = device.createTexture(
            "icantpy motion blur history",
            GpuTexture.USAGE_COPY_DST or GpuTexture.USAGE_TEXTURE_BINDING,
            sourceFormat,
            width,
            height,
            1,
            1,
        )
        val createdView = device.createTextureView(created)
        texture = created
        view = createdView
        format = sourceFormat
        holder.update(created, createdView)
        Minecraft.getInstance().textureManager.register(textureId, holder)
        registered = true
        return true
    }

    private class HistoryTexture : AbstractTexture() {
        fun update(tex: GpuTexture?, texView: GpuTextureView?) {
            this.texture = tex
            this.textureView = texView
        }

        fun clearRefs() {
            this.texture = null
            this.textureView = null
        }

        override fun close() {
            clearRefs()
        }
    }
}
