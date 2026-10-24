package mcsoc.iceboat.gameeffects

import mcsoc.iceboat.datatrackers.levelScoreboard
import mcsoc.iceboat.datatrackers.mapData
import mcsoc.iceboat.datatrackers.playerData
import mcsoc.iceboat.datatrackers.raceData
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
// the name of the scoreboard (this does not matter, and can be changed whenever)
private const val SIDEBAR_TIME_ID = "positions"

/* Arguments
 *  level - The world 
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
    
    this.updateTimeDisplay()
}
fun ServerPlayer.trackTimeAtNode(lap: UInt, nodeId: UUID, splitTime: Duration) 
    = this.level().trackPlayerTimeAtNode(lap, this.uuid, nodeId, splitTime)

    
fun ServerLevel.sendScoreboardToPlayer(player: ServerPlayer) {
    this.levelScoreboard.sendScoreboard(SIDEBAR_TIME_ID, DisplaySlot.SIDEBAR, player)
    this.levelScoreboard.sendAllScoreboardValues(SIDEBAR_TIME_ID, player)
}
fun ServerLevel.sendScoreboardToAll() {
    this.levelScoreboard.sendScoreboardToTracking(SIDEBAR_TIME_ID, DisplaySlot.SIDEBAR)
    this.levelScoreboard.sendAllScoreboardValuesToTracking(SIDEBAR_TIME_ID)
}

// 
//  NB this is **currently** server-tied rather than level-tied
fun ServerLevel.updateTimeDisplay() {
    // TODO switch from using a "real" scoreboard to using packets
    val scoreboard = this.levelScoreboard
    scoreboard.tryAddObjective(SIDEBAR_TIME_ID, Component.literal("Positions"), BlankFormat.INSTANCE)
    
    if (this.raceData.activeMapKey.isEmpty()) {
        scoreboard.clearScores(SIDEBAR_TIME_ID)
        return
    }
    
    for (playerId in this.playerData.activePlayers) {
        val player: ServerPlayer = this.server.playerList.getPlayer(playerId) ?: continue
        val playerData = this.playerData.getOrInitPlayerData(playerId)
        
        val position = (playerData.nodesPassed * POSITION_STEP - playerData.nodePosition).toInt()
        val positionString = "${playerData.lapsCompleted}".padStart(2) + " | " + "${playerData.nodesThisLapPassed}".padEnd(3)
        val numberFormat = FixedFormat(Component.literal(positionString))
        
        scoreboard.setPlayerScore(SIDEBAR_TIME_ID, player, position, numberFormat)
    }
}