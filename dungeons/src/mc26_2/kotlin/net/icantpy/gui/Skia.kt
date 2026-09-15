package net.icantpy.gui

import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.asComposeCanvas
import com.mojang.blaze3d.GpuFormat
import com.mojang.blaze3d.opengl.GlStateManager
import com.mojang.blaze3d.opengl.GlTexture
import com.mojang.blaze3d.systems.RenderSystem
import com.mojang.blaze3d.textures.GpuTexture
import com.mojang.blaze3d.textures.GpuTextureView
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.client.renderer.texture.AbstractTexture
import net.minecraft.resources.Identifier
import org.jetbrains.skia.BackendRenderTarget
import org.jetbrains.skia.ColorSpace
import org.jetbrains.skia.DirectContext
import org.jetbrains.skia.Surface
import org.jetbrains.skia.SurfaceColorFormat
import org.jetbrains.skia.SurfaceOrigin
import org.lwjgl.opengl.WGL
import java.util.ArrayDeque
import java.util.concurrent.atomic.AtomicInteger

object SkiaContext {
    private var wglContext: Long = 0

    fun initialize() {
        if (wglContext != 0L) return
        val deviceContext = WGL.wglGetCurrentDC()
        if (deviceContext == 0L) return
        wglContext = WGL.wglCreateContext(null, deviceContext)
        if (wglContext == 0L) return
        WGL.wglShareLists(null, WGL.wglGetCurrentContext(null), wglContext)
    }

    val directContext: DirectContext by lazy { DirectContext.makeGL() }

    fun run(runnable: Runnable) {
        val oldContext = WGL.wglGetCurrentContext(null)
        val dc = WGL.wglGetCurrentDC()
        WGL.wglMakeCurrent(null, dc, wglContext)
        try {
            runnable.run()
        } finally {
            WGL.wglMakeCurrent(null, dc, oldContext)
        }
    }
}

private const val GL_FRAMEBUFFER = 0x8D40
private const val GL_COLOR_ATTACHMENT0 = 0x8CE0
private const val GL_TEXTURE_2D = 0x0DE1
private const val GL_RGBA8 = 0x8058

private class SkiaBackedTexture : AbstractTexture() {
    fun update(tex: GpuTexture?, view: GpuTextureView?) {
        this.texture = tex
        this.textureView = view
    }

    fun clearRefs() {
        this.texture = null
        this.textureView = null
    }

    override fun close() {
        clearRefs()
    }
}

private class SurfaceResources(
    val fbo: Int,
    val gpuTexture: GpuTexture,
    val gpuTextureView: GpuTextureView,
    val skiaSurface: Surface,
    val backendTarget: BackendRenderTarget,
) {
    fun destroy() {
        SkiaContext.run {
            skiaSurface.close()
            backendTarget.close()
        }
        gpuTextureView.close()
        gpuTexture.close()
        GlStateManager._glDeleteFramebuffers(fbo)
    }
}

class SkiaSurface {
    private val textureId = Identifier.fromNamespaceAndPath(
        Composite.modId(),
        "skia_surface_${counter.getAndIncrement()}",
    )
    private var currentWidth = 0
    private var currentHeight = 0
    private var active: SurfaceResources? = null
    private val skiaTexture = SkiaBackedTexture()
    private var textureRegistered = false
    private val pendingCleanup = mutableListOf<DeferredCleanup>()
    private val recordedCalls: ArrayDeque<GuiGraphicsExtractor.() -> Unit> = ArrayDeque()

