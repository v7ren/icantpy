package net.icantpy.dungeon.leap

import net.minecraft.world.phys.AABB

enum class OrientSpot {
    ANY,
    GOLDOR_S1,
    GOLDOR_S2,
    GOLDOR_S3,
    GOLDOR_S4,
    EE2,
    HEE2,
    SS,
    EE3,
    CORE,
    ;

    val p3Section: Int?
        get() = when (this) {
            GOLDOR_S1, SS -> 1
            GOLDOR_S2, EE2, HEE2 -> 2
            GOLDOR_S3, EE3 -> 3
            GOLDOR_S4, CORE -> 4
            ANY -> null
        }

    fun token(): String = when (this) {
        ANY -> "any"
        GOLDOR_S1, SS -> "sss"
        GOLDOR_S2 -> "s2"
        GOLDOR_S3 -> "s3"
        GOLDOR_S4 -> "s4"
        EE2 -> "ee2"
        HEE2 -> "hee2"
        EE3 -> "ee3"
        CORE -> "core"
    }

    companion object {
        val PRIORITY_TOKENS: List<String> = listOf("sss", "ee2", "ee3", "s4", "core")

        fun fromToken(raw: String): OrientSpot? = when (raw.lowercase().trim()) {
            "any" -> ANY
            "s1", "goldor_s1", "ss", "sss" -> SS
            "s2", "goldor_s2" -> GOLDOR_S2
            "s3", "goldor_s3" -> GOLDOR_S3
            "s4", "goldor_s4" -> GOLDOR_S4
            "ee2" -> EE2
            "hee2" -> HEE2
            "ee3" -> EE3
            "core" -> CORE
            else -> null
        }

        fun matches(actual: OrientSpot, preferred: OrientSpot): Boolean = when (preferred) {
            ANY -> true
            EE2, HEE2 -> actual == EE2 || actual == HEE2
            SS, GOLDOR_S1 -> actual == SS || actual == GOLDOR_S1
            else -> actual == preferred
        }

        fun sameToken(left: String, right: String): Boolean {
            val a = fromToken(left) ?: return left.equals(right, ignoreCase = true)
            val b = fromToken(right) ?: return false
            return matches(a, b) && matches(b, a)
        }
    }
}

object LeapOrientSpots {
    private val p3Sections = arrayOf(
        box(90.0, 158.0, 123.0, 111.0, 105.0, 32.0),
        box(16.0, 158.0, 122.0, 111.0, 105.0, 143.0),
        box(19.0, 158.0, 48.0, -3.0, 106.0, 142.0),
        box(91.0, 158.0, 50.0, -3.0, 106.0, 30.0),
    )

    private val ee2Box = box(57.0, 108.0, 130.0, 59.0, 110.0, 132.0)
    private val hee2Box = box(57.0, 132.0, 138.0, 62.0, 133.0, 140.0)

    fun findP3Section(x: Double, y: Double, z: Double): Int {
        for (index in p3Sections.indices) {
            if (p3Sections[index].contains(x, y, z)) return index + 1
        }
        return 0
    }

    fun atSpot(spot: OrientSpot, x: Double, y: Double, z: Double): Boolean = when (spot) {
        OrientSpot.ANY -> true
        OrientSpot.EE2 -> ee2Box.contains(x, y, z) && findP3Section(x, y, z) == 2
        OrientSpot.HEE2 -> hee2Box.contains(x, y, z) || atSpot(OrientSpot.EE2, x, y, z)
        else -> spot.p3Section?.let { findP3Section(x, y, z) == it } == true
    }

    fun selfSection(x: Double, y: Double, z: Double, override: OrientSpot?): Int {
        if (override != null && override != OrientSpot.ANY) {
            return override.p3Section ?: 0
        }
        return findP3Section(x, y, z)
    }

    fun cursorQuadrant(screenW: Int, screenH: Int, mouseX: Int, mouseY: Int): Int =
        (if (mouseY >= screenH / 2) 2 else 0) + (if (mouseX >= screenW / 2) 1 else 0)

    fun centerHit(
        screenW: Int,
        screenH: Int,
        mouseX: Int,
        mouseY: Int,
        scale: Float = 1f,
        width: Int = 180,
        height: Int = 64,
    ): Boolean {
        val boxW = width * scale
        val boxH = height * scale
        val left = screenW / 2f - boxW / 2f
        val top = screenH / 2f - boxH / 2f
        return mouseX.toFloat() >= left &&
            mouseX.toFloat() < left + boxW &&
            mouseY.toFloat() >= top &&
            mouseY.toFloat() < top + boxH
    }

    private fun box(
        x1: Double,
        y1: Double,
        z1: Double,
        x2: Double,
        y2: Double,
        z2: Double,
    ): AABB = AABB(
        minOf(x1, x2),
        minOf(y1, y2),
        minOf(z1, z2),
        maxOf(x1, x2),
        maxOf(y1, y2),
        maxOf(z1, z2),
    )
}
