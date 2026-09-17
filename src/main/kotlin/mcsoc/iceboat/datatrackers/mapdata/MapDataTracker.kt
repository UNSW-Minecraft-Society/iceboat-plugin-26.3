package mcsoc.iceboat.datatrackers.mapdata

import com.mojang.serialization.Codec
import com.mojang.serialization.MapCodec
import com.mojang.serialization.codecs.RecordCodecBuilder
import mcsoc.iceboat.datatrackers.LevelDataType
import mcsoc.iceboat.datatrackers.LevelTiedData
import mcsoc.iceboat.datatrackers.playerData
import net.minecraft.core.UUIDUtil
import net.minecraft.server.level.ServerLevel
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

    override fun getPlayerTime(level: ServerLevel, player: UUID?, node: UUID): Duration? {
        return level.playerData.player_data[player]?.bestNodeTimes[node]
    }
}