    fun resize(width: Int, height: Int) {
        if (width <= 0 || height <= 0) return
        if (currentWidth == width && currentHeight == height) return

        skiaTexture.clearRefs()
        active?.let { pendingCleanup.add(DeferredCleanup(it)) }
        active = null
        currentWidth = width
        currentHeight = height

        val device = RenderSystem.getDevice()
        val usage = GpuTexture.USAGE_COPY_DST or
            GpuTexture.USAGE_COPY_SRC or
            GpuTexture.USAGE_TEXTURE_BINDING or
            GpuTexture.USAGE_RENDER_ATTACHMENT
        val gpuTexture = device.createTexture(
            { "icantpy Skia Surface" },
            usage,
            GpuFormat.RGBA8_UNORM,
            width,
            height,
            1,
            1,
        )
        val gpuTextureView = device.createTextureView(gpuTexture)
        val glId = (gpuTexture as GlTexture).glId()
        org.lwjgl.opengl.GL11.glBindTexture(GL_TEXTURE_2D, glId)
        org.lwjgl.opengl.GL11.glTexParameteri(
            GL_TEXTURE_2D,
            org.lwjgl.opengl.GL11.GL_TEXTURE_MIN_FILTER,
            org.lwjgl.opengl.GL11.GL_NEAREST,
        )
        org.lwjgl.opengl.GL11.glTexParameteri(
            GL_TEXTURE_2D,
            org.lwjgl.opengl.GL11.GL_TEXTURE_MAG_FILTER,
            org.lwjgl.opengl.GL11.GL_NEAREST,
        )
        org.lwjgl.opengl.GL11.glBindTexture(GL_TEXTURE_2D, 0)
        val fbo = GlStateManager.glGenFramebuffers()
        GlStateManager._glBindFramebuffer(GL_FRAMEBUFFER, fbo)
        GlStateManager._glFramebufferTexture2D(GL_FRAMEBUFFER, GL_COLOR_ATTACHMENT0, GL_TEXTURE_2D, glId, 0)
        GlStateManager._glBindFramebuffer(GL_FRAMEBUFFER, 0)

        var skiaSurface: Surface? = null
        var backendTarget: BackendRenderTarget? = null
        SkiaContext.run {
            val bt = BackendRenderTarget.makeGL(width, height, 0, 8, fbo, GL_RGBA8)
            backendTarget = bt
            skiaSurface = Surface.makeFromBackendRenderTarget(
                SkiaContext.directContext,
                bt,
                SurfaceOrigin.BOTTOM_LEFT,
                SurfaceColorFormat.RGBA_8888,
                ColorSpace.sRGB,
            ) ?: error("Failed to create Skia surface")
        }

        active = SurfaceResources(fbo, gpuTexture, gpuTextureView, skiaSurface!!, backendTarget!!)
        skiaTexture.update(gpuTexture, gpuTextureView)
    }

    fun extractRenderState(guiGraphics: GuiGraphicsExtractor, render: (Canvas) -> Unit) {
        val res = beginFrame() ?: return
        val scale = skiaGuiScale(Minecraft.getInstance().window.width, guiGraphics.guiWidth())
        SkiaContext.run {
            SkiaContext.directContext.resetGLAll()
            val canvas = res.skiaSurface.canvas
            canvas.clear(0)
            canvas.save()
            canvas.scale(scale, scale)
            render(canvas.asComposeCanvas())
            canvas.restore()
            res.skiaSurface.flushAndSubmit()
        }
        endFrame(guiGraphics)
    }

    fun paint(guiGraphics: GuiGraphicsExtractor, render: (org.jetbrains.skia.Canvas) -> Unit) {
        val res = beginFrame() ?: return
        val scale = skiaGuiScale(Minecraft.getInstance().window.width, guiGraphics.guiWidth())
        SkiaContext.run {
            SkiaContext.directContext.resetGLAll()
            val canvas = res.skiaSurface.canvas
            canvas.clear(0)
            canvas.save()
            canvas.scale(scale, scale)
            render(canvas)
            canvas.restore()
            res.skiaSurface.flushAndSubmit()
        }
        endFrame(guiGraphics)
    }

    fun close() {
        recordedCalls.clear()
        skiaTexture.clearRefs()
        active?.let { pendingCleanup.add(DeferredCleanup(it)) }
        active = null
        currentWidth = 0
        currentHeight = 0
    }

    private fun beginFrame(): SurfaceResources? {
        val iterator = pendingCleanup.iterator()
        while (iterator.hasNext()) {
            val entry = iterator.next()
            entry.framesRemaining--
            if (entry.framesRemaining <= 0) {
                entry.resources.destroy()
                iterator.remove()
            }
        }
        val window = Minecraft.getInstance().window
        resize(window.width, window.height)
        val res = active ?: return null
        if (!textureRegistered) {
            Minecraft.getInstance().textureManager.register(textureId, skiaTexture)
            textureRegistered = true
        }
        return res
    }

    private fun endFrame(guiGraphics: GuiGraphicsExtractor) {
        guiGraphics.blit(textureId, 0, 0, guiGraphics.guiWidth(), guiGraphics.guiHeight(), 0f, 1f, 1f, 0f)
        while (true) {
            val call = recordedCalls.poll() ?: break
            call.invoke(guiGraphics)
        }
    }

    private data class DeferredCleanup(
        val resources: SurfaceResources,
        var framesRemaining: Int = 4,
    )

    companion object {
        private val counter = AtomicInteger(0)
    }
}

