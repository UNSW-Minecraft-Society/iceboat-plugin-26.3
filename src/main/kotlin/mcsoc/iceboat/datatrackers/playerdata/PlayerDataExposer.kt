package mcsoc.iceboat.datatrackers.playerdata

import com.mojang.serialization.Codec
import com.mojang.serialization.codecs.RecordCodecBuilder
import mcsoc.iceboat.utils.CODEC
import net.minecraft.core.UUIDUtil
import java.util.UUID
import kotlin.time.Duration


class PlayerData(
    val currNode: UUID? = null,
    bestNodeTimes: Map<UUID, Duration> = mapOf()
) {
    internal val internal_bestNodeTimes: MutableMap<UUID, Duration> = bestNodeTimes.toMutableMap()
    val bestNodeTimes: Map<UUID, Duration> get() = internal_bestNodeTimes
    companion object {
        val CODEC: Codec<PlayerData> = RecordCodecBuilder.create{it.group(
            UUIDUtil.CODEC.fieldOf("currNode").forGetter(PlayerData::currNode),
            Codec.unboundedMap(
                UUIDUtil.STRING_CODEC,
                Duration.CODEC
            )
            .fieldOf("best_times")
            .forGetter(PlayerData::bestNodeTimes)
        ).apply(it, ::PlayerData)}
    }
}


interface PlayerDataExposer {
    val player_data: Map<UUID, PlayerData>
    fun trackTime(player: UUID, node: UUID, time: Duration)
}

internal interface PlayerDataHolder : PlayerDataExposer {
    override val player_data: MutableMap<UUID, PlayerData>
    override fun trackTime(player: UUID, node: UUID, time: Duration) {
        player_data.getOrPut(player){PlayerData()}
            .let{it.internal_bestNodeTimes[node] = time}
    }
}