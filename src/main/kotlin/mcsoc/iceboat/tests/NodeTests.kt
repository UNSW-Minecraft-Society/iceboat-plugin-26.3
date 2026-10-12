package mcsoc.iceboat.tests

import mcsoc.iceboat.IceboatPlugin
import mcsoc.iceboat.datatrackers.mapData
import mcsoc.iceboat.datatrackers.mapdata.NodeData
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents
import net.minecraft.server.level.ServerLevel
import net.minecraft.world.phys.Vec3


fun registerNodeTests() {
    ServerLifecycleEvents.SERVER_STARTED.register{
        val level = it.overworld().also(::setupNodeTestData)
            // .also(::runNodeIterableAccessTest)
            // .also(::runNodeInsertionTest)
            // .also{ IceboatPlugin.LOGGER.info("Tests Complete. See you next time!") }
            .also{ l -> l.mapData.getMapKeys().forEach(l.mapData::clearMap) }
    }
}

fun runNodeInsertionTest(level: ServerLevel) {
    val mapData = level.mapData
    
    IceboatPlugin.LOGGER.info("INSERTION TEST")
    IceboatPlugin.LOGGER.info("  APPEND")
    val x = mapData.appendNode("map1", NodeData(Vec3(1.0, 0.0, 0.0)))
    
    IceboatPlugin.LOGGER.info("  APPEND")
    mapData.appendNode("map1", NodeData(Vec3(4.0, 3.0, 2.0)))
    
    IceboatPlugin.LOGGER.info("  INSERT BY INDEX")
    mapData.insertNodeAfter("map1", NodeData(Vec3(3.0, 2.0, 1.0)), 1u)
    
    IceboatPlugin.LOGGER.info("  INSERT BY ID")
    mapData.insertNodeAfter(x, NodeData(Vec3(2.0, 1.0, 0.0)))
    
    mapData.printNodes("map1")
}

fun runNodeIterableAccessTest(level: ServerLevel) {
    val mapData = level.mapData
    
    IceboatPlugin.LOGGER.info("ITERABLE TEST")   
    IceboatPlugin.LOGGER.info("  APPEND")
    mapData.appendNode("map2", NodeData(Vec3(1.0, 0.0, 0.0)))
    IceboatPlugin.LOGGER.info("  APPEND")
    mapData.appendNode("map2", NodeData(Vec3(2.0, 1.0, 0.0)))
    IceboatPlugin.LOGGER.info("  APPEND")
    mapData.appendNode("map2", NodeData(Vec3(3.0, 2.0, 1.0)))

    IceboatPlugin.LOGGER.info("  PRINT")
    mapData.printNodes("map2")
}