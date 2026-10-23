package mcsoc.iceboat.datatrackers

import com.mojang.serialization.codecs.RecordCodecBuilder
import mcsoc.iceboat.utils.LevelScoreboardHolder
import net.minecraft.server.level.ServerLevel
import net.minecraft.world.scores.Scoreboard
import java.util.UUID

class LevelScoreTracker : LevelTiedData(), LevelScoreboardHolder {
    override fun getType(): LevelDataType<*> = LevelDataType.LevelScores
    
    override lateinit var level: ServerLevel
    override val dirtyObjectives: MutableCollection<String> = mutableSetOf()
    
    override val trackingPlayers: MutableCollection<UUID> get() = level.playerData.activePlayers
    override val scoreboard: Scoreboard = Scoreboard()
}