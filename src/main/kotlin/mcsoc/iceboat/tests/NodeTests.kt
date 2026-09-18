package mcsoc.iceboat.tests

import mcsoc.iceboat.datatrackers.mapData
import mcsoc.iceboat.datatrackers.mapdata.NodeData
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents
import net.minecraft.world.phys.Vec3

fun registerNodeInsertionTest() {
    ServerLifecycleEvents.SERVER_STARTED.register{
        val level = it.overworld()
        val mapData = level.mapData
        
        mapData.initMap("map1", Vec3(0.0, 0.0, 0.0))
        val x = mapData.appendNode("map1", NodeData(Vec3(1.0, 0.0, 0.0)))
        mapData.appendNode("map1", NodeData(Vec3(3.0, 2.0, 1.0)))
        mapData.insertNodeAfter(NodeData(Vec3(2.0, 1.0, 0.0)), x)
        
        mapData.printNodes("map1")
    }
}