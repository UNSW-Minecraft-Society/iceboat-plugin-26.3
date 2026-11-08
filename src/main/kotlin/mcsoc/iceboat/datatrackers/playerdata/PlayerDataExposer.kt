package mcsoc.iceboat.datatrackers.playerdata

import com.mojang.serialization.Codec
import com.mojang.serialization.codecs.RecordCodecBuilder
import mcsoc.iceboat.IceboatPlugin
import mcsoc.iceboat.utils.CODEC
import net.minecraft.core.UUIDUtil
import net.minecraft.world.entity.player.Player
import java.util.Optional
import java.util.UUID
import kotlin.time.Duration


class PlayerData(
    val playerName: String,
) {
    var currNode: UUID? = null
    private val currNodeOptional get() = Optional.ofNullable(currNode)
    var totalNodesPassed: UInt = 0u
    var nodePosition = 0u
    var lapsCompleted: UInt = 0u
    var nodesThisLapPassed: UInt = 0u
    var currentSplit: UInt = 0u
    // each race starts at t = zero
    var splitStartTime: Duration = Duration.ZERO
    fun toSplitTime(raceTime: Duration): Duration {
        return raceTime - splitStartTime
    }
    
    var mostRecentTime: Duration = Duration.ZERO

    // [lap][split]
    val splitTimes: MutableList<MutableList<Duration>> = mutableListOf()
    companion object {
        val CODEC: Codec<PlayerData> = RecordCodecBuilder.create{it.group(
            Codec.STRING.fieldOf("playerName").forGetter(PlayerData::playerName),
            UUIDUtil.CODEC.optionalFieldOf("currNode").forGetter(PlayerData::currNodeOptional),
            UInt.CODEC.optionalFieldOf("totalNodesPassed", 0u).forGetter(PlayerData::totalNodesPassed),
            UInt.CODEC.optionalFieldOf("nodesThisLapPassed", 0u).forGetter(PlayerData::nodesThisLapPassed),
            UInt.CODEC.optionalFieldOf("currentSplit", 0u).forGetter(PlayerData::currentSplit),
            UInt.CODEC.optionalFieldOf("nodePosition", 0u).forGetter(PlayerData::nodePosition),
            UInt.CODEC.optionalFieldOf("lapsCompleted", 0u).forGetter(PlayerData::lapsCompleted),
            Duration.CODEC.listOf().listOf().fieldOf("splitTimes").forGetter(PlayerData::splitTimes)
        ).apply(it, ::PlayerData)}
    }
    
    private constructor(
        playerName: String,
        currNode: Optional<UUID>,
        nodesPassed: UInt, nodesThisLapPassed: UInt, currentSplit: UInt, 
        nodePosition: UInt, lapsCompleted: UInt,
        splitTimes: List<List<Duration>>
    ) : this(playerName) {
        this.currNode = currNode.orElse(null)
        this.totalNodesPassed = nodesPassed
        this.nodePosition = nodePosition
        this.nodesThisLapPassed = nodesThisLapPassed
        this.currentSplit = currentSplit
        this.lapsCompleted = lapsCompleted
        this.splitTimes.addAll(splitTimes.map(List<Duration>::toMutableList))
    }
        
    fun resetData() {
        currNode = null
        splitTimes.clear()
        splitStartTime = Duration.ZERO
        totalNodesPassed = 0u
        nodesThisLapPassed = 0u
        currentSplit = 0u
        nodePosition = 0u
        lapsCompleted = 0u
    }
    
    fun initGameData(numLaps: UInt, numSplits: UInt) {
        while (splitTimes.size <= numLaps.toInt()) splitTimes.add(MutableList(numSplits.toInt() + 1){Duration.ZERO})
    }
    
    override fun toString(): String = 
        "Player Info:\n" +
        "  currNode: $currNode\n"
}


interface PlayerDataExposer {
    val player_data: Map<UUID, PlayerData>
    fun getOrInitPlayerData(player: UUID, playerName: String = "Err"): PlayerData
    fun getOrInitPlayerData(player: Player) = getOrInitPlayerData(player.uuid, player.displayName.string)
    
    val activePlayers: MutableCollection<UUID>
    val activePlayerData: Iterable<PlayerData>
        get() = player_data.filter{it.key in activePlayers}.values
    
    fun trackPlayerPassingNode(player: UUID, isFirstNode: Boolean, endsSplit: Boolean)
    fun trackPlayerTimeAtNode(player: UUID, time: Duration, isFirstNode: Boolean, endsSplit: Boolean): Boolean?        
}

internal interface PlayerDataHolder : PlayerDataExposer {
    override val player_data: MutableMap<UUID, PlayerData>
    override fun getOrInitPlayerData(player: UUID, playerName: String): PlayerData = player_data.getOrPut(player){PlayerData(playerName)}
    
    override fun trackPlayerPassingNode(player: UUID, isFirstNode: Boolean, endsSplit: Boolean) {
        player_data[player]?.let {
            it.totalNodesPassed++
            it.nodesThisLapPassed++
            
            // if first map node
            if (isFirstNode) {
                it.lapsCompleted++
                it.nodesThisLapPassed = 0u
            } else if (endsSplit) it.currentSplit++
        } ?: run { IceboatPlugin.LOGGER.error("trackPlayerTimeAtNode: ", StringIndexOutOfBoundsException()) }
    }
    
    override fun trackPlayerTimeAtNode(player: UUID, time: Duration, isFirstNode: Boolean, endsSplit: Boolean): Boolean? {
        return getOrInitPlayerData(player).let{
            if (isFirstNode || endsSplit) {
                val lap = it.lapsCompleted.toInt()
                val split = it.currentSplit.toInt()
                val prevLapTime = if (lap == 0) Duration.ZERO 
                else it.splitTimes[lap - 1][split]
                
                // This shouldn't fail IF initGameData was called correctly!
                val splitTime = time - it.splitStartTime
                it.splitTimes[lap][split] = splitTime
                it.mostRecentTime = splitTime
                
                it.splitStartTime = time
                prevLapTime >= splitTime
            } else { null }.also {
                trackPlayerPassingNode(player, isFirstNode, endsSplit)
            }
        }
    }
}