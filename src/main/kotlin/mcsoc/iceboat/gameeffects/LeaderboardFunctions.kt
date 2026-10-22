package mcsoc.iceboat.gameeffects

import mcsoc.iceboat.datatrackers.mapData
import mcsoc.iceboat.datatrackers.playerData
import mcsoc.iceboat.datatrackers.raceData
import net.minecraft.network.chat.Component
import net.minecraft.network.chat.numbers.BlankFormat
import net.minecraft.network.chat.numbers.FixedFormat
import net.minecraft.network.chat.numbers.NumberFormat
import net.minecraft.network.chat.numbers.StyledFormat
import net.minecraft.server.level.ServerLevel
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.scores.DisplaySlot
import net.minecraft.world.scores.ScoreHolder
import net.minecraft.world.scores.criteria.ObjectiveCriteria
import java.util.UUID
import kotlin.time.Duration


private const val POSITION_STEP: UInt = 40u
private const val SIDEBAR_TIME_ID = "positions"

fun trackPlayerTimeAtNode(level: ServerLevel, lap: UInt, player: UUID, node: UUID, time: Duration) {
    val nextNode = level.mapData.getNode(node) ?: return
    
    level.playerData.getOrInitPlayerData(player).nodePosition = level.raceData.getNodeProgress(node, lap)?.numPassed ?: 0u
    val isNewFastestTime = level.raceData.trackPlayerPassed(node, lap, player, time)
    
    if (nextNode.endsSplit || nextNode.isFirst) {
        if (level.playerData.trackPlayerTimeAtNode(player, node, nextNode.isFirst, time)) {
            val player = level.server.playerList.getPlayer(player)
            player?.sendSystemMessage(Component.literal("new pb: $time!"))
        } else {
            val player = level.server.playerList.getPlayer(player)
            player?.sendSystemMessage(Component.literal("split time: $time."))
        }
        if (isNewFastestTime) {
            // announce new fastest time idk
        }
    } else {
        level.playerData.trackPlayerPassingNode(player, node)
    }
    
    updateTimeDisplay(level)
}

fun ServerPlayer.trackTimeAtNode(lap: UInt, node: UUID, time: Duration) 
    = trackPlayerTimeAtNode(this.level(), lap, this.uuid, node, time)
    

// NB this is **currently** server-tied rather than level-tied
fun updateTimeDisplay(level: ServerLevel) {
    val scoreboard = level.server.scoreboard
    
    if (level.raceData.activeMapKey.isEmpty()) {
        scoreboard.getObjective(SIDEBAR_TIME_ID)?.let(scoreboard::removeObjective)
        return
    }
    val timeDisplay = scoreboard.getObjective(SIDEBAR_TIME_ID) ?:
        scoreboard.addObjective(
            SIDEBAR_TIME_ID, ObjectiveCriteria.DUMMY, Component.literal("Positions"), 
            ObjectiveCriteria.RenderType.INTEGER, true, BlankFormat.INSTANCE
        ).also {
            scoreboard.setDisplayObjective(DisplaySlot.SIDEBAR, it)
        }
    
    for (player in level.playerData.activePlayerData) {
        val position = (player.nodesPassed * POSITION_STEP - player.nodePosition).toInt()
        
        val positionString = "${player.lapsCompleted}".padStart(2) + " | " + "${player.nodesThisLapPassed}".padEnd(3)
        scoreboard.getOrCreatePlayerScore(ScoreHolder.forNameOnly(player.playerName), timeDisplay, true).let{
            it.set(position)
            it.numberFormatOverride(FixedFormat(Component.literal(positionString)))
        }
    }
}