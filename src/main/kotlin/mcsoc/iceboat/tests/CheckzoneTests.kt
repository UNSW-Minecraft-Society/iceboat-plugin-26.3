package mcsoc.iceboat.tests

import mcsoc.iceboat.IceboatPlugin
import mcsoc.iceboat.datatrackers.mapData
import mcsoc.iceboat.datatrackers.mapdata.NodeData
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents
import net.minecraft.server.level.ServerLevel
import net.minecraft.world.phys.Vec3


fun registerCheckzoneTests() {
    ServerLifecycleEvents.SERVER_STARTED.register{
        val level = it.overworld().also(::setupNodeTestData)
            .also(::runCheckzoneTest)
            // .also(::runNodeInsertionTest)
            // .also{ IceboatPlugin.LOGGER.info("Tests Complete. See you next time!") }
            .also{ l -> l.mapData.getMapKeys().forEach(l.mapData::clearMap) }
    }
}

fun runCheckzoneTest(level: ServerLevel) {
    val pos = Vec3(20.0, 5.0, 20.0)
    val yRot = 45F
    val before = Vec3(10.0, 5.0, 20.0)
    val after = Vec3(30.0, 5.0, 20.0)

    val testnode = NodeData(pos, yRot)
    val crossed = testnode.passedBy(before, after)
    IceboatPlugin.LOGGER.info("crossed: {}", crossed)
}