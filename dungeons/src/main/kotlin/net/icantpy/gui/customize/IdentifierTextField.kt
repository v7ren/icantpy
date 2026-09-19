package net.icantpy.gui.customize

import net.minecraft.ChatFormatting
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.components.EditBox
import net.minecraft.network.chat.Component
import net.minecraft.network.chat.Style
import net.minecraft.resources.Identifier
import net.minecraft.util.FormattedCharSequence

/** Identifier text field that reports a parsed identifier or null when blank, ported from Skyblocker. */
class IdentifierTextField(width: Int, height: Int, private val callback: (Identifier?) -> Unit) :
    EditBox(Minecraft.getInstance().font, width, height, Component.empty()) {

    private var lastValid = ""
    private var valid = false

    init {
        setResponder { onChanged(it) }
        setMaxLength(100)
        addFormatter { string, _ -> FormattedCharSequence.forward(string, if (valid) Style.EMPTY else Style.EMPTY.applyFormat(ChatFormatting.RED)) }
    }

    private fun onChanged(s: String) {
        val identifier = Identifier.tryParse(s)
        valid = true
        if (s.isBlank()) {
            callback(null)
            lastValid = ""
        } else if (identifier != null) {
            callback(identifier)
            lastValid = s
        } else {
            valid = false
        }
    }

    override fun setFocused(focused: Boolean) {
        super.setFocused(focused)
        if (!focused && lastValid != getValue()) {
            setValue(lastValid)
        }
    }
}
