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
    
    val trackingPlayers: MutableCollection<UUID>
    val scoreboard: Scoreboard
    
    fun tryAddObjective(objectiveName: String, displayName: Component, numberFormat: NumberFormat?): Objective {
        return scoreboard.getObjective(objectiveName) ?: scoreboard.addObjective(
            objectiveName, ObjectiveCriteria.DUMMY, displayName,
            ObjectiveCriteria.RenderType.INTEGER, true, numberFormat
        )
    }
    
    private fun getOrInitObjective(objectiveName: String): Objective {
        return scoreboard.getObjective(objectiveName) ?: tryAddObjective(
            objectiveName, Component.literal(objectiveName), null
        )
    }

    private fun getScoreAccess(objectiveName: String, player: Player): ScoreAccess {
        return scoreboard.getOrCreatePlayerScore(ScoreHolder.fromGameProfile(player.gameProfile), getOrInitObjective(objectiveName))
    }
    
    
    fun setPlayerScore(objectiveName: String, player: Player, score: Int, numberFormat: NumberFormat? = null) {
        getScoreAccess(objectiveName, player).also{
            it.set(score)
            it.numberFormatOverride(numberFormat)
        }
        dirtyObjectives.add(objectiveName)
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
        dirtyObjectives.add(objectiveName)
    }
    
    
    fun sendScoreboard(objectiveName: String, displaySlot: DisplaySlot, player: ServerPlayer) {
        val objective = getOrInitObjective(objectiveName)
        player.connection.send(
            ClientboundSetObjectivePacket(
                objective,
                ClientboundSetObjectivePacket.METHOD_ADD
            )
        )
        player.connection.send(
            ClientboundSetDisplayObjectivePacket(
                displaySlot, objective
            )
        )
    }
    
    fun sendScoreboardValues(objectiveName: String, player: ServerPlayer) {
        val objective = getOrInitObjective(objectiveName)
        for (score in scoreboard.listPlayerScores(objective)) {
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
    
    fun sendScoreboardToTracking(objectiveName: String, displaySlot: DisplaySlot) {
        for (playerId in trackingPlayers) {
            val player = level.server.playerList.getPlayer(playerId) ?: continue
            sendScoreboard(objectiveName, displaySlot, player)
        }
    }
    
    fun sendScoreboardValuesToTracking(objectiveName: String) {
        for (playerId in trackingPlayers) {
            val player = level.server.playerList.getPlayer(playerId) ?: continue
            sendScoreboardValues(objectiveName, player)
        }
    }
    
    
    fun tick() {
        for (objective in dirtyObjectives) {
            sendScoreboardValuesToTracking(objective)
            dirtyObjectives.remove(objective)
        }
    }
}
