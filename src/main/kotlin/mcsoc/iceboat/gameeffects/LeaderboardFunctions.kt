package mcsoc.iceboat.gameeffects

import mcsoc.iceboat.IceboatPlugin
import mcsoc.iceboat.datatrackers.levelBossBars
import mcsoc.iceboat.datatrackers.levelScoreboard
import mcsoc.iceboat.datatrackers.mapData
import mcsoc.iceboat.datatrackers.playerData
import mcsoc.iceboat.datatrackers.raceData
import mcsoc.iceboat.utils.formatMinuteSecondMillis
import mcsoc.iceboat.utils.formatSecondMillis
import net.minecraft.ChatFormatting
import net.minecraft.network.chat.Component
import net.minecraft.network.chat.Style
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
        val nodesCompare = playersData.getOrInitPlayerData(p2).totalNodesPassed.compareTo(playersData.getOrInitPlayerData(p1).totalNodesPassed)
        if (nodesCompare != 0) nodesCompare
        else playersData.getOrInitPlayerData(p1).nodePosition.compareTo(playersData.getOrInitPlayerData(p2).nodePosition)
    }
}

/* Handle updating player score and sending feedback when they go over a race node
 * Arguments
 *  level - The world (REVOLVING)
 */
fun ServerLevel.trackPlayerTimeAtNode(playerId: UUID, nodeId: UUID, raceTime: Duration) {
    val nextNode = this.mapData.getNode(nodeId) ?: return

    val playerData = this.playerData.getOrInitPlayerData(playerId)
    val lapNumber = playerData.lapsCompleted.toInt()
    val splitNumber = playerData.currentSplit.toInt()
    
    playerData.nodePosition = this.raceData.getNodeProgress(nodeId, lapNumber)?.numPassed ?: 0u

    this.playerData.trackPlayerTimeAtNode(playerId, raceTime, nextNode.isFirst, nextNode.endsSplit)?.let{
        val splitTime = playerData.mostRecentTime
        val splitTimeString = splitTime.formatMinuteSecondMillis
        
        val splitMsg = Component.literal("Time for Split ${splitNumber + 1}: ")
            .append(Component.literal(splitTimeString).withStyle(Style.EMPTY
                .withUnderlined(true)
                .withColor(ChatFormatting.GRAY)
            ))
            .append(
                if (lapNumber != 0) {
                    // positive means took longer
                    val splitDelta = splitTime - playerData.splitTimes[lapNumber - 1][splitNumber]
                    val deltaString = splitDelta.absoluteValue.formatSecondMillis
                    
                    Component.literal(" (").withStyle(ChatFormatting.GRAY).append(
                        if (splitDelta.isNegative()) {
                            Component.literal("-$deltaString").withStyle(ChatFormatting.GREEN).append(
                                if (it) {
                                    Component.literal(" (PB)").withStyle(ChatFormatting.DARK_GREEN)
                                } else Component.empty()
                            )
                        } else {
                            Component.literal("+$deltaString").withStyle(ChatFormatting.RED)
                        }
                    ).append(Component.literal(")").withStyle(ChatFormatting.GRAY))
                } else Component.empty()
            ).append(Component.literal("."))
        
        this.server.playerList.getPlayer(playerId)?.sendSystemMessage(splitMsg)
    }
    if (this.raceData.trackPlayerPassed(nodeId, lapNumber.toUInt(), playerId, playerData.mostRecentTime) && lapNumber > 0) {
        // announce new fastest time idk
    }
        
    this.raceData.sortPositions(this, playerPositionsSort)
    this.updateTimeDisplay()
}
fun ServerPlayer.trackTimeAtNode(nodeId: UUID, raceTime: Duration) 
    = this.level().trackPlayerTimeAtNode(this.uuid, nodeId, raceTime)


// set the LAPS display at the top of the sidebar
fun ServerLevel.addLapsIndicatorToSidebar() {
    val scoreboard = this.levelScoreboard
    scoreboard.tryAddObjective(SIDEBAR_TIME_ID, SIDEBAR_POSITIONS_DISPLAY, BlankFormat.INSTANCE)
    
    IceboatPlugin.LOGGER.info("phase: {}, prac: {}", this.raceData.phase, this.raceData.phase.isPractice)
    
    val lapsString = if (this.raceData.phase.isPractice == true) "INFINITE"
        else "${this.raceData.numLaps.toInt()}".padStart(2).padEnd(8)
    val numberFormat = FixedFormat(Component.literal(lapsString)
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
        
        val position = (playerData.totalNodesPassed * POSITION_STEP - playerData.nodePosition).toInt()
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
fun ServerLevel.removeScoreboardFor(playerId: UUID) {
    this.levelScoreboard.removePlayerFromTracking(playerId, SIDEBAR_TIME_ID)
}

// Refresh boss bar which indicates lap progress
fun ServerLevel.updateProgressBarDisplay(playerId: UUID) {
    val player = this.server.playerList.getPlayer(playerId) ?: return    
    val numNodes = this.mapData.getMapData(this.raceData.activeMapKey)?.nodeCount ?: return
    this.levelBossBars.setBarProgress(playerId, this.playerData.getOrInitPlayerData(player).nodesThisLapPassed.toFloat() / numNodes.toFloat())
}