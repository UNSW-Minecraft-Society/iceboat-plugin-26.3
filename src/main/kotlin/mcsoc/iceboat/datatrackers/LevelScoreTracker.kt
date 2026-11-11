package mcsoc.iceboat.datatrackers

import com.mojang.serialization.Codec
import com.mojang.serialization.MapCodec
import com.mojang.serialization.codecs.RecordCodecBuilder
import com.mojang.datafixers.util.Pair as CodecPair

import mcsoc.iceboat.utils.clientdisplay.ClientBossBarHolder
import mcsoc.iceboat.utils.clientdisplay.PlayerTiedScoreHolder
import mcsoc.iceboat.utils.clientdisplay.PlayerTiedScoreboard

import net.minecraft.core.UUIDUtil
import net.minecraft.network.chat.ComponentSerialization
import net.minecraft.network.chat.numbers.NumberFormatTypes
import net.minecraft.resources.Identifier
import net.minecraft.server.bossevents.CustomBossEvent
import net.minecraft.server.level.ServerLevel
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.scores.Objective
import net.minecraft.world.scores.PlayerScoreEntry
import net.minecraft.world.scores.ScoreHolder
import net.minecraft.world.scores.Scoreboard

import java.util.Optional
import java.util.UUID
import kotlin.properties.Delegates


private data class BossBarRecord(val bossBar: CustomBossEvent.Packed, val id: Identifier) {
    companion object {
        val CODEC: Codec<BossBarRecord> = RecordCodecBuilder.create{it.group(
            CustomBossEvent.Packed.CODEC.fieldOf("bossBar").forGetter(BossBarRecord::bossBar),
            Identifier.CODEC.fieldOf("id").forGetter(BossBarRecord::id)
        ).apply(it, ::BossBarRecord)}
    }
}

class LevelScoreTracker() : LevelTiedData(), PlayerTiedScoreHolder, ClientBossBarHolder {
    override fun setTiedLevel(level: ServerLevel) {
        super.setTiedLevel(level)
        this.scoreboard.level = level
    }

    override fun getServerPlayer(playerId: UUID): ServerPlayer? = this.level.server.playerList.getPlayer(playerId)
    
    override fun getType(): LevelDataType<*> = LevelDataType.LevelScores
    companion object {
        val CODEC: MapCodec<LevelScoreTracker> = RecordCodecBuilder.mapCodec{it.group(
            PlayerTiedScoreboard.CODEC.fieldOf("scoreboard").forGetter(LevelScoreTracker::scoreboard),
            Codec.unboundedMap(
                UUIDUtil.STRING_CODEC,
                BossBarRecord.CODEC
            ).fieldOf("bossEventMap").forGetter(LevelScoreTracker::packBossBars)
        ).apply(it, ::LevelScoreTracker)}
    }
    private constructor(scoreboard: PlayerTiedScoreboard, bossEventMap: Map<UUID, BossBarRecord>) : this() {
        this.scoreboard = scoreboard
        this.bossEventMap.putAll(bossEventMap.mapValues{(k, v) -> CustomBossEvent.load(k, v.id, v.bossBar){}})
    }
    
    override var scoreboard: PlayerTiedScoreboard = PlayerTiedScoreboard()
        private set
    override val bossEventMap: MutableMap<UUID, CustomBossEvent> = mutableMapOf()
    
    private fun packBossBars(): Map<UUID, BossBarRecord> {
        return bossEventMap.mapValues{BossBarRecord(it.value.pack(), it.value.customId())}
    }
}