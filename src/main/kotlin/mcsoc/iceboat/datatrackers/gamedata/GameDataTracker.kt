package mcsoc.iceboat.datatrackers.gamedata

import com.mojang.serialization.Codec
import com.mojang.serialization.MapCodec
import com.mojang.serialization.codecs.RecordCodecBuilder
import mcsoc.iceboat.datatrackers.LevelDataType
import mcsoc.iceboat.datatrackers.LevelTiedData
import net.minecraft.core.UUIDUtil
import java.util.UUID


class GameDataTracker() : LevelTiedData(), GameDataHolder {
    override fun getType() = LevelDataType.RaceData
    companion object {
        val CODEC: MapCodec<GameDataTracker> = RecordCodecBuilder.mapCodec{it.group(
            Codec.STRING.fieldOf("activeMapKey").forGetter(GameDataTracker::activeMapKey),
            Codec.unboundedMap(UUIDUtil.STRING_CODEC, LapProgress.CODEC).listOf().fieldOf("nodeProgress").forGetter(GameDataTracker::nodeProgress),
            GamePhase.CODEC.fieldOf("gamePhase").forGetter(GameDataTracker::phase)
        ).apply(it, ::GameDataTracker)}
    }
    private constructor(activeMapKey: String, nodeProgress: MutableList<MutableMap<UUID, LapProgress>>, phase: GamePhase) : this() {
        this.activeMapKey = activeMapKey
        nodeProgress.map{it.toMutableMap()}.forEach(this.nodeProgress::add)
        this.phase = phase
    }
    
    override var activeMapKey: String = ""
        get() = if (phase.isRunning) field else "" 
    override var phase: GamePhase = GamePhase.Inactive
    override val nodeProgress: MutableList<MutableMap<UUID, LapProgress>> = mutableListOf()
    override val playerToBoatMap: MutableMap<UUID, UUID> = mutableMapOf()
    override val boatToDisplayMap: MutableMap<UUID, UUID> = mutableMapOf()
}