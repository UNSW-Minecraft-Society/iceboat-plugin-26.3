@file:JvmName("LevelData")
package mcsoc.iceboat.datatrackers

import com.mojang.serialization.Codec
import com.mojang.serialization.MapCodec
import com.mojang.serialization.codecs.RecordCodecBuilder
import mcsoc.iceboat.IceboatPlugin
import mcsoc.iceboat.datatrackers.mapdata.MapDataExposer
import mcsoc.iceboat.datatrackers.mapdata.MapDataTracker
import net.minecraft.resources.Identifier
import net.minecraft.server.level.ServerLevel
import net.minecraft.util.datafix.DataFixTypes
import net.minecraft.world.level.saveddata.SavedData
import net.minecraft.world.level.saveddata.SavedDataType


// each tracker should extend this class
abstract class LevelTiedData {
    companion object {                
        internal val CODEC: Codec<LevelTiedData> = LevelDataType.CODEC.dispatch(
            { inst -> inst.type },
            { type -> type.codec }
        )
    }
    internal var isDirty: Boolean = false
    protected fun setDirty() {
        this.isDirty = true
    }
    
    abstract val type: LevelDataType<*>
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
        return this.tracked_data.getOrPut(type){type.default} as T 
    }
}

private val ServerLevel.levelTiedData get() = LevelTiedDataTracker.getLevelData(this)


// codec can be null, indicating that the datatype should be level-tied but not saved
sealed class LevelDataType<T : LevelTiedData>(val id: String, codec: MapCodec<T>?, val default: T) {
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
}

// put other level-tied data getters here