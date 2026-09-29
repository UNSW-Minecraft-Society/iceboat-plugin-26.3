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


const val POSITION_STEP: UInt = 40u
const val SIDEBAR_TIME_ID = "times"

fun trackPlayerTimeAtNode(level: ServerLevel, player: UUID, node: UUID, time: Duration) {
    if (level.playerData.trackPlayerTimeAtNode(player, node, time)) {
        val player = level.server.playerList.getPlayer(player)
        player?.sendSystemMessage(Component.literal("new pb: $time!"))
    } else {
        val player = level.server.playerList.getPlayer(player)
        player?.sendSystemMessage(Component.literal("split time: $time."))
    }
    level.mapData.trackPlayerTime(node, level, player)
    updateTimeDisplay(level)
}

fun ServerPlayer.trackTimeAtNode(node: UUID, time: Duration) 
    = trackPlayerTimeAtNode(this.level(), this.uuid, node, time)
    

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
        val position = player.nodesPassed
        scoreboard.getOrCreatePlayerScore(ScoreHolder.forNameOnly(player.playerName), timeDisplay, true).set((POSITION_STEP * position).toInt())
    }
}