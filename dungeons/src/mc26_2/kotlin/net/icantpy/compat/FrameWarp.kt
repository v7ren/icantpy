package net.icantpy.compat

import com.mojang.blaze3d.GpuFormat
import com.mojang.blaze3d.pipeline.RenderPipeline
import com.mojang.blaze3d.systems.RenderSystem
import com.mojang.blaze3d.textures.FilterMode
import com.mojang.blaze3d.textures.GpuTexture
import com.mojang.blaze3d.textures.GpuTextureView
import com.mojang.blaze3d.vertex.VertexConsumer
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.client.gui.navigation.ScreenRectangle
import net.minecraft.client.gui.render.TextureSetup
import net.minecraft.client.renderer.RenderPipelines
import net.minecraft.client.renderer.state.gui.GuiElementRenderState
import net.minecraft.client.renderer.state.gui.GuiRenderState
import org.joml.Matrix3x2f
import java.lang.reflect.Field

/**
 * Persistent GPU copy of the world-only main-target frame, drawn as a warped grid for frame generation.
 * Minecraft 26.2 adapter; the 26.1.2 twin in payload-2612 has the same API.
 *
 * [draw] submits one custom GUI element. GuiGraphicsExtractor keeps its GuiRenderState private, so the
 * element is added through a reflective field read. If that field disappears, [draw] throws and the caller
 * disables frame generation.
 */
object FrameWarp {
    /** Grid resolution: each cell is one textured quad, so warping error stays small across the screen. */
    const val COLUMNS = 24
    const val ROWS = 14

    /**
     * Extra cell rings past each texture border. When the camera turns, the warped image no longer covers the
     * whole screen; those pixels come from these rings, whose UVs are clamped to the edge, so they show stretched
     * edge pixels instead of stale or undefined memory.
     */
    const val EDGE_RINGS = 2
    private const val GRID_STRIDE = COLUMNS + 1 + 2 * EDGE_RINGS
    private const val VERTEX_COUNT = GRID_STRIDE * (ROWS + 1 + 2 * EDGE_RINGS)
    private const val WHITE = -1

    /**
     * Same vertex format and shader as GUI_TEXTURED, but without blending: the warped frame overwrites the
     * target instead of mixing with the previous frame (its texture alpha could be below 1 on world pixels).
     */
    private val OPAQUE_PIPELINE = RenderPipelines.GUI_OPAQUE_TEXTURED_BACKGROUND

    /**
     * Render-target textures are stored bottom-up, so screen top (grid row 0) samples V=1 and screen bottom
     * samples V=0. Unverified in-game.
     */
    private const val FLIP_V = true

    private var texture: GpuTexture? = null
    private var view: GpuTextureView? = null
    private var format: GpuFormat? = null

    private val guiRenderStateField: Field by lazy {
        GuiGraphicsExtractor::class.java.getDeclaredField("guiRenderState").apply { isAccessible = true }
    }

    /**
     * Copies the main render target into the history texture. Returns true when the copy was made this call
     * (the texture now holds the current world frame). Returns false when the texture was just (re)created
     * for a new size or format, because nothing was copied and there is nothing to draw yet.
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

    /**
     * Draws the captured frame as a [COLUMNS] x [ROWS] grid plus [EDGE_RINGS] edge rings on every side. [mapper]
     * receives a normalized captured-frame point (u right, v down; inside the frame 0..1, outside it in the edge
     * rings) and returns its GUI-pixel position on screen, or null to skip the cells that touch that vertex
     * (for example a point behind the camera).
     */
    fun draw(graphics: GuiGraphicsExtractor, mapper: (u: Float, v: Float) -> FloatArray?) {
        val textureView = view ?: return
        val width = graphics.guiWidth()
        val height = graphics.guiHeight()
        val sampler = RenderSystem.getSamplerCache().getClampToEdge(FilterMode.LINEAR)
        val area = ScreenRectangle(0, 0, width, height)
        val state = guiRenderState(graphics)
        // Base layer: the unwarped world copy, restored into the main target as an exact GPU copy (alpha included)
        // when this element's vertices are built, i.e. in the render phase after the frame clear and before any GUI
        // draw. Its quads then cover cells the warp skips.
        state.addGuiElement(
            WarpedFrameRenderState(
                OPAQUE_PIPELINE,
                TextureSetup.singleTexture(textureView, sampler),
                area,
                buildGrid { u, v -> floatArrayOf(u * width, v * height) },
                restoreBase = true,
            ),
        )
        state.addGuiElement(
            WarpedFrameRenderState(
                OPAQUE_PIPELINE,
                TextureSetup.singleTexture(textureView, sampler),
                area,
                buildGrid(mapper),
            ),
        )
    }

    fun close() {
        view?.close()
        texture?.close()
        view = null
        texture = null
        format = null
    }

