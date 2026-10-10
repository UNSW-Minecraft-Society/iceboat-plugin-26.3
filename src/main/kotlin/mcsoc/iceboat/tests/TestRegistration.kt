package mcsoc.iceboat.tests

import mcsoc.iceboat.datatrackers.mapData
import net.minecraft.server.level.ServerLevel
import net.minecraft.world.phys.Vec3


fun registerTests() {
    // registerNodeTests()
    // registerTestCommands()
}

fun setupNodeTestData(level: ServerLevel) {
    val mapData = level.mapData
    mapData.initMap("map1", Vec3(0.0, 0.0, 0.0))
    mapData.initMap("map2", Vec3(0.0, 0.0, 0.0))
    mapData.initMap("map3", Vec3(0.0, 0.0, 0.0))
}