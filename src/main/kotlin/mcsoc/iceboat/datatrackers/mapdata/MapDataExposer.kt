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
    fun getNode(node: UUID): NodeData?
    fun getFirstMapNode(id: String): UUID?
    fun trackPlayerTime(node: UUID, level: ServerLevel, player: UUID)
    fun insertNodeAfter(new: NodeData, node: UUID? = null): UUID
    fun appendNode(id: String, new: NodeData): UUID
    fun initMap(id: String, first_pos: Vec3 = Vec3.ZERO): NodeData

    fun NodeData.next(): NodeData = this.next?.let{ getNode(it) } ?: throw StringIndexOutOfBoundsException()
    
    private fun getFirstMapNodeInternal(id: String): NodeData {
        return getFirstMapNode(id)?.let{ getNode(it) } ?: initMap(id)
    }
    
    fun printNodes(map: String) {
        var node = getFirstMapNodeInternal(map)
        do {
            IceboatPlugin.LOGGER.info("node: {}", node)
            node = node.next()
        } while (node.index != 0u)
    }
    
    fun getAllMapNodes(id: String): Iterable<UUID> {
        val res: MutableCollection<UUID> = mutableListOf()
        
        var nodeId = getFirstMapNode(id) ?: return res
        do {
            res.add(nodeId)
            var node = getNode(nodeId) ?: throw StringIndexOutOfBoundsException("1")
            nodeId = node.next ?: throw StringIndexOutOfBoundsException("4")
            val index = getNode(nodeId)?.index ?: throw StringIndexOutOfBoundsException("2")
        } while (index > 0u)
        
        return res
    }
}

internal interface MapDataHolder : MapDataExposer {
    val node_map: MutableMap<UUID, NodeData>
    val map_heads: MutableMap<String, UUID>
    
    override fun getFirstMapNode(id: String): UUID? = map_heads[id]
    override fun getNode(node: UUID): NodeData? = node_map[node]
    
    override fun initMap(id: String, first_pos: Vec3): NodeData {
        return getNode(insertNodeAfter(NodeData(first_pos)).also{ map_heads[id] = it })!!
    }
    
    override fun appendNode(id: String, new: NodeData): UUID {
        val firstNode = getNode(getFirstMapNode(id) ?: run {
            initMap(id)
            getFirstMapNode(id)!!
        })!!
        
        var node = firstNode
        do {
            node = node.next()
            val index = node.next().next().index ?: throw IllegalAccessError()
        } while (index > 0u)
        
        return insertNodeAfter(new, node.next)
    }
    
    private fun NodeData.propogateIndex() {
        val this_index = this.index ?: return
        val next = this.next() 
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
        getNode(node)?.let{
            val playerTime = getPlayerTime(level, player, node) ?: return
            val currFastestTime = it.getPlayerTime(level, node) ?: Duration.INFINITE
            
            if (currFastestTime > playerTime) it.fastestPlayer = player
        }
    }
    
    override fun insertNodeAfter(new: NodeData, node: UUID?): UUID {
        if (node == null || node_map.isEmpty()) return addNode(new, 0u).also{node_map[it]?.next = it}
        val oldNode = node_map[node] ?: throw IndexOutOfBoundsException("1")
        return addNode(new, (oldNode.index ?: 0u) + 1u)
            .also{oldNode.insertNodeAfter(it, new)}
    }
    
    fun getPlayerTime(level: ServerLevel, player: UUID?, node: UUID): Duration?
}