    private fun buildGrid(mapper: (u: Float, v: Float) -> FloatArray?): WarpedGrid {
        val grid = WarpedGrid(FloatArray(VERTEX_COUNT), FloatArray(VERTEX_COUNT), BooleanArray(VERTEX_COUNT))
        for (row in -EDGE_RINGS..ROWS + EDGE_RINGS) {
            for (column in -EDGE_RINGS..COLUMNS + EDGE_RINGS) {
                val index = vertexIndex(column, row)
                val point = mapper(column.toFloat() / COLUMNS, row.toFloat() / ROWS) ?: continue
                if (!point[0].isFinite() || !point[1].isFinite()) continue
                grid.screenX[index] = point[0]
                grid.screenY[index] = point[1]
                grid.valid[index] = true
            }
        }
        return grid
    }

    /**
     * Copies the captured frame into the main render target. position_tex_color discards fragments whose alpha is
     * 0, and the fog-filled sky (the horizon band and around the sun/moon) has alpha 0 in the captured frame, so a
     * drawn base layer left those pixels black (the frame clear) and the sky flickered. A texture copy keeps them.
     */
    private fun restoreBaseToMainTarget() {
        val history = texture ?: return
        val target = Minecraft.getInstance().gameRenderer.mainRenderTarget().colorTexture ?: return
        val width = history.getWidth(0)
        val height = history.getHeight(0)
        if (target.getWidth(0) != width || target.getHeight(0) != height) return
        RenderSystem.getDevice().createCommandEncoder()
            .copyTextureToTexture(history, target, 0, 0, 0, 0, 0, width, height)
    }

    private fun guiRenderState(graphics: GuiGraphicsExtractor): GuiRenderState =
        guiRenderStateField.get(graphics) as GuiRenderState

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
            "icantpy frame gen history",
            GpuTexture.USAGE_COPY_DST or GpuTexture.USAGE_COPY_SRC or GpuTexture.USAGE_TEXTURE_BINDING,
            sourceFormat,
            width,
            height,
            1,
            1,
        )
        texture = created
        view = device.createTextureView(created)
        format = sourceFormat
        return true
    }

    /** Grid coordinates may run from -[EDGE_RINGS] to [COLUMNS] / [ROWS] + [EDGE_RINGS]. */
    private fun vertexIndex(column: Int, row: Int): Int =
        (row + EDGE_RINGS) * GRID_STRIDE + (column + EDGE_RINGS)

    /** Texture coordinates are clamped to the frame, so edge rings repeat the border pixels. */
    private fun textureU(column: Int): Float = (column.toFloat() / COLUMNS).coerceIn(0f, 1f)

    private fun textureV(row: Int): Float {
        val v = (row.toFloat() / ROWS).coerceIn(0f, 1f)
        return if (FLIP_V) 1f - v else v
    }

    private class WarpedGrid(val screenX: FloatArray, val screenY: FloatArray, val valid: BooleanArray)

    /** Emits one quad per fully valid cell, in the same corner order as BlitRenderState. */
    private class WarpedFrameRenderState(
        private val pipeline: RenderPipeline,
        private val textureSetup: TextureSetup,
        private val area: ScreenRectangle,
        private val grid: WarpedGrid,
        private val restoreBase: Boolean = false,
    ) : GuiElementRenderState {
        override fun buildVertices(vertexConsumer: VertexConsumer) {
            if (restoreBase) restoreBaseToMainTarget()
            val identity = Matrix3x2f()
            for (row in -EDGE_RINGS until ROWS + EDGE_RINGS) {
                for (column in -EDGE_RINGS until COLUMNS + EDGE_RINGS) {
                    val topLeft = vertexIndex(column, row)
                    val bottomLeft = vertexIndex(column, row + 1)
                    val bottomRight = vertexIndex(column + 1, row + 1)
                    val topRight = vertexIndex(column + 1, row)
                    if (!grid.valid[topLeft] || !grid.valid[bottomLeft] ||
                        !grid.valid[bottomRight] || !grid.valid[topRight]
                    ) {
                        continue
                    }
                    emit(vertexConsumer, identity, topLeft, column, row)
                    emit(vertexConsumer, identity, bottomLeft, column, row + 1)
                    emit(vertexConsumer, identity, bottomRight, column + 1, row + 1)
                    emit(vertexConsumer, identity, topRight, column + 1, row)
                }
            }
        }

        private fun emit(consumer: VertexConsumer, identity: Matrix3x2f, index: Int, column: Int, row: Int) {
            consumer.addVertexWith2DPose(identity, grid.screenX[index], grid.screenY[index])
                .setUv(textureU(column), textureV(row))
                .setColor(WHITE)
        }

        override fun pipeline(): RenderPipeline = pipeline

        override fun textureSetup(): TextureSetup = textureSetup

        override fun scissorArea(): ScreenRectangle = area

        override fun bounds(): ScreenRectangle = area
    }
}
