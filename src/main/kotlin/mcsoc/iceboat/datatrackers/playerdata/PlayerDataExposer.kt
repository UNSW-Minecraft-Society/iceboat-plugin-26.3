package mcsoc.iceboat.datatrackers.playerdata

import com.mojang.serialization.Codec
import com.mojang.serialization.codecs.RecordCodecBuilder
import net.minecraft.core.UUIDUtil
import java.util.UUID


data class PlayerData(
    val currNode: UUID? = null
) {
    companion object {
        val CODEC: Codec<PlayerData> = RecordCodecBuilder.create{it.group(
            UUIDUtil.CODEC.fieldOf("currNode").forGetter(PlayerData::currNode)
        ).apply(it, ::PlayerData)}
    }
}


interface PlayerDataExposer {
    val player_data: Map<UUID, PlayerData>
    fun getPlayerData(player: UUID): PlayerData
}

internal interface PlayerDataHolder : PlayerDataExposer {
    override val player_data: MutableMap<UUID, PlayerData>
    override fun getPlayerData(player: UUID): PlayerData = player_data.getOrPut(player){PlayerData()}
}