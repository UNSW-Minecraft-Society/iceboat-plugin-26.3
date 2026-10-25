@file:JvmName("LevelData")
package mcsoc.iceboat.datatrackers

import com.mojang.serialization.Codec
import com.mojang.serialization.MapCodec
import com.mojang.serialization.codecs.RecordCodecBuilder
import mcsoc.iceboat.IceboatPlugin
import mcsoc.iceboat.datatrackers.gamedata.GameDataExposer
import mcsoc.iceboat.datatrackers.gamedata.GameDataTracker
import mcsoc.iceboat.datatrackers.mapdata.MapDataExposer
import mcsoc.iceboat.datatrackers.mapdata.MapDataTracker
import mcsoc.iceboat.datatrackers.playerdata.PlayerDataExposer
import mcsoc.iceboat.datatrackers.playerdata.PlayerDataTracker
import mcsoc.iceboat.datatrackers.timer.GameTimer
import mcsoc.iceboat.datatrackers.timer.TickExposer
import mcsoc.iceboat.datatrackers.gameevents.GameEventsExposer
import mcsoc.iceboat.datatrackers.gameevents.GameEventsTracker
import mcsoc.iceboat.utils.LevelScoreboardHolder
import net.minecraft.resources.Identifier
import net.minecraft.server.level.ServerLevel
import net.minecraft.util.datafix.DataFixTypes
import net.minecraft.world.level.saveddata.SavedData
import net.minecraft.world.level.saveddata.SavedDataType


// each tracker should extend this class
abstract class LevelTiedData {
    companion object {
        internal val CODEC: Codec<LevelTiedData> = LevelDataType.CODEC.dispatch(
            { inst -> inst.getType() },
            { type -> type.codec }
        )
    }
    internal var isDirty: Boolean = false
    protected fun setDirty() {
        this.isDirty = true
    }
    
    abstract fun getType(): LevelDataType<*>
}

private class LevelTiedDataTracker() : SavedData() {
    private val tracked_data: MutableMap<LevelDataType<*>, LevelTiedData> = mutableMapOf()
    constructor(map: Map<LevelDataType<*>, LevelTiedData>) : this() {
        tracked_data.putAll(map)
    }
    
    companion object {
        private val CODEC: Codec<LevelTiedDataTracker> = RecordCodecBuilder.create{it.group(
            Codec.unboundedMap(LevelDataType.CODEC, LevelTiedData.CODEC)
                .fieldOf("level_data")
                .forGetter(LevelTiedDataTracker::tracked_data)
        ).apply(it, ::LevelTiedDataTracker)}
        
        private val TYPE = SavedDataType<LevelTiedDataTracker>(
            Identifier.fromNamespaceAndPath(IceboatPlugin.MOD_ID, "saved_level_data"),
            ::LevelTiedDataTracker, CODEC, DataFixTypes.LEVEL
        )
        fun getLevelData(level: ServerLevel): LevelTiedDataTracker = level.dataStorage.computeIfAbsent(TYPE)
    }
    override fun isDirty(): Boolean = tracked_data.values.any(LevelTiedData::isDirty)
    override fun setDirty(dirty: Boolean) {
        super.setDirty(dirty)
        for (tracker in tracked_data.values) {
            tracker.isDirty = dirty
        }
    }
    
    @Suppress("UNCHECKED_CAST")
    fun <T : LevelTiedData> getDataOfType(type: LevelDataType<T>): T {
        return this.tracked_data.getOrPut(type){type.default()} as T 
    }
}

private val ServerLevel.levelTiedData get() = LevelTiedDataTracker.getLevelData(this)


// codec can be null, indicating that the datatype should be level-tied but not saved
sealed class LevelDataType<T : LevelTiedData>(val id: String, val default: () -> T, codec: MapCodec<T>? = null) {
    companion object {
        private val REGISTRY: Map<String, LevelDataType<*>> by lazy {
            LevelDataType::class.sealedSubclasses
                .mapNotNull { it.objectInstance }
                .associateBy { it.id }
        }
        fun fromId(id: String): LevelDataType<*> = REGISTRY[id] ?: throw IllegalArgumentException("Unknown LevelDataType: \"$id\"")
            
        val CODEC: Codec<LevelDataType<*>> = Codec.STRING.xmap(::fromId, LevelDataType<*>::id)
    }
    val codec: MapCodec<T> = codec ?: MapCodec.unit(default)
    
    // put another enum value for each tracked data type
    object MapData : LevelDataType<MapDataTracker>("nodes", ::MapDataTracker, MapDataTracker.CODEC)
    object PlayerData : LevelDataType<PlayerDataTracker>("players", ::PlayerDataTracker, PlayerDataTracker.CODEC)
    object GameClock : LevelDataType<GameTimer>("clock", ::GameTimer, GameTimer.CODEC)
    object RaceData : LevelDataType<GameDataTracker>("race", ::GameDataTracker, GameDataTracker.CODEC)
    object EventsQueue : LevelDataType<GameEventsTracker>("events", ::GameEventsTracker, GameEventsTracker.CODEC)
    object LevelScores : LevelDataType<LevelScoreTracker>("scores", ::LevelScoreTracker, LevelScoreTracker.CODEC)
}

// put other level-tied data getters here
val ServerLevel.mapData: MapDataExposer get() = this.levelTiedData.getDataOfType(LevelDataType.MapData)
val ServerLevel.playerData: PlayerDataExposer get() = this.levelTiedData.getDataOfType(LevelDataType.PlayerData)
val ServerLevel.gameClock: TickExposer get() = this.levelTiedData.getDataOfType(LevelDataType.GameClock)
val ServerLevel.raceData: GameDataExposer get() = this.levelTiedData.getDataOfType(LevelDataType.RaceData)
val ServerLevel.eventsQueue: GameEventsExposer get() = this.levelTiedData.getDataOfType(LevelDataType.EventsQueue).also{it.level = this}
val ServerLevel.levelScoreboard: LevelScoreboardHolder get() = this.levelTiedData.getDataOfType(LevelDataType.LevelScores).also{it.level = this}