package mcsoc.iceboat.datatrackers.mapdata

import com.mojang.serialization.Codec
import com.mojang.serialization.codecs.RecordCodecBuilder
import mcsoc.iceboat.IceboatPlugin
import mcsoc.iceboat.utils.CODEC
import mcsoc.iceboat.utils.FLOAT_PI
import mcsoc.iceboat.utils.format
import net.minecraft.core.UUIDUtil
import net.minecraft.world.phys.Vec3
import org.joml.Matrix2d
import org.joml.Vector2d
import org.joml.minus
import org.joml.times
import java.util.Optional
import java.util.UUID
import kotlin.math.PI
import kotlin.math.sqrt
import kotlin.properties.Delegates


// holds node position and index
// next node is grabbed via nodes[index + 1]
data class NodeData(
    val pos: Vec3,
    val yawToNext: Float = 0F,
    val range: Double = sqrt(DETECTION_RANGE_SQ)
) {
    private val c1: Vector2d
    private val c2: Vector2d
    val ends: Pair<Vec3, Vec3>
    init {
        val unrotated = Vec3.Z_AXIS.scale(range)
        val p1 = unrotated.yRot(-yawToNextRadians + FLOAT_PI / 2)
        val p2 = unrotated.yRot(-yawToNextRadians - FLOAT_PI / 2)
        c1 = Vector2d(p1.x, p1.z)
        c2 = Vector2d(p2.x, p2.z)
        ends = Pair(pos.add(c1.x, 0.0, c1.y), pos.add(c2.x, 0.0, c2.y))
    }
    private fun getCheckMatrix(before: Vec3): Matrix2d {
        val before = Vector2d(before.x - pos.x, before.z - pos.z)
        return Matrix2d(c1 - before, c2 - before).invert()
    }

    private val yawToNextRadians get() = ((yawToNext * PI) / 180).toFloat()
    internal var internal_index by Delegates.notNull<UInt>()
    val index: UInt 
            get() = internal_index
    val isFirst: Boolean 
            get() = index == 0u
    internal var next: UUID by Delegates.notNull<UUID>()
    // this literally should only error if saving occurs *during* node creation (??)
    private val nextOptional get() = try {
            Optional.of(next)
        } catch (e: IllegalStateException) {
            Optional.empty()
        }
        
    companion object {
        const val DETECTION_RANGE_SQ = 25.0
        val CODEC: Codec<NodeData> = RecordCodecBuilder.create{it.group(
            Vec3.CODEC.fieldOf("position").forGetter(NodeData::pos),
            Codec.FLOAT.fieldOf("yaw").forGetter(NodeData::yawToNext),
            Codec.DOUBLE.fieldOf("range").forGetter(NodeData::range),
            UInt.CODEC.fieldOf("index").forGetter(NodeData::internal_index),
            UUIDUtil.CODEC.optionalFieldOf("next").forGetter(NodeData::nextOptional),
        ).apply(it, ::NodeData)}
    }
    private constructor(
        pos: Vec3,
        yawToNext: Float,
        range: Double,
        index: UInt,
        next: Optional<UUID>
    ) : this(pos, yawToNext, range) {
        this.internal_index = index
        if (next.isPresent) this.next = next.get()
    }
    
    // Algorithm derived by Smitology
    fun passedBy(before: Vec3, after: Vec3): Boolean {
        val delta = after.subtract(before)
        
        return (getCheckMatrix(before) * Vector2d(delta.x, delta.z))
            .let{ it.x >= 0 && it.y >= 0 && it.x + it.y >= 1 }
    }
    
    override fun toString(): String = 
        "Node $internal_index:\n" +
        "             pos: ${pos.format}\n" +
        "     yaw to next: $yawToNext\n" +
        "       next node: $next"
}


data class MapData(
    var firstNodeId: UUID
) {
    val startPositions: MutableCollection<Vec3> = mutableSetOf()
    private val startPositionsList get() = startPositions.toList()
    companion object {
        val CODEC: Codec<MapData> = RecordCodecBuilder.create{it.group(
            UUIDUtil.CODEC.fieldOf("firstNodeId").forGetter(MapData::firstNodeId),
            Vec3.CODEC.listOf().fieldOf("startPositions").forGetter(MapData::startPositionsList)
        ).apply(it, ::MapData)}
    }
    private constructor(firstNodeId: UUID, startPositions: List<Vec3>) : this(firstNodeId) {
        this.startPositions.addAll(startPositions)
    }
}


