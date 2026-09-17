package mcsoc.iceboat.datatrackers.mapdata

import com.mojang.serialization.Codec
import com.mojang.serialization.codecs.RecordCodecBuilder
import mcsoc.iceboat.IceboatPlugin
import net.minecraft.core.UUIDUtil
import net.minecraft.world.phys.Vec3
import java.util.UUID


// holds node position and index
// next node is grabbed via nodes[index + 1]
data class NodeData(
    val pos: Vec3,
    val index: UByte,
    internal var next: UUID? = null
) {
    companion object {
        val CODEC: Codec<NodeData> = RecordCodecBuilder.create{it.group(
            Vec3.CODEC.fieldOf("position").forGetter(NodeData::pos),
            Codec.BYTE.xmap(Byte::toUByte, UByte::toByte).fieldOf("number").forGetter(NodeData::index),
            UUIDUtil.CODEC.fieldOf("next").forGetter(NodeData::next),
        ).apply(it, ::NodeData)}
    }
}


interface MapDataExposer {
    // used to roughly mark the layout of the map
    val node_map: Map<UUID, NodeData>
    val nodes: Iterable<NodeData> get() = node_map.values
    
    fun getNextNode(node: NodeData): NodeData {
        return node_map[node.next] ?: run {
            IceboatPlugin.LOGGER.error("getNextNode: ", IndexOutOfBoundsException())
            node
        }
    }
}

internal interface MapDataHolder : MapDataExposer {
    override val node_map: MutableMap<UUID, NodeData>
}