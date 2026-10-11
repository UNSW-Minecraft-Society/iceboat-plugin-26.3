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
            .forGetter(PlayerDataTracker::player_data),
            UUIDUtil.CODEC.listOf().fieldOf("activePlayers").forGetter(PlayerDataTracker::activePlayersList)
        ).apply(it, ::PlayerDataTracker)}
    }
    private constructor(player_data: Map<UUID, PlayerData>, activePlayers: List<UUID>) : this() {
        this.player_data.putAll(player_data)
        this.activePlayers.addAll(activePlayers)
    }
    
    override val player_data: MutableMap<UUID, PlayerData> = mutableMapOf()
    override val activePlayers: MutableCollection<UUID> = mutableSetOf()
    private val activePlayersList get() = activePlayers.toList()
}