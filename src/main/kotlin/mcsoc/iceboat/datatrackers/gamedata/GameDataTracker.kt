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
            Codec.unboundedMap(UUIDUtil.STRING_CODEC, LapProgress.CODEC).listOf().fieldOf("nodeProgress").forGetter(GameDataTracker::nodeProgress)
        ).apply(it, ::GameDataTracker)}
    }
    private constructor(activeMapKey: String?, nodeProgress: MutableList<MutableMap<UUID, LapProgress>>) : this() {
        this.activeMapKey = activeMapKey
        nodeProgress.map{it.toMutableMap()}.forEach(this.nodeProgress::add)
    }
    
    override var activeMapKey: String? = null
    override var nodeProgress: MutableList<MutableMap<UUID, LapProgress>> = mutableListOf()
}