interface MapDataExposer {
    // used to roughly mark the layout of the map
    fun getNode(node: UUID): NodeData?
    fun getNodeAfter(node: UUID): NodeData? = getNode(node)?.next?.let(::getNode)
    
    fun appendNode(id: String, new: NodeData): UUID
    fun insertNodeAfter(mapId: String, new: NodeData, index: UInt? = null): UUID
    fun insertNodeAfter(node: UUID, new: NodeData): UUID?
    
    fun getMapKeys(): Iterable<String>
    fun getMapData(id: String): MapData?
    
    fun initMap(id: String, first_pos: Vec3 = Vec3.ZERO): NodeData
    fun clearMap(id: String)
    
    
    fun NodeData.next(): NodeData = getNode(this.next) ?: throw StringIndexOutOfBoundsException()
    
    fun printNodes(map: String, print: (NodeData) -> Unit = { IceboatPlugin.LOGGER.info("node: {}", it) }) {
        var node: NodeData = getFirstMapNodeInternal(map) ?: run { print(null); return }
        do {
            print(node)
            node = node.next()
        } while (!node.isFirst)
    }
    
    fun getAllMapNodes(id: String): Iterable<Pair<UUID, NodeData>> {
        val res: MutableCollection<Pair<UUID, NodeData>> = mutableListOf()
        
        var nodeId = getMapData(id)?.firstNodeId ?: return res
        do {
            var node = getNode(nodeId) ?: throw StringIndexOutOfBoundsException("1")
            res.add(Pair(nodeId, node))
            nodeId = node.next
            val index = getNode(nodeId)?.index ?: throw StringIndexOutOfBoundsException("2")
        } while (index > 0u)
        
        return res
    }
}

private fun MapDataExposer.getFirstMapNodeInternal(id: String): NodeData? {
    return getMapData(id)?.firstNodeId?.let{ getNode(it) }
}

internal interface MapDataHolder : MapDataExposer {
    val node_map: MutableMap<UUID, NodeData>
    val mapDataMap: MutableMap<String, MapData>
    
    override fun getMapData(id: String): MapData? = mapDataMap[id]
    override fun getNode(node: UUID): NodeData? = node_map[node]
    override fun getMapKeys(): Iterable<String> = mapDataMap.keys
    
    override fun clearMap(id: String) {
        getFirstMapNodeInternal(id)?.unlinkChain()
        node_map.remove(getMapData(id)?.firstNodeId)
        mapDataMap.remove(id)
    }
    override fun initMap(id: String, first_pos: Vec3): NodeData {
        return getNode(insertNodeAfter(id, NodeData(first_pos)))!!
    }
    
    override fun appendNode(id: String, new: NodeData): UUID {
        var node = getFirstMapNodeInternal(id) ?: return insertNodeAfter(id, new)
        while (!node.next().isFirst) node = node.next()
        
        return node.insertNodeAfter(new)
    }
    
    private fun NodeData.unlinkChain() {
        val nextNode = this.next()
        if (!nextNode.isFirst) {
            nextNode.unlinkChain()
        }
        node_map.remove(this.next)
    }
    
    private fun NodeData.propogateIndex() {
        val next = this.next() 
        if (!next.isFirst) {
            next.internal_index = this.index + 1u
            next.propogateIndex()
        }
    }
    
    // NB: doesn't check if newNode has a next val already
    private fun NodeData.insertNodeAfter(newNode: NodeData): UUID {
        val index = this.index
        val new = addNode(newNode, index + 1u)
        newNode.next = this.next
        this.next = new
        this.propogateIndex()
        
        return new
    }
    
    // NB: doesn't check for duplicates
    private fun addNode(new: NodeData, index: UInt? = null): UUID = UUID.randomUUID().also{
        index?.let { new.internal_index = index }
        node_map[it] = new
    }
    
    
    // if index is null, insert it as new head
    override fun insertNodeAfter(mapId: String, new: NodeData, index: UInt?): UUID {
        val mapHead = getFirstMapNodeInternal(mapId)
        
        // insert node as new head
        if (index == null || mapHead == null) {
            val newNodeId = addNode(new, 0u)
            mapDataMap[mapId]?.let{it.firstNodeId = newNodeId} ?: run { mapDataMap[mapId] = MapData(newNodeId) }
            mapHead?.let{
                var node = it
                // seek to end of node chain
                while (!it.next().isFirst) node = node.next()
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
}