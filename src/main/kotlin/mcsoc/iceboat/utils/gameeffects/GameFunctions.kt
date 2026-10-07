package mcsoc.iceboat.utils.gameeffects

import mcsoc.iceboat.datatrackers.mapData
import mcsoc.iceboat.datatrackers.playerData
import mcsoc.iceboat.datatrackers.playerdata.PlayerData
import mcsoc.iceboat.datatrackers.raceData
import net.minecraft.network.chat.Component
import net.minecraft.server.level.ServerLevel
import net.minecraft.server.level.ServerPlayer
import java.util.UUID


fun ServerLevel.tickPlayerNodeTracking() {
    val finishedPlayers: MutableCollection<UUID> = mutableListOf()
    for (playerId in level.playerData.activePlayers) {
        
        // skip if player doesn't exist :skull:
        val playerData = level.playerData.player_data[playerId] ?: continue
        // skip if player isn't part of the race or has finished
        val curr = playerData.currNode ?: continue
        
        // tick player time (even if offline)
        playerData.timer.tick()
        
        // skip if player is offline
        val player = level.server.playerList.getPlayer(playerId) ?: continue
        
        // this shouldn't fail if currNode exists
        val next = level.mapData.getNode(curr)?.next ?: throw StringIndexOutOfBoundsException("tick")
        val nextNode = level.mapData.getNode(next) ?: throw StringIndexOutOfBoundsException("tick")
        
        if (nextNode.inRangeOf(player)) {
            player.trackTimeAtNode(playerData.lapsCompleted, next, playerData.timer.time)
            if (playerData.lapsCompleted >= level.raceData.numLaps) {
                // trigger win
                finishedPlayers.add(playerId)
                playerData.currNode = null
            } else {
                playerData.timer.reset()
                playerData.currNode = next
            }
            
        }
    }
    for (playerId in level.playerData.activePlayers) {
        val player = level.server.playerList.getPlayer(playerId) ?: continue
        for (finishedPlayer in finishedPlayers) {
            val finishedPlayerData = level.playerData.player_data[finishedPlayer] ?: continue
            val position = finishedPlayerData.nodePosition + 1u
            if (playerId == finishedPlayer) {
                player.sendSystemMessage(Component.literal("You finished in position $position!"))
            } else {
                player.sendSystemMessage(Component.literal("${finishedPlayerData.playerName} finished in position $position!"))
            }
        }
    }
}

fun ServerLevel.startRound(mapId: String): Int {
    val firstNode = this.mapData.getFirstMapNode(mapId) ?: return 1
    val firstNodeData = firstNode.let(level.mapData::getNode) ?: return 1
    
    if (this.raceData.activeMapKey != null) this.endRound()
    this.raceData.setActiveMap(mapId)
    
    val pos = firstNodeData.pos
    for (player in this.playerData.activePlayers) {
        val playerData = this.playerData.getOrInitPlayerData(player).also {
            it.resetData()
            it.currNode = firstNode
        }
    
        val player = this.server.playerList.getPlayer(player) ?: continue
        player.teleportTo(this, pos.x, pos.y, pos.z, emptySet(), firstNodeData.yawToNext, 0F, false)
    }

    return 0
}

fun ServerLevel.endRound() {
    // clear player data
    this.playerData.activePlayerData.forEach(PlayerData::resetData)
    this.raceData.setInactive()
}