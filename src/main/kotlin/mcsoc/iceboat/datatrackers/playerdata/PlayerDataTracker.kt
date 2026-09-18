package mcsoc.iceboat.datatrackers.playerdata

import com.mojang.serialization.Codec
import com.mojang.serialization.MapCodec
import com.mojang.serialization.codecs.RecordCodecBuilder
import mcsoc.iceboat.datatrackers.LevelDataType
import mcsoc.iceboat.datatrackers.LevelTiedData
import net.minecraft.core.UUIDUtil
import java.util.UUID


class PlayerDataTracker() : LevelTiedData(), PlayerDataHolder {
    override fun getType(): LevelDataType<*> = LevelDataType.PlayerData
    companion object {
        val CODEC: MapCodec<PlayerDataTracker> = RecordCodecBuilder.mapCodec{it.group(
            Codec.unboundedMap(
                UUIDUtil.STRING_CODEC,
                PlayerData.CODEC
            )
            .optionalFieldOf("player_data", mapOf())
            .forGetter(PlayerDataTracker::player_data)
        ).apply(it, ::PlayerDataTracker)}
    }
    private constructor(player_data: Map<UUID, PlayerData>) : this() {
        this.player_data.putAll(player_data)
    }
    
    override val player_data: MutableMap<UUID, PlayerData> = mutableMapOf()
}