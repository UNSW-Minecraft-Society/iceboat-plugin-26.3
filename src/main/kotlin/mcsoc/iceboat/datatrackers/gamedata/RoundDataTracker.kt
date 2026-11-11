package mcsoc.iceboat.datatrackers.gamedata

import com.mojang.serialization.Codec
import com.mojang.serialization.MapCodec
import com.mojang.serialization.codecs.RecordCodecBuilder
import mcsoc.iceboat.datatrackers.LevelDataType
import mcsoc.iceboat.datatrackers.LevelTiedData
import mcsoc.iceboat.utils.CODEC
import net.minecraft.core.UUIDUtil
import java.util.UUID
import kotlin.time.Duration


class RoundDataTracker() : LevelTiedData(), GameDataHolder {
    override fun getType() = LevelDataType.RaceData
    companion object {
        val CODEC: MapCodec<RoundDataTracker> = RecordCodecBuilder.mapCodec{it.group(
            Codec.STRING.fieldOf("activeMapKey").forGetter(RoundDataTracker::activeMapKey),
            GamePhase.CODEC.fieldOf("gamePhase").forGetter(RoundDataTracker::phase),
            Duration.CODEC.fieldOf("startTime").forGetter(RoundDataTracker::startTime),
            UInt.CODEC.fieldOf("numLaps").forGetter(RoundDataTracker::numLaps),
            Codec.unboundedMap(UUIDUtil.STRING_CODEC, LapProgress.CODEC).listOf().fieldOf("nodeProgress").forGetter(RoundDataTracker::nodeProgress)
        ).apply(it, ::RoundDataTracker)}
    }
    private constructor(activeMapKey: String, phase: GamePhase, startTime: Duration, numlaps: UInt, nodeProgress: MutableList<MutableMap<UUID, LapProgress>>) : this() {
        this.activeMapKey = activeMapKey
        this.phase = phase
        this.startTime = startTime
        this.numLaps = numlaps
        nodeProgress.map{it.toMutableMap()}.forEach(this.nodeProgress::add)
    }
    
    override var activeMapKey: String = ""
        get() = if (phase.isRunning) field else "" 
        
    override var phase: GamePhase = GamePhase.Inactive
    override var startTime: Duration = Duration.ZERO
    override var numLaps: UInt = 3u
        get() = if (phase.isPractice == true) 100u else field
    override val nodeProgress: MutableList<MutableMap<UUID, LapProgress>> = mutableListOf()
    override val playerToBoatMap: MutableMap<UUID, UUID> = mutableMapOf()
    override val vehicleToDisplayMap: MutableMap<UUID, Pair<Int, UUID>> = mutableMapOf()
    override val playerPositions: MutableList<UUID> = mutableListOf()
    
    override var isSillyMode: Boolean = false
}