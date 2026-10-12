package mcsoc.iceboat.datatrackers.mapdata

import com.mojang.serialization.Codec
import com.mojang.serialization.MapCodec
import com.mojang.serialization.codecs.RecordCodecBuilder
import mcsoc.iceboat.datatrackers.LevelDataType
import mcsoc.iceboat.datatrackers.LevelTiedData
import mcsoc.iceboat.datatrackers.playerData
import net.minecraft.core.UUIDUtil
import net.minecraft.server.level.ServerLevel
import net.minecraft.world.phys.Vec3
import java.util.UUID
import kotlin.time.Duration


class MapDataTracker() : LevelTiedData(), MapDataHolder {
    override fun getType() = LevelDataType.MapData
    companion object {
        val CODEC: MapCodec<MapDataTracker> = RecordCodecBuilder.mapCodec{it.group(
            Codec.unboundedMap(
                UUIDUtil.STRING_CODEC, 
                NodeData.CODEC
            )   
                .optionalFieldOf("nodes", mapOf())
                .forGetter(MapDataTracker::node_map),
            Codec.unboundedMap(
                Codec.STRING, 
                MapData.CODEC
            )
                .optionalFieldOf("maps", mapOf())
                .forGetter(MapDataTracker::mapDataMap)
        ).apply(it, ::MapDataTracker)}
    }
    private constructor(nodes: Map<UUID, NodeData>, maps: Map<String, MapData>) : this() {
        this.node_map.putAll(nodes)
        this.mapDataMap.putAll(maps)
    }
    
    override val node_map: MutableMap<UUID, NodeData> = mutableMapOf()
    override val mapDataMap: MutableMap<String, MapData> = mutableMapOf()
    
    override fun insertNodeAfter(mapId: String, new: NodeData, index: UInt?): UUID {
        setDirty()
        return super.insertNodeAfter(mapId, new, index)
    }
    override fun insertNodeAfter(node: UUID, new: NodeData): UUID? {
        setDirty()
        return super.insertNodeAfter(node, new)
    }
    override fun appendNode(id: String, new: NodeData): UUID {
        setDirty()
        return super.appendNode(id, new)
    }
    override fun initMap(id: String, first_pos: Vec3): NodeData {
        setDirty()
        return super.initMap(id, first_pos)
    }
}