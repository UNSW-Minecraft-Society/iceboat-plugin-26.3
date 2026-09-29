package mcsoc.iceboat.datatrackers.playerdata

import com.mojang.serialization.Codec
import com.mojang.serialization.codecs.RecordCodecBuilder
import mcsoc.iceboat.IceboatPlugin
import mcsoc.iceboat.datatrackers.timer.TimeHolder
import mcsoc.iceboat.utils.CODEC
import mcsoc.iceboat.utils.ticks
import net.minecraft.core.UUIDUtil
import net.minecraft.network.chat.Component
import net.minecraft.network.chat.ComponentSerialization
import net.minecraft.world.entity.player.Player
import java.util.UUID
import kotlin.time.Duration


class PlayerData(
    val playerName: String,
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
            Codec.STRING.fieldOf("playerName").forGetter(PlayerData::playerName),
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
    var activePlayers: MutableCollection<UUID>
    val activePlayerData: Iterable<PlayerData>
        get() = player_data.filter{it.key in activePlayers}.values
    
    fun trackPlayerTimeAtNode(player: UUID, node: UUID, time: Duration)
}

internal interface PlayerDataHolder : PlayerDataExposer {
    override val player_data: MutableMap<UUID, PlayerData>
    fun getPlayerData(player: UUID, playerName: String = "Err") = player_data.getOrPut(player){PlayerData(playerName)}
    fun getPlayerData(player: Player) = player_data.getOrPut(player.uuid){PlayerData(player.displayName.string)}

    override fun trackPlayerTimeAtNode(player: UUID, node: UUID, time: Duration) {
        player_data[player]?.let{it.internal_bestNodeTimes[node] = time} ?: run {
            IceboatPlugin.LOGGER.error("trackPlayerTimeAtNode: ", StringIndexOutOfBoundsException())
        }
    }
}