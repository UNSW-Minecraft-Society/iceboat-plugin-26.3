package mcsoc.iceboat.datatrackers.gamedata

import com.mojang.serialization.Codec
import com.mojang.serialization.codecs.RecordCodecBuilder
import mcsoc.iceboat.utils.CODEC
import net.minecraft.core.UUIDUtil
import net.minecraft.util.StringRepresentable
import java.util.UUID
import kotlin.time.Duration


data class LapProgress(
    var fastestPlayer: Pair<UUID, Duration>? = null,
    var numPassed: UInt = 0u
) : Comparable<Duration> {
    companion object {
        private val PAIR_CODEC: Codec<Pair<UUID, Duration>> = RecordCodecBuilder.create{it.group(
            UUIDUtil.CODEC.fieldOf("uuid").forGetter(Pair<UUID, Duration>::first),
            Duration.CODEC.fieldOf("time").forGetter(Pair<UUID, Duration>::second)
        ).apply(it, ::Pair)}
        
        val CODEC: Codec<LapProgress> = RecordCodecBuilder.create{it.group(
            PAIR_CODEC.fieldOf("fastestPlayer").forGetter(LapProgress::fastestPlayer),
            UInt.CODEC.fieldOf("numPassed").forGetter(LapProgress::numPassed),
        ).apply(it, ::LapProgress)}
    }
    
    fun setFastestPlayer(fastestPlayer: UUID, fastestTime: Duration) {
        this.fastestPlayer = Pair(fastestPlayer, fastestTime)
    }
    
    // return greater if no time has been set yet
    override fun compareTo(other: Duration): Int {
        return this.fastestPlayer?.second?.compareTo(other) ?: 1
    }
}


enum class GamePhase(val isRunning: Boolean, val isActive: Boolean) : StringRepresentable {
    Inactive(false, false),
    Countdown(true, false),
    Active(true, true);

    override fun getSerializedName(): String = this.name
    
    companion object {
        val CODEC = StringRepresentable.fromEnum(GamePhase::values)
    }
}


interface GameDataExposer {
    val phase: GamePhase
    val activeMapKey: String
    fun setActiveMap(key: String)
    fun startRace()
    fun setInactive()
    
    val numLaps: UInt get() = 3u
    
    val nodeProgress: List<Map<UUID, LapProgress>>
    fun getNodeProgress(node: UUID, lap: Int): LapProgress? {
        return if (nodeProgress.size <= lap) null
        else nodeProgress[lap][node]
    }
    fun getNodeProgress(node: UUID, lap: UInt) = getNodeProgress(node, lap.toInt())
    
    // return if time was new fastest
    fun trackPlayerPassed(node: UUID, lap: UInt, player: UUID, playerTime: Duration): Boolean
    
    val playerToBoatMap: MutableMap<UUID, UUID>
    val boatToDisplayMap: MutableMap<UUID, UUID>
}

internal interface GameDataHolder : GameDataExposer {
    override var phase: GamePhase
    override var activeMapKey: String
    
    override val nodeProgress: MutableList<MutableMap<UUID, LapProgress>>
    override fun getNodeProgress(node: UUID, lap: Int): LapProgress = super.getNodeProgress(node, lap) ?: run {
        while (nodeProgress.size <= lap) nodeProgress.add(mutableMapOf())
        LapProgress().also{ nodeProgress[lap][node] = it }
    }    
    
    private fun setActiveMapInternal(key: String?) {
        activeMapKey = key?.also{
            phase = GamePhase.Countdown
        } ?: run {
            phase = GamePhase.Inactive
            ""
        }
        // clear old race data
        nodeProgress.clear()
        playerToBoatMap.clear()
    }
    override fun setActiveMap(key: String) = setActiveMapInternal(key)
    override fun startRace() {
        phase = GamePhase.Active
    }
    override fun setInactive() = setActiveMapInternal(null)
    
    override fun trackPlayerPassed(node: UUID, lap: UInt, player: UUID, playerTime: Duration): Boolean {
        val lap = lap.toInt()
        val progress = getNodeProgress(node, lap)
        progress.numPassed++

        return if (progress > playerTime) {
            progress.setFastestPlayer(player, playerTime)
            true
        } else false
    }
}