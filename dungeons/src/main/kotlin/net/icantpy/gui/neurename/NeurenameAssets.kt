package net.icantpy.gui.neurename

import net.minecraft.resources.Identifier

/**
 * Textures for the NEU-style item customizer. Asset files are reproduced from NotEnoughUpdates
 * (LGPL-3.0); see NOTICE.txt in this package's resources.
 */
internal object NeurenameAssets {
    private fun id(path: String): Identifier = Identifier.fromNamespaceAndPath("icantpy", "neurename/$path")

    val help = id("help.png")
    val reset = id("itemcustomize/reset.png")

    val bar = id("core/bar.png")
    val barOne = id("core/bar_1.png")
    val barTwo = id("core/bar_2.png")
    val barThree = id("core/bar_3.png")
    val barOn = id("core/bar_on.png")

    val toggleOff = id("core/toggle_off.png")
    val toggleOne = id("core/toggle_1.png")
    val toggleTwo = id("core/toggle_2.png")
    val toggleThree = id("core/toggle_3.png")
    val toggleOn = id("core/toggle_on.png")

    val selectDot = id("core/colour_selector_dot.png")
    val selectBar = id("core/colour_selector_bar.png")
    val selectBarAlpha = id("core/colour_selector_bar_alpha.png")
    val selectChroma = id("core/colour_selector_chroma.png")
}
