package mcsoc.iceboat.datatrackers.mapdata

import com.mojang.serialization.Codec
import com.mojang.serialization.codecs.RecordCodecBuilder
import mcsoc.iceboat.IceboatPlugin
import mcsoc.iceboat.utils.format
import net.minecraft.core.UUIDUtil
import net.minecraft.server.level.ServerLevel
import net.minecraft.world.entity.player.Player
import net.minecraft.world.phys.Vec3
import java.util.UUID
import kotlin.time.Duration


// holds node position and index
// next node is grabbed via nodes[index + 1]
open class NodeData(
    val pos: Vec3,
    val yawToNext: Float = 0F,
    internal var _internal_index: UInt? = null,
    var fastestPlayer: UUID? = null,
    internal var next: UUID? = null
) {
    val index: UInt?
        get() = _internal_index
        
    companion object {
        const val DETECTION_RANGE_SQ = 25.0
        val CODEC: Codec<NodeData> = RecordCodecBuilder.create{it.group(
            Vec3.CODEC.fieldOf("position").forGetter(NodeData::pos),
            Codec.FLOAT.fieldOf("yaw").forGetter(NodeData::yawToNext),
            Codec.INT.xmap(Int::toUInt, UInt::toInt).fieldOf("number").forGetter(NodeData::_internal_index),
            UUIDUtil.CODEC.fieldOf("fastestPlayer").forGetter(NodeData::fastestPlayer),
            UUIDUtil.CODEC.fieldOf("next").forGetter(NodeData::next),
        ).apply(it, ::NodeData)}
    }
    
    fun inRangeOf(p: Vec3): Boolean = pos.distanceToSqr(p) <= DETECTION_RANGE_SQ
    fun inRangeOf(p: Player): Boolean = inRangeOf(p.position())
    
    override fun toString(): String = 
        "Node $_internal_index:\n" +
        "             pos: ${pos.format}\n" +
        "     yaw to next: $yawToNext\n" +
        "  fastest player: $fastestPlayer\n" +
        "       next node: $next"
}


interface MapDataExposer {
    // used to roughly mark the layout of the map
    fun getNode(node: UUID): NodeData?
    fun getNextNode(node: UUID): NodeData? = getNode(node)?.next?.let(::getNode)
    fun getMapKeys(): Iterable<String>
    fun getFirstMapNode(id: String): UUID?
    fun trackPlayerTime(node: UUID, level: ServerLevel, player: UUID)
    fun insertNodeAfter(mapId: String, new: NodeData, index: UInt? = null): UUID
    fun insertNodeAfter(node: UUID, new: NodeData): UUID?
    fun appendNode(id: String, new: NodeData): UUID
    fun initMap(id: String, first_pos: Vec3 = Vec3.ZERO): NodeData
    fun clearMap(id: String)

    fun NodeData.next(): NodeData = this.next?.let{ getNode(it) } ?: throw StringIndexOutOfBoundsException()
    
    fun printNodes(map: String, print: (NodeData) -> Unit = { IceboatPlugin.LOGGER.info("node: {}", it) }) {
        var node: NodeData = getFirstMapNodeInternal(map) ?: run { print(null); return }
        do {
            print(node)
            node = node.next()
        } while (node._internal_index != 0u)
    }
    
    fun getAllMapNodes(id: String): Iterable<Pair<UUID, NodeData>> {
        val res: MutableCollection<Pair<UUID, NodeData>> = mutableListOf()
        
        var nodeId = getFirstMapNode(id) ?: return res
        do {
            var node = getNode(nodeId) ?: throw StringIndexOutOfBoundsException("1")
            res.add(Pair(nodeId, node))
            nodeId = node.next ?: throw StringIndexOutOfBoundsException("4")
            val index = getNode(nodeId)?._internal_index ?: throw StringIndexOutOfBoundsException("2")
        } while (index > 0u)
        
        return res
    }
}

private fun MapDataExposer.getFirstMapNodeInternal(id: String): NodeData? {
    return getFirstMapNode(id)?.let{ getNode(it) }
}

internal interface MapDataHolder : MapDataExposer {
    val node_map: MutableMap<UUID, NodeData>
    val map_heads: MutableMap<String, UUID>
    
    override fun getFirstMapNode(id: String): UUID? = map_heads[id]
    override fun getNode(node: UUID): NodeData? = node_map[node]
    override fun getMapKeys(): Iterable<String> = map_heads.keys
    
    override fun clearMap(id: String) {
        getFirstMapNodeInternal(id)?.unlinkChain()
    }
    override fun initMap(id: String, first_pos: Vec3): NodeData {
        return getNode(insertNodeAfter(id, NodeData(first_pos)))!!
    }
    
    override fun appendNode(id: String, new: NodeData): UUID {
        var node = getFirstMapNodeInternal(id) ?: return insertNodeAfter(id, new)
        while (node.next().index != 0u) node = node.next()
        
        return node.insertNodeAfter(new)
    }
    
    private fun NodeData.unlinkChain() {
        val nextNode = this.next()
        if (nextNode.index != 0u) {
            nextNode.unlinkChain()
        }
        IceboatPlugin.LOGGER.info("\"unlinkChain\": forgot node ${nextNode.index}")
        this.next = null
    }
    
    private fun NodeData.propogateIndex() {
        val this_index = this._internal_index ?: return
        val next = this.next() 
        val next_index = next._internal_index ?: return 
        if (next_index > this_index) {
            next._internal_index = this_index + 1u
            next.propogateIndex()
        }
    }
    
    // NB: doesn't check if newNode has a next val already
    private fun NodeData.insertNodeAfter(newNode: NodeData): UUID {
        val index = this.index ?: throw UninitializedPropertyAccessException("NodeData.insertNodeAfter")
        val new = addNode(newNode, index + 1u)
        newNode.next = this.next
        this.next = new
        this.propogateIndex()
        
        return new
    }
    
    // NB: doesn't check for duplicates
    private fun addNode(new: NodeData, index: UInt? = null): UUID = UUID.randomUUID().also{
        new._internal_index = index
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
    
    // if index is null, insert it as new head
    override fun insertNodeAfter(mapId: String, new: NodeData, index: UInt?): UUID {
        val mapHead = getFirstMapNodeInternal(mapId)
        
        // insert node as new head
        if (index == null || mapHead == null) {
            val newNodeId = addNode(new, 0u)
            map_heads[mapId] = newNodeId
            mapHead?.let{
                var node = it
                // seek to end of node chain
                while (it.next().index != 0u) node = node.next()
                node.next = newNodeId
            } ?: run { new.next = newNodeId }
            return newNodeId
        }
        
        // seek to specified index
        var node: NodeData = mapHead
        for (i in 0u until index) { node = node.next() }
        return node.insertNodeAfter(new)
    }

    override fun insertNodeAfter(node: UUID, new: NodeData): UUID? {
        return getNode(node)?.insertNodeAfter(new)
    }
    
    fun getPlayerTime(level: ServerLevel, player: UUID?, node: UUID): Duration?
}