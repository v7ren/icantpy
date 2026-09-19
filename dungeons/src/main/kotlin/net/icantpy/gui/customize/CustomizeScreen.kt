package net.icantpy.gui.customize

import net.icantpy.compat.McCompat
import net.icantpy.gui.McUi
import net.icantpy.cosmetics.items.CustomRename
import net.icantpy.cosmetics.items.CustomRenameConfig
import net.minecraft.ChatFormatting
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.client.gui.components.Button
import net.minecraft.client.gui.components.tabs.TabManager
import net.minecraft.client.gui.components.tabs.TabNavigationBar
import net.minecraft.client.gui.layouts.LinearLayout
import net.minecraft.client.gui.navigation.ScreenRectangle
import net.minecraft.client.gui.screens.Screen
import net.minecraft.client.input.MouseButtonEvent
import net.minecraft.network.chat.Component
import net.minecraft.world.item.ItemStack

/** Skyblocker-style item customizer with Armor and Item tabs. */
class CustomizeScreen(private val previousScreen: Screen?, private val item: Boolean) :
    Screen(Component.literal("icantpy Customization").withStyle(ChatFormatting.GRAY).withStyle { it.withShadowColor(0) }) {

    private val tabManager = TabManager({ addRenderableWidget(it) }, { removeWidget(it) })
    private val footerLayout = LinearLayout.horizontal().spacing(5)
    private var armorTab: ArmorTab? = null
    private var tabNavigation: TabNavigationBar? = null
    private val snapshot: CustomRenameConfig = CustomRename.config

    override fun isPauseScreen(): Boolean = false

    override fun tick() {
        armorTab?.tick()
    }

    override fun init() {
        super.init()
        val armor = ArmorTab(this)
        armorTab = armor
        tabNavigation = McCompat.buildTabBar(tabManager, width, listOf(armor, ItemTab(this)))
        val i = tabNavigation!!.getRectangle().bottom()
        McCompat.arrangeTabBar(tabNavigation!!, width)
        tabManager.setTabArea(ScreenRectangle(0, i, width, height - i - 30))
        tabNavigation!!.selectTab(if (item) 1 else 0, false)
        addRenderableWidget(tabNavigation!!)

        footerLayout.addChild(Button.builder(Component.literal("Cancel")) { cancel() }.build())
        footerLayout.addChild(Button.builder(Component.literal("Done")) { onClose() }.build())
        footerLayout.arrangeElements()
        repositionElements()
    }

    /** No-op kept for parity with Skyblocker; the whole config is snapshotted at open. */
    fun backupConfigs(stack: ItemStack) {}

    private fun cancel() {
        CustomRename.restore(snapshot)
        onClose()
    }

    override fun mouseClicked(click: MouseButtonEvent, doubled: Boolean): Boolean {
        val b = super.mouseClicked(click, doubled)
        if (!b) setFocused(null)
        return b
    }

    override fun repositionElements() {
        val i = tabNavigation!!.getRectangle().bottom()
        McCompat.arrangeTabBar(tabNavigation!!, width)
        footerLayout.setPosition((width - footerLayout.getWidth()) / 2, height - footerLayout.getHeight() - 5)
        tabManager.setTabArea(ScreenRectangle(0, i, width, footerLayout.getY() - i - 2))
    }

    override fun extractRenderState(graphics: GuiGraphicsExtractor, mouseX: Int, mouseY: Int, a: Float) {
        super.extractRenderState(graphics, mouseX, mouseY, a)
    }

    override fun onClose() {
        CustomRename.persist()
        McUi.setScreen(minecraft, previousScreen)
    }
}
