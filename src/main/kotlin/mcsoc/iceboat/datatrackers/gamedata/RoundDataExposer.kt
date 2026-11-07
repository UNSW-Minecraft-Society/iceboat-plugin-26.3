package mcsoc.iceboat.datatrackers.gamedata

import com.mojang.serialization.Codec
import com.mojang.serialization.codecs.RecordCodecBuilder
import mcsoc.iceboat.utils.CODEC
import net.minecraft.core.UUIDUtil
import net.minecraft.server.level.ServerLevel
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


enum class GamePhase(val isRunning: Boolean, val isActive: Boolean, val isPractice: Boolean? = null) : StringRepresentable {
    Inactive(false, false),
    Countdown(true, false),
    Practice(true, true, true),
    Active(true, true, false);

    override fun getSerializedName(): String = this.name
    
    companion object {
        val CODEC = StringRepresentable.fromEnum(GamePhase::values)
    }
}


interface RoundDataExposer {
    val phase: GamePhase
    val activeMapKey: String
    
    val numLaps: UInt
    val startTime: Duration
    fun toRaceTime(currTime: Duration): Duration {
        return currTime - startTime
    }
    
    fun startRoundWith(key: String, numLaps: UInt)
    fun beginRace(currTime: Duration)
    fun beginPracticeRace(currTime: Duration)
    fun setInactive()
    
    val nodeProgress: List<Map<UUID, LapProgress>>
    fun getNodeProgress(node: UUID, lap: Int): LapProgress? {
        return if (nodeProgress.size <= lap) null
        else nodeProgress[lap][node]
    }
    fun getNodeProgress(node: UUID, lap: UInt) = getNodeProgress(node, lap.toInt())
    
    // return if time was new fastest
    fun trackPlayerPassed(node: UUID, lap: UInt, player: UUID, playerTime: Duration): Boolean
    
    val playerToBoatMap: MutableMap<UUID, UUID>
    val vehicleToDisplayMap: MutableMap<UUID, Pair<Int, UUID>>
    
    val playerPositions: MutableList<UUID>
    fun sortPositions(level: ServerLevel, sortBy: ((ServerLevel) -> ((UUID, UUID) -> Int))) {
        playerPositions.sortWith(sortBy(level))
    }
    
    var isSillyMode: Boolean
}

internal interface GameDataHolder : RoundDataExposer {
    override var phase: GamePhase
    
    override var activeMapKey: String
    override var numLaps: UInt
    override var startTime: Duration
    
    override val nodeProgress: MutableList<MutableMap<UUID, LapProgress>>
    override fun getNodeProgress(node: UUID, lap: Int): LapProgress = super.getNodeProgress(node, lap) ?: run {
        while (nodeProgress.size <= lap) nodeProgress.add(mutableMapOf())
        LapProgress().also{ nodeProgress[lap][node] = it }
    }    

    override fun startRoundWith(key: String, numLaps: UInt) {
        if (phase != GamePhase.Inactive) setInactive()
        
        this.activeMapKey = key
        this.numLaps = numLaps
        
        // Await for beginRace to be called
        this.phase = GamePhase.Countdown
    }
    override fun beginPracticeRace(currTime: Duration) {
        this.startTime = currTime
        phase = GamePhase.Practice
    }
    override fun beginRace(currTime: Duration) {
        this.startTime = currTime
        phase = GamePhase.Active
    }
    override fun setInactive() {
        phase = GamePhase.Inactive
        
        // clear old race data
        nodeProgress.clear()
        playerToBoatMap.clear()
    }
    
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