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
            .forGetter(MapDataTracker::node_map)
        ).apply(it, ::MapDataTracker)}
    }
    private constructor(nodes: Map<UUID, NodeData>) : this() {
        this.node_map.putAll(nodes)
    }
    
    override val node_map: MutableMap<UUID, NodeData> = mutableMapOf()
    override val map_heads: MutableMap<String, UUID> = mutableMapOf()

    override fun getPlayerBestTime(level: ServerLevel, player: UUID?, node: UUID): Duration? {
        return level.playerData.player_data[player]?.bestNodeTimes[node]
    }
    
    override fun trackPlayerTime(node: UUID, level: ServerLevel, player: UUID, lap: UInt) {
        setDirty()
        return super.trackPlayerTime(node, level, player, lap)
    }
    
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