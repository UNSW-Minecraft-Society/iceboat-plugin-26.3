package mcsoc.iceboat.utils.clientdisplay

import net.minecraft.network.chat.Component
import net.minecraft.network.chat.numbers.NumberFormat
import net.minecraft.network.protocol.game.ClientboundSetDisplayObjectivePacket
import net.minecraft.network.protocol.game.ClientboundSetObjectivePacket
import net.minecraft.network.protocol.game.ClientboundSetScorePacket
import net.minecraft.server.level.ServerLevel
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.entity.player.Player
import net.minecraft.world.scores.DisplaySlot
import net.minecraft.world.scores.Objective
import net.minecraft.world.scores.PlayerScoreEntry
import net.minecraft.world.scores.ScoreAccess
import net.minecraft.world.scores.ScoreHolder
import net.minecraft.world.scores.Scoreboard
import net.minecraft.world.scores.criteria.ObjectiveCriteria
import java.util.Optional
import java.util.UUID


private fun Scoreboard.getOrInitObjective(objectiveName: String): Objective {
    return this.getObjective(objectiveName) ?: throw IllegalStateException()
    // tryAddObjective(objectiveName, Component.literal(objectiveName), null)
}

//
interface LevelScoreboardExposer {
    val level: ServerLevel
    val scoreboard: Scoreboard
    
    val dirtyObjectives: Collection<String>
    val dirtyScores: Map<String, MutableCollection<String>>
    
    val trackingPlayers: Collection<UUID>
    
    fun tryAddObjective(objectiveName: String, displayName: Component, numberFormat: NumberFormat?): Objective
    
    fun handleDirtyObjective(objectiveName: String)
    fun handleDirtyScores(objectiveName: String, scores: Collection<String>)
    
    fun setDummyScore(objectiveName: String, dummyName: String, score: Int, numberFormat: NumberFormat? = null)
    fun setPlayerScore(objectiveName: String, player: Player, score: Int, numberFormat: NumberFormat? = null)
    
    
    fun clearScores(objectiveName: String) {
        val objective = scoreboard.getOrInitObjective(objectiveName)
        for (playerId in trackingPlayers) {
            val player = level.server.playerList.getPlayer(playerId) ?: continue
            player.connection.send(
                ClientboundSetObjectivePacket(
                    objective,
                    ClientboundSetObjectivePacket.METHOD_REMOVE
                )
            )
        }
        scoreboard.removeObjective(objective)
    }
    
    
    fun sendScoreboard(objectiveName: String, displaySlots: Iterable<DisplaySlot>, player: ServerPlayer) {
        val objective = scoreboard.getOrInitObjective(objectiveName)
        player.connection.send(
            ClientboundSetObjectivePacket(
                objective,
                ClientboundSetObjectivePacket.METHOD_ADD
            )
        )
        for (slot in displaySlots) {
            player.connection.send(
                ClientboundSetDisplayObjectivePacket(
                    slot, objective
                )
            )
        }
    }
    
    fun sendScoreboardValues(objectiveName: String, player: ServerPlayer, scores: Iterable<PlayerScoreEntry>) {
        val objective = scoreboard.getOrInitObjective(objectiveName)
        for (score in scores) {
            player.connection.send(
                ClientboundSetScorePacket(
                    score.owner,
                    objectiveName,
                    score.value,
                    Optional.of(Component.literal(score.owner)),
                    Optional.ofNullable(score.numberFormatOverride)
                )
            )
        }
    }
    
    fun sendAllScoreboardValues(objectiveName: String, player: ServerPlayer) {
        val objective = scoreboard.getOrInitObjective(objectiveName)
        sendScoreboardValues(objectiveName, player, scoreboard.listPlayerScores(objective))
    }
    
    fun sendScoreboardToTracking(objectiveName: String, displaySlots: Iterable<DisplaySlot>) {
        for (playerId in trackingPlayers) {
            val player = level.server.playerList.getPlayer(playerId) ?: continue
            sendScoreboard(objectiveName, displaySlots, player)
        }
    }
    
    fun sendScoreboardValuesToTracking(objectiveName: String, scores: Iterable<PlayerScoreEntry>) {
        for (playerId in trackingPlayers) {
            val player = level.server.playerList.getPlayer(playerId) ?: continue
            sendScoreboardValues(objectiveName, player, scores)
        }
    }
    
    fun sendAllScoreboardValuesToTracking(objectiveName: String) {
        for (playerId in trackingPlayers) {
            val player = level.server.playerList.getPlayer(playerId) ?: continue
            sendAllScoreboardValues(objectiveName, player)
        }
    }
    
    
    fun tick() {
        for (objectiveName in dirtyObjectives) {
            handleDirtyObjective(objectiveName)
        }
        
        for ((objectiveName, scores) in dirtyScores) {
            handleDirtyScores(objectiveName, scores)
        }
    }
}


interface LevelScoreboardHolder : LevelScoreboardExposer {
    override val dirtyObjectives: MutableCollection<String>
    override val dirtyScores: MutableMap<String, MutableCollection<String>>
    override val trackingPlayers: MutableCollection<UUID>

    override fun tryAddObjective(objectiveName: String, displayName: Component, numberFormat: NumberFormat?): Objective {
        return scoreboard.getObjective(objectiveName) ?: run {
            scoreboard.addObjective(
                objectiveName, ObjectiveCriteria.DUMMY, displayName,
                ObjectiveCriteria.RenderType.INTEGER, true, numberFormat
            ).also{
                dirtyObjectives.add(objectiveName)
            }
        }
    }

    override fun setDummyScore(objectiveName: String, dummyName: String, score: Int, numberFormat: NumberFormat?) {
        getDummyScoreAccess(objectiveName, dummyName).also{
            it.set(score)
            it.numberFormatOverride(numberFormat)
        }
        dirtyScores[objectiveName]?.add(dummyName) ?: run {
            dirtyScores[objectiveName] = mutableListOf(dummyName)
        }
    }
    override fun setPlayerScore(objectiveName: String, player: Player, score: Int, numberFormat: NumberFormat?) {
        getPlayerScoreAccess(objectiveName, player).also{
            it.set(score)
            it.numberFormatOverride(numberFormat)
        }
        dirtyScores[objectiveName]?.add(player.gameProfile.name) ?: run {
            dirtyScores[objectiveName] = mutableListOf(player.gameProfile.name)
        }
    }

    override fun handleDirtyObjective(objectiveName: String) {
        sendAllScoreboardValuesToTracking(objectiveName)
        dirtyObjectives.remove(objectiveName)
    }
    override fun handleDirtyScores(objectiveName: String, scores: Collection<String>) {
        val objective = scoreboard.getOrInitObjective(objectiveName)
        val scores = scoreboard.listPlayerScores(objective).filter{it.owner in scores}
        sendScoreboardValuesToTracking(objectiveName, scores)
        dirtyScores.remove(objectiveName)
    }

    
    private fun getDummyScoreAccess(objectiveName: String, dummy: String): ScoreAccess {
        return scoreboard.getOrCreatePlayerScore(ScoreHolder.forNameOnly(dummy), scoreboard.getOrInitObjective(objectiveName))
    }
    private fun getPlayerScoreAccess(objectiveName: String, player: Player): ScoreAccess {
        return scoreboard.getOrCreatePlayerScore(ScoreHolder.fromGameProfile(player.gameProfile), scoreboard.getOrInitObjective(objectiveName))
    }
}
