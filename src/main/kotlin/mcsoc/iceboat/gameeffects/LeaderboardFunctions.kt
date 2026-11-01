package mcsoc.iceboat.gameeffects

import mcsoc.iceboat.IceboatPlugin
import mcsoc.iceboat.datatrackers.levelBossBars
import mcsoc.iceboat.datatrackers.levelScoreboard
import mcsoc.iceboat.datatrackers.mapData
import mcsoc.iceboat.datatrackers.playerData
import mcsoc.iceboat.datatrackers.raceData
import net.minecraft.ChatFormatting
import net.minecraft.network.chat.Component
import net.minecraft.network.chat.numbers.BlankFormat
import net.minecraft.network.chat.numbers.FixedFormat
import net.minecraft.server.level.ServerLevel
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.scores.DisplaySlot
import java.util.UUID
import kotlin.time.Duration


// distance between nodes on the scoreboard (eg node 1 is 1-40, node 2 is at 41-80, ...)
//  This should be bigger than the expected number of players
private const val POSITION_STEP: UInt = 40u
// the id of the scoreboard (this does not matter, and can be changed whenever)
private const val SIDEBAR_TIME_ID = "positions"
// What to display as position scoreboard title
private val SIDEBAR_POSITIONS_DISPLAY = Component.literal("Positions")
        .withStyle(ChatFormatting.BOLD)
        .withStyle(ChatFormatting.GRAY)
// An arbitrary highest scoreboard value (so it shows at top). No regular score should exceed this value!
private const val HIGHEST_SCOREBOARD_VALUE = 99999


private val playerPositionsSort: (ServerLevel) -> ((UUID, UUID) -> Int) = {
    val playersData = it.playerData
    
    {p1, p2 ->
        val nodesCompare = playersData.getOrInitPlayerData(p2).nodesPassed.compareTo(playersData.getOrInitPlayerData(p1).nodesPassed)
        if (nodesCompare != 0) nodesCompare
        else playersData.getOrInitPlayerData(p1).nodePosition.compareTo(playersData.getOrInitPlayerData(p2).nodePosition)
    }
}

/* Handle updating player score and sending feedback when they go over a race node
 * Arguments
 *  level - The world (REVOLVING)
 */
fun ServerLevel.trackPlayerTimeAtNode(lap: UInt, playerId: UUID, nodeId: UUID, splitTime: Duration) {
    val nextNode = this.mapData.getNode(nodeId) ?: return

    this.playerData.getOrInitPlayerData(playerId).nodePosition = this.raceData.getNodeProgress(nodeId, lap)?.numPassed ?: 0u
    val isNewFastestTime = this.raceData.trackPlayerPassed(nodeId, lap, playerId, splitTime)
    
    if (nextNode.endsSplit || nextNode.isFirst) {
        if (this.playerData.trackPlayerTimeAtNode(playerId, nodeId, nextNode.isFirst, splitTime)) {
            val player = this.server.playerList.getPlayer(playerId)
            player?.sendSystemMessage(Component.literal("new pb: $splitTime!"))
        } else {
            val player = this.server.playerList.getPlayer(playerId)
            player?.sendSystemMessage(Component.literal("split time: $splitTime."))
        }
        if (isNewFastestTime) {
            // announce new fastest time idk
        }
    } else {
        this.playerData.trackPlayerPassingNode(playerId, nodeId)
    }
    
    this.raceData.sortPositions(this, playerPositionsSort)
    this.updateTimeDisplay()
}
fun ServerPlayer.trackTimeAtNode(lap: UInt, nodeId: UUID, splitTime: Duration) 
    = this.level().trackPlayerTimeAtNode(lap, this.uuid, nodeId, splitTime)


// set the LAPS display at the top of the sidebar
fun ServerLevel.addLapsIndicatorToSidebar() {
    val scoreboard = this.levelScoreboard
    scoreboard.tryAddObjective(SIDEBAR_TIME_ID, SIDEBAR_POSITIONS_DISPLAY, BlankFormat.INSTANCE)
    
    val numberFormat = FixedFormat(Component.literal("${this.raceData.numLaps.toInt()}".padStart(2).padEnd(8))
        .withStyle(ChatFormatting.GRAY))

    scoreboard.setScore(SIDEBAR_TIME_ID, "LAPS:", HIGHEST_SCOREBOARD_VALUE, numberFormat)
}

// Refresh all player position indicators on sidebar based on player's position
fun ServerLevel.updateTimeDisplay() {
    val scoreboard = this.levelScoreboard
    scoreboard.tryAddObjective(SIDEBAR_TIME_ID, SIDEBAR_POSITIONS_DISPLAY, BlankFormat.INSTANCE)
    
    if (this.raceData.activeMapKey.isEmpty()) {
        scoreboard.removeObjective(SIDEBAR_TIME_ID)
        return
    }
    
    for (playerId in this.playerData.activePlayers) {
        val player: ServerPlayer = this.server.playerList.getPlayer(playerId) ?: continue
        val playerData = this.playerData.getOrInitPlayerData(playerId)
        
        val position = (playerData.nodesPassed * POSITION_STEP - playerData.nodePosition).toInt()
        val positionString = if (playerData.lapsCompleted < this.raceData.numLaps) {
            Component.literal("${playerData.lapsCompleted + 1u}".padStart(2) + " | " + "${playerData.nodesThisLapPassed}".padEnd(3))
        } else {
            Component.literal("FIN".padEnd(6))
                .withStyle(ChatFormatting.ITALIC)
                .withStyle(ChatFormatting.GREEN)
        }
        
        val numberFormat = FixedFormat(positionString)
        scoreboard.setScore(SIDEBAR_TIME_ID, player, position, numberFormat)
    }
}


fun ServerLevel.initProgressBarFor(playerId: UUID) {
    this.levelBossBars.addBossBar(IceboatPlugin.id("lap_progress"), Component.literal("Lap Progress"), playerId)
    this.levelBossBars.addPlayerTo(playerId, playerId)
}

fun ServerLevel.initScoreboardFor(playerId: UUID) {
    this.levelScoreboard.addPlayerToTracking(playerId, SIDEBAR_TIME_ID, listOf(DisplaySlot.LIST, DisplaySlot.SIDEBAR))
}

// Refresh boss bar which indicates lap progress
fun ServerLevel.updateProgressBarDisplay(playerId: UUID) {
    val player = this.server.playerList.getPlayer(playerId) ?: return    
    val numNodes = this.mapData.getMapData(this.raceData.activeMapKey)?.nodeCount ?: return
    this.levelBossBars.setBarProgress(playerId, this.playerData.getOrInitPlayerData(player).nodesThisLapPassed.toFloat() / numNodes.toFloat())
}