package mcsoc.iceboat.datatrackers.playerdata

import com.mojang.serialization.Codec
import com.mojang.serialization.codecs.RecordCodecBuilder
import mcsoc.iceboat.datatrackers.timer.TimeHolder
import mcsoc.iceboat.utils.CODEC
import mcsoc.iceboat.utils.ticks
import net.minecraft.core.UUIDUtil
import java.util.UUID
import kotlin.time.Duration
import kotlin.time.Instant
import kotlin.time.TimeMark
import kotlin.time.TimeSource


class PlayerData(
    var currNode: UUID? = null,
    bestNodeTimes: Map<UUID, Duration> = mapOf(),
) {
    val timer = object : TimeHolder {
        override var time: Duration = Duration.ZERO
        override fun tick() { time += 1.ticks }
        override fun reset() { time = Duration.ZERO }
    }
    
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
    
    fun resetData() {
        currNode = null
        internal_bestNodeTimes.clear()
        timer.reset()
    }
    
    override fun toString(): String = 
        "Player Info:\n" +
        "  currNode: $currNode\n" +
        "  time: ${timer.time}\n"
}


interface PlayerDataExposer {
    val player_data: Map<UUID, PlayerData>
    fun getPlayerData(player: UUID): PlayerData
    fun trackTime(player: UUID, node: UUID, time: Duration)
    
    fun getPlayers(): Iterable<UUID> = player_data.keys
    fun getPlayerData(): Iterable<PlayerData> = player_data.values
}

internal interface PlayerDataHolder : PlayerDataExposer {
    override val player_data: MutableMap<UUID, PlayerData>
    override fun getPlayerData(player: UUID) = player_data.getOrPut(player){PlayerData()}
    override fun trackTime(player: UUID, node: UUID, time: Duration) {
        player_data.getOrPut(player){PlayerData()}
            .let{it.internal_bestNodeTimes[node] = time}
    }
}