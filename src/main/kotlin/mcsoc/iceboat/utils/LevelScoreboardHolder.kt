package mcsoc.iceboat.utils

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


//
interface LevelScoreboardHolder {
    var level: ServerLevel
    val dirtyObjectives: MutableCollection<String>
    val dirtyScores: MutableMap<String, MutableCollection<String>>
    
    val trackingPlayers: MutableCollection<UUID>
    val scoreboard: Scoreboard
    
    fun tryAddObjective(objectiveName: String, displayName: Component, numberFormat: NumberFormat?): Objective {
        return scoreboard.getObjective(objectiveName) ?: run {
            scoreboard.addObjective(
                objectiveName, ObjectiveCriteria.DUMMY, displayName,
                ObjectiveCriteria.RenderType.INTEGER, true, numberFormat
            ).also{
                dirtyObjectives.add(objectiveName)
            }
        }
    }
    
    private fun getOrInitObjective(objectiveName: String): Objective {
        return scoreboard.getObjective(objectiveName) ?: throw IllegalStateException()
            // tryAddObjective(objectiveName, Component.literal(objectiveName), null)
    }

    private fun getDummyScoreAccess(objectiveName: String, dummy: String): ScoreAccess {
        return scoreboard.getOrCreatePlayerScore(ScoreHolder.forNameOnly(dummy), getOrInitObjective(objectiveName))
    }
    private fun getPlayerScoreAccess(objectiveName: String, player: Player): ScoreAccess {
        return scoreboard.getOrCreatePlayerScore(ScoreHolder.fromGameProfile(player.gameProfile), getOrInitObjective(objectiveName))
    }

    fun setDummyScore(objectiveName: String, dummyName: String, score: Int, numberFormat: NumberFormat? = null) {
        getDummyScoreAccess(objectiveName, dummyName).also{
            it.set(score)
            it.numberFormatOverride(numberFormat)
        }
        dirtyScores[objectiveName]?.add(dummyName) ?: run {
            dirtyScores[objectiveName] = mutableListOf(dummyName)
        }
    }
    fun setPlayerScore(objectiveName: String, player: Player, score: Int, numberFormat: NumberFormat? = null) {
        getPlayerScoreAccess(objectiveName, player).also{
            it.set(score)
            it.numberFormatOverride(numberFormat)
        }
        dirtyScores[objectiveName]?.add(player.gameProfile.name) ?: run {
            dirtyScores[objectiveName] = mutableListOf(player.gameProfile.name)
        }
    }
    
    fun clearScores(objectiveName: String) {
        val objective = getOrInitObjective(objectiveName)
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
        val objective = getOrInitObjective(objectiveName)
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
        val objective = getOrInitObjective(objectiveName)
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
        val objective = getOrInitObjective(objectiveName)
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
        for (objective in dirtyObjectives) {
            sendAllScoreboardValuesToTracking(objective)
            dirtyObjectives.remove(objective)
        }
        
        for ((objectiveName, scores) in dirtyScores) {
            val objective = getOrInitObjective(objectiveName)
            val scores = scoreboard.listPlayerScores(objective).filter{it.owner in scores}
            sendScoreboardValuesToTracking(objectiveName, scores)
            dirtyScores.remove(objectiveName)
        }
    }
}
