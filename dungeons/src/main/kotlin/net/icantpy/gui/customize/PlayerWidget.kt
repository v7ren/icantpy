package net.icantpy.gui.customize

import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.client.gui.components.AbstractWidget
import net.minecraft.client.gui.narration.NarrationElementOutput
import net.minecraft.client.input.MouseButtonEvent
import net.minecraft.client.player.RemotePlayer
import net.minecraft.client.renderer.RenderPipelines
import net.minecraft.client.sounds.SoundManager
import net.minecraft.network.chat.Component
import net.minecraft.util.Mth
import org.joml.Quaternionf
import org.joml.Vector3f

/** Rotatable player preview, ported from Skyblocker's `PlayerWidget`. */
class PlayerWidget(x: Int, y: Int, width: Int, height: Int, private val player: RemotePlayer) :
    AbstractWidget(x, y, width, height, Component.literal("")) {

    private var xRotation = -10f
    private var yRotation = 225f

    init {
        player.yHeadRot = 0f
        player.yHeadRotO = 0f
    }

    override fun onDrag(click: MouseButtonEvent, offsetX: Double, offsetY: Double) {
        super.onDrag(click, offsetX, offsetY)
        xRotation = Mth.clamp(xRotation - offsetY.toFloat() * 2.5f, -50f, 50f)
        yRotation += offsetX.toFloat() * 2.5f
    }

    override fun extractWidgetRenderState(graphics: GuiGraphicsExtractor, mouseX: Int, mouseY: Int, a: Float) {
        CustomizeDraw.panel(graphics, getX(), getY(), getWidth(), getHeight())
        val size = 64f
        val translation = Vector3f(0f, player.bbHeight / 2f + 0.0625f, 0f)
        val rotation = Quaternionf().rotationXYZ(-xRotation * Mth.DEG_TO_RAD, -yRotation * Mth.DEG_TO_RAD, FLIP_ROTATION)
        val renderState = Minecraft.getInstance().entityRenderDispatcher.extractEntity(player, a)
        graphics.entity(renderState, size, translation, rotation, null, getX(), getY(), getRight(), getBottom())
    }

    override fun updateWidgetNarration(builder: NarrationElementOutput) {}

    override fun playDownSound(soundManager: SoundManager) {}

    private companion object {
        const val FLIP_ROTATION = Math.PI.toFloat()
    }
}
