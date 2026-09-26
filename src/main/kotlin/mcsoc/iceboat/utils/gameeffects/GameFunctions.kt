package mcsoc.iceboat.utils.gameeffects

import mcsoc.iceboat.datatrackers.gameClock
import mcsoc.iceboat.datatrackers.mapData
import mcsoc.iceboat.datatrackers.playerData
import mcsoc.iceboat.datatrackers.playerdata.PlayerData
import net.minecraft.server.level.ServerLevel
import java.util.UUID


fun ServerLevel.tickPlayerNodeTracking() {
    for (player in level.playerData.getPlayers()) {
        
        val playerData = level.playerData.getPlayerData(player)
        // skip if player isn't part of a race
        val curr = playerData.currNode ?: continue
        playerData.timer.tick()
        
        // skip if player is offline
        val player = level.server.playerList.getPlayer(player) ?: continue
        
        // this shouldn't fail if currNode exists
        val next = level.mapData.getNode(curr)?.next ?: throw StringIndexOutOfBoundsException("tick")
        val nextNode = level.mapData.getNode(next) ?: throw StringIndexOutOfBoundsException("tick")
        
        if (nextNode.inRangeOf(player)) {
            player.trackTimeAtNode(next, playerData.timer.time)
            playerData.timer.reset()
            playerData.currNode = next
        }
    }
}

fun ServerLevel.startRound(firstNode: UUID) {
    for (player in level.playerData.getPlayers()) {
        val playerData = level.playerData.getPlayerData(player)        
        playerData.resetData()
        playerData.currNode = firstNode
        
        val pos = level.mapData.getNode(firstNode)?.pos ?: continue
        val player = level.server.playerList.getPlayer(player) ?: continue
        player.teleportTo(level, pos.x, pos.y, pos.z, emptySet(), 0F, 0F, false)
    }
}

fun ServerLevel.endRound() {
    level.playerData.getPlayerData().forEach(PlayerData::resetData)
}