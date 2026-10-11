package mcsoc.iceboat.datatrackers.playerdata

import com.mojang.serialization.Codec
import com.mojang.serialization.codecs.RecordCodecBuilder
import mcsoc.iceboat.IceboatPlugin
import mcsoc.iceboat.datatrackers.timer.TimeHolder
import mcsoc.iceboat.utils.CODEC
import mcsoc.iceboat.utils.ticks
import net.minecraft.core.UUIDUtil
import net.minecraft.world.entity.player.Player
import java.util.Optional
import java.util.UUID
import kotlin.math.min
import kotlin.time.Duration


class PlayerData(
    val playerName: String,
) {
    var currNode: UUID? = null
    private val currNodeOptional get() = Optional.ofNullable(currNode)
    var nodesPassed: UInt = 0u
    var nodePosition = 0u
    var lapsCompleted: UInt = 0u
    val timer = object : TimeHolder {
        override var time: Duration = Duration.ZERO
        override fun tick() { time += 1.ticks }
        override fun reset() { time = Duration.ZERO }
    }
    
    internal val internal_bestNodeTimes: MutableMap<UUID, Duration> = mutableMapOf()
    val bestNodeTimes: Map<UUID, Duration> get() = internal_bestNodeTimes
    companion object {
        val CODEC: Codec<PlayerData> = RecordCodecBuilder.create{it.group(
            Codec.STRING.fieldOf("playerName").forGetter(PlayerData::playerName),
            UUIDUtil.CODEC.optionalFieldOf("currNode").forGetter(PlayerData::currNodeOptional),
            UInt.CODEC.fieldOf("nodesPassed").forGetter(PlayerData::nodesPassed),
            UInt.CODEC.fieldOf("nodePosition").forGetter(PlayerData::nodePosition),
            UInt.CODEC.fieldOf("lapsCompleted").forGetter(PlayerData::lapsCompleted),
            Codec.unboundedMap(
                UUIDUtil.STRING_CODEC,
                Duration.CODEC
            )
            .fieldOf("best_times")
            .forGetter(PlayerData::bestNodeTimes)
        ).apply(it, ::PlayerData)}
    }
    
    private constructor(
        playerName: String,
        currNode: Optional<UUID>,
        nodesPassed: UInt, nodePosition: UInt, lapsCompleted: UInt,
        bestNodeTimes: Map<UUID, Duration>
    ) : this(playerName) {
        this.currNode = currNode.orElse(null)
        this.nodesPassed = nodesPassed
        this.nodePosition = nodePosition
        this.lapsCompleted = lapsCompleted
        internal_bestNodeTimes.putAll(bestNodeTimes)
    }
        
    fun resetData() {
        currNode = null
        nodesPassed = 0u
        internal_bestNodeTimes.clear()
        nodesPassed = 0u
        nodePosition = 0u
        lapsCompleted = 0u
        timer.reset()
    }
    
    override fun toString(): String = 
        "Player Info:\n" +
        "  currNode: $currNode\n" +
        "  time: ${timer.time}\n"
}


interface PlayerDataExposer {
    val player_data: Map<UUID, PlayerData>
    fun getOrInitPlayerData(player: UUID, playerName: String = "Err"): PlayerData
    fun getOrInitPlayerData(player: Player) = getOrInitPlayerData(player.uuid, player.displayName.string)
    
    var activePlayers: MutableCollection<UUID>
    val activePlayerData: Iterable<PlayerData>
        get() = player_data.filter{it.key in activePlayers}.values
    
    fun trackPlayerTimeAtNode(player: UUID, node: UUID, index: UInt, time: Duration): Boolean
}

internal interface PlayerDataHolder : PlayerDataExposer {
    override val player_data: MutableMap<UUID, PlayerData>
    override fun getOrInitPlayerData(player: UUID, playerName: String): PlayerData = player_data.getOrPut(player){PlayerData(playerName)}
    
    override fun trackPlayerTimeAtNode(player: UUID, node: UUID, index: UInt, time: Duration): Boolean {
        return player_data[player]?.let {
            // if first map node
            if (index == 0u) {
                it.lapsCompleted++
            } 
            it.nodesPassed++
            val currNodeTime = it.internal_bestNodeTimes[node] ?: Duration.INFINITE
            it.internal_bestNodeTimes[node] = minOf(currNodeTime, time)
            
            currNodeTime >= time
        } ?: run {
            IceboatPlugin.LOGGER.error("trackPlayerTimeAtNode: ", StringIndexOutOfBoundsException())
            false
        }
    }
}