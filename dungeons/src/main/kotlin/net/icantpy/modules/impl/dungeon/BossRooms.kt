package net.icantpy.modules.impl.dungeon

object BossRooms {
    fun floorNumber(floor: DungeonFloor): Int = when (floor) {
        DungeonFloor.NONE -> 0
        DungeonFloor.F1, DungeonFloor.M1 -> 1
        DungeonFloor.F2, DungeonFloor.M2 -> 2
        DungeonFloor.F3, DungeonFloor.M3 -> 3
        DungeonFloor.F4, DungeonFloor.M4 -> 4
        DungeonFloor.F5, DungeonFloor.M5 -> 5
        DungeonFloor.F6, DungeonFloor.M6 -> 6
        DungeonFloor.F7, DungeonFloor.M7 -> 7
    }

    fun contains(floor: DungeonFloor, x: Double, z: Double): Boolean = when (floorNumber(floor)) {
        1 -> x > -71 && z > -39
        2, 3, 4 -> x > -39 && z > -39
        5, 6 -> x > -39 && z > -7
        7 -> x > -40 && z > -40
        else -> false
    }
}
