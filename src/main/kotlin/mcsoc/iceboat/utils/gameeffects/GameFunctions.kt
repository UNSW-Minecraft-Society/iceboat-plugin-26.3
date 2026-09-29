package mcsoc.iceboat.utils.gameeffects

import mcsoc.iceboat.datatrackers.mapData
import mcsoc.iceboat.datatrackers.playerData
import mcsoc.iceboat.datatrackers.playerdata.PlayerData
import net.minecraft.server.level.ServerLevel
import java.util.UUID


fun ServerLevel.tickPlayerNodeTracking() {
    for (player in level.playerData.activePlayers) {
        
        // skip if player doesn't exist :skull:
        val playerData = level.playerData.player_data[player] ?: continue
        // skip if player isn't part of the race
        val curr = playerData.currNode ?: continue
        
        // tick player time even if offline
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
    for (player in level.playerData.activePlayers) {
        val playerData = level.playerData.getOrInitPlayerData(player)
        playerData.resetData()
        playerData.currNode = firstNode
        
        val pos = level.mapData.getNode(firstNode)?.pos ?: continue
        val player = level.server.playerList.getPlayer(player) ?: continue
        player.teleportTo(level, pos.x, pos.y, pos.z, emptySet(), 0F, 0F, false)
    }
}

fun ServerLevel.endRound() {
    level.playerData.activePlayerData.forEach(PlayerData::resetData)
}