package net.icantpy.gui

object Composite {
    private var id: String = "icantpy"

    fun init(modId: String) {
        id = modId.lowercase()
    }

    fun modId(): String = id
}
