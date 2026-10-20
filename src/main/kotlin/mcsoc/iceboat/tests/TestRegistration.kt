package mcsoc.iceboat.tests

import mcsoc.iceboat.datatrackers.mapData
import net.minecraft.server.level.ServerLevel
import net.minecraft.world.phys.Vec3


fun registerTests() {
    // registerNodeTests()
    // registerTestCommands()
    registerCheckzoneTests()
}

fun setupNodeTestData(level: ServerLevel) {
    val mapData = level.mapData
    mapData.initMap("map1", Vec3(0.0, 0.0, 0.0))
    mapData.initMap("map2", Vec3(0.0, 0.0, 0.0))
    mapData.initMap("map3", Vec3(0.0, 0.0, 0.0))
}

fun clearNodeTestData(level: ServerLevel) {
    for (map in listOf("map1", "map2", "map3")) level.mapData.clearMap(map)
}