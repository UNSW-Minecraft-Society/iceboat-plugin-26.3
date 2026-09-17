package mcsoc.iceboat.datatrackers.mapdata

import com.mojang.serialization.Codec
import com.mojang.serialization.codecs.RecordCodecBuilder
import mcsoc.iceboat.IceboatPlugin
import mcsoc.iceboat.utils.format
import net.minecraft.core.UUIDUtil
import net.minecraft.server.level.ServerLevel
import net.minecraft.world.phys.Vec3
import java.util.UUID
import kotlin.time.Duration


// holds node position and index
// next node is grabbed via nodes[index + 1]
data class NodeData(
    val pos: Vec3,
    internal var index: UInt? = null,
    var fastestPlayer: UUID? = null,
    internal var next: UUID? = null
) {
    companion object {
        val CODEC: Codec<NodeData> = RecordCodecBuilder.create{it.group(
            Vec3.CODEC.fieldOf("position").forGetter(NodeData::pos),
            Codec.INT.xmap(Int::toUInt, UInt::toInt).fieldOf("number").forGetter(NodeData::index),
            UUIDUtil.CODEC.fieldOf("fastestPlayer").forGetter(NodeData::fastestPlayer),
            UUIDUtil.CODEC.fieldOf("next").forGetter(NodeData::next),
        ).apply(it, ::NodeData)}
    }
    
    override fun toString(): String = 
        "Node $index:\n" +
        "  pos: ${pos.format},\n" +
        "  fastest player: $fastestPlayer\n" +
        "  next node: $next"
}


interface MapDataExposer {
    // used to roughly mark the layout of the map
    val node_map: Map<UUID, NodeData>
    val nodes: Iterable<NodeData> get() = node_map.values
    fun NodeData.next(): NodeData = node_map[this.next] ?: throw StringIndexOutOfBoundsException()
    
    fun trackPlayerTime(node: UUID, level: ServerLevel, player: UUID)
    fun insertNodeAfter(node: UUID?, new: NodeData): UUID
    fun printNodes() {
        var node = nodes.firstOrNull() ?: return IceboatPlugin.LOGGER.info("node: {}", null)
        while (node.index != 0u) {
            node = node.next()
        }
        do {
            IceboatPlugin.LOGGER.info("node: {}", node)
            node = node.next()
        } while (node.index != 0u)
    }
}

internal interface MapDataHolder : MapDataExposer {
    override val node_map: MutableMap<UUID, NodeData>
    
    private fun NodeData.propogateIndex() {
        val this_index = this.index ?: return
        val next = this.next() ?: return 
        val next_index = next.index ?: return 
        if (next_index > this_index) {
            next.index = this_index + 1u
            next.propogateIndex()
        }
    }
    
    // NB: doesn't check if newNode has a next val already
    private fun NodeData.insertNodeAfter(new: UUID, newNode: NodeData) {
        newNode.next = this.next
        this.next = new
        this.propogateIndex()
    }
    
    // NB: doesn't check for duplicates
    private fun addNode(new: NodeData, index: UInt? = null): UUID = UUID.randomUUID().also{
        new.index = index
        node_map[it] = new
    }
    
    private fun NodeData.getPlayerTime(level: ServerLevel, node: UUID): Duration? = 
        getPlayerTime(level, this.fastestPlayer, node)
        
    override fun trackPlayerTime(node: UUID, level: ServerLevel, player: UUID) {
        node_map[node]?.let{
            val playerTime = getPlayerTime(level, player, node) ?: Duration.INFINITE
            val currFastestTime = it.getPlayerTime(level, node) ?: Duration.INFINITE
            
            if (currFastestTime > playerTime) it.fastestPlayer = player
        }
    }
    
    override fun insertNodeAfter(node: UUID?, new: NodeData): UUID {
        if (node == null || node_map.isEmpty()) return addNode(new, 0u).also{node_map[it]?.next = it}
        val oldNode = node_map[node] ?: throw IndexOutOfBoundsException("1")
        return addNode(new, (oldNode.index ?: 0u) + 1u)
            .also{oldNode.insertNodeAfter(it, new)}
    }
    
    fun getPlayerTime(level: ServerLevel, player: UUID?, node: UUID): Duration?
}