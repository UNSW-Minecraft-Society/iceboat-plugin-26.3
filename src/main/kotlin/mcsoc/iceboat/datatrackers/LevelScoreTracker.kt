package mcsoc.iceboat.datatrackers

import com.mojang.serialization.Codec
import com.mojang.serialization.MapCodec
import com.mojang.serialization.codecs.RecordCodecBuilder
import mcsoc.iceboat.utils.clientdisplay.ClientBossBarHolder
import com.mojang.datafixers.util.Pair as CodecPair
import mcsoc.iceboat.utils.clientdisplay.LevelScoreboardExposer
import mcsoc.iceboat.utils.clientdisplay.LevelScoreboardHolder
import net.minecraft.network.chat.ComponentSerialization
import net.minecraft.network.chat.numbers.NumberFormatTypes
import net.minecraft.server.bossevents.CustomBossEvent
import net.minecraft.server.level.ServerLevel
import net.minecraft.world.scores.Objective
import net.minecraft.world.scores.PlayerScoreEntry
import net.minecraft.world.scores.ScoreHolder
import net.minecraft.world.scores.Scoreboard
import java.util.Optional
import java.util.UUID


private val PLAYER_SCORE_ENTRY_CODEC: Codec<PlayerScoreEntry> = RecordCodecBuilder.create { instance ->
    instance.group(
        Codec.STRING.fieldOf("owner").forGetter(PlayerScoreEntry::owner),
        Codec.INT.fieldOf("value").forGetter(PlayerScoreEntry::value),
        ComponentSerialization.CODEC.optionalFieldOf("displayName").forGetter{Optional.ofNullable(it.display)},
        NumberFormatTypes.CODEC.optionalFieldOf("numberFormat").forGetter{Optional.ofNullable(it.numberFormatOverride)}
    ).apply(instance){owner, value, display, numberFormat ->
        PlayerScoreEntry(
            owner, value,
            display.orElse(null),
            numberFormat.orElse(null)
        )
    }
}

private val OBJECTIVE_ENTRY_CODEC: Codec<CodecPair<Objective.Packed, List<PlayerScoreEntry>>> = RecordCodecBuilder.create{it.group(
        Objective.Packed.CODEC.fieldOf("objective").forGetter(CodecPair<Objective.Packed, List<PlayerScoreEntry>>::getFirst),
        PLAYER_SCORE_ENTRY_CODEC.listOf().fieldOf("scores").forGetter(CodecPair<Objective.Packed, List<PlayerScoreEntry>>::getSecond)
    ).apply(it, ::CodecPair)
}

private val SCOREBOARD_CODEC: Codec<Scoreboard> = RecordCodecBuilder.create{it.group(
    OBJECTIVE_ENTRY_CODEC.listOf().fieldOf("packedScores").forGetter{it.getEntries()}
).apply(it){objectives ->
    Scoreboard().also {
        for (entry in objectives) {
            val packedObjective = entry.first
            val scores = entry.second

            val objective = it.addObjective(
                packedObjective.name, packedObjective.criteria, packedObjective.displayName,
                packedObjective.renderType, packedObjective.displayAutoUpdate,
                packedObjective.numberFormat.orElse(null)
            )
            for (score in scores) {
                it.getOrCreatePlayerScore(ScoreHolder.forNameOnly(score.owner), objective).also{
                    it.set(score.value)
                    it.numberFormatOverride(score.numberFormatOverride)
                    it.display(score.display)
                }
            }
        }
    }
}}

private fun Scoreboard.getEntries(): List<CodecPair<Objective.Packed, List<PlayerScoreEntry>>> {
    return this.objectives.map{CodecPair(it.pack(), this.listPlayerScores(it).toList())}
}

class LevelScoreTracker private constructor(override val scoreboard: Scoreboard) : LevelTiedData(),
    LevelScoreboardHolder, ClientBossBarHolder {
    constructor() : this(Scoreboard())
    override fun getType(): LevelDataType<*> = LevelDataType.LevelScores
    companion object {
        val CODEC: MapCodec<LevelScoreTracker> = RecordCodecBuilder.mapCodec{it.group(
            SCOREBOARD_CODEC.fieldOf("scoreboard").forGetter(LevelScoreTracker::scoreboard)
        ).apply(it, ::LevelScoreTracker)}
    }
    
    override lateinit var level: ServerLevel
    override val bossEventMap: MutableMap<UUID, CustomBossEvent> = mutableMapOf()
    override val dirtyObjectives: MutableCollection<String> = mutableSetOf()
    override val dirtyScores: MutableMap<String, MutableCollection<String>> = mutableMapOf()
    
    // serialise this
    override val trackingPlayers: MutableCollection<UUID> get() = level.playerData.activePlayers
}