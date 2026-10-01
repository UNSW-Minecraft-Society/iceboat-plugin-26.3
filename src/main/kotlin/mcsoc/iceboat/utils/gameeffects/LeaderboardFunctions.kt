package mcsoc.iceboat.utils.gameeffects

import mcsoc.iceboat.datatrackers.mapData
import mcsoc.iceboat.datatrackers.playerData
import net.minecraft.network.chat.Component
import net.minecraft.network.chat.numbers.BlankFormat
import net.minecraft.server.level.ServerLevel
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.scores.DisplaySlot
import net.minecraft.world.scores.Objective
import net.minecraft.world.scores.ScoreHolder
import net.minecraft.world.scores.Scoreboard
import net.minecraft.world.scores.criteria.ObjectiveCriteria
import java.util.UUID
import kotlin.time.Duration


private const val POSITION_STEP: UInt = 40u
private const val SIDEBAR_TIME_ID = "times"

fun trackPlayerTimeAtNode(level: ServerLevel, lap: UInt, player: UUID, node: UUID, time: Duration) {
    level.mapData.trackPlayerTime(node, level, player, lap)
    val index = (level.mapData.getNode(node) ?: return)
        .also{ level.playerData.getOrInitPlayerData(player).nodePosition = it.playerProgress[lap.toInt()].numPassed++ }
        .index
    if (level.playerData.trackPlayerTimeAtNode(player, node, index, time)) {
        val player = level.server.playerList.getPlayer(player)
        player?.sendSystemMessage(Component.literal("new pb: $time!"))
    } else {
        val player = level.server.playerList.getPlayer(player)
        player?.sendSystemMessage(Component.literal("split time: $time."))
    }
    
    updateTimeDisplay(level)
}

fun ServerPlayer.trackTimeAtNode(lap: UInt, node: UUID, time: Duration) 
    = trackPlayerTimeAtNode(this.level(), lap, this.uuid, node, time)
    

fun updateTimeDisplay(level: ServerLevel) {
    val scoreboard = level.server.scoreboard
    val timeDisplay = scoreboard.getObjective(SIDEBAR_TIME_ID) ?:
        scoreboard.addObjective(
            SIDEBAR_TIME_ID, ObjectiveCriteria.DUMMY, Component.literal("Times"), 
            ObjectiveCriteria.RenderType.INTEGER, true, BlankFormat.INSTANCE
        ).also {
            scoreboard.setDisplayObjective(DisplaySlot.SIDEBAR, it)
        }
    
    for (player in level.playerData.activePlayerData) {
        val position = ((player.nodesPassed + 1u) * POSITION_STEP - player.nodePosition).toInt()
        scoreboard.getOrCreatePlayerScore(ScoreHolder.forNameOnly(player.playerName), timeDisplay, true).set(position)
    }
}