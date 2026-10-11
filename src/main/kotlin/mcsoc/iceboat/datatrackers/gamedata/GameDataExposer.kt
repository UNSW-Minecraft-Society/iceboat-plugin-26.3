package mcsoc.iceboat.datatrackers.gamedata

import com.mojang.serialization.Codec
import com.mojang.serialization.codecs.RecordCodecBuilder
import mcsoc.iceboat.utils.CODEC
import net.minecraft.core.UUIDUtil
import java.util.UUID
import kotlin.time.Duration


data class LapProgress(
    var fastestPlayer: com.mojang.datafixers.util.Pair<UUID, Duration>? = null,
    var numPassed: UInt = 0u
) : Comparable<Duration> {
    companion object {
        val CODEC: Codec<LapProgress> = RecordCodecBuilder.create{it.group(
            Codec.pair(UUIDUtil.CODEC, Duration.CODEC).fieldOf("fastestPlayer").forGetter(LapProgress::fastestPlayer),
            UInt.CODEC.fieldOf("numPassed").forGetter(LapProgress::numPassed),
        ).apply(it, ::LapProgress)}
    }
    
    fun setFastestPlayer(fastestPlayer: UUID, fastestTime: Duration) {
        this.fastestPlayer = com.mojang.datafixers.util.Pair(fastestPlayer, fastestTime)
    }
    
    // return greater if no time has been set yet
    override fun compareTo(other: Duration): Int {
        return this.fastestPlayer?.second?.compareTo(other) ?: 1
    }
}

interface GameDataExposer {
    val activeMapKey: String
    fun setActiveMap(key: String)
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
}

internal interface GameDataHolder : GameDataExposer {
    override var activeMapKey: String
    
    private fun setActiveMapInternal(key: String?) { 
        activeMapKey = (key ?: "")
        // clear old race data
        nodeProgress.clear()
    }
    override fun setActiveMap(key: String) = setActiveMapInternal(key)
    override fun setInactive() = setActiveMapInternal(null)
    
    override var nodeProgress: MutableList<MutableMap<UUID, LapProgress>>
    override fun getNodeProgress(node: UUID, lap: Int): LapProgress = super.getNodeProgress(node, lap) ?: run {
        while (nodeProgress.size <= lap) nodeProgress.add(mutableMapOf())
        LapProgress().also{ nodeProgress[lap][node] = it }
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