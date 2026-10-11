package mcsoc.iceboat.utils.gameeffects

import mcsoc.iceboat.IceboatPlugin
import mcsoc.iceboat.datatrackers.mapData
import mcsoc.iceboat.datatrackers.playerData
import mcsoc.iceboat.datatrackers.playerdata.PlayerData
import mcsoc.iceboat.datatrackers.raceData
import mcsoc.iceboat.utils.format
import net.minecraft.network.chat.Component
import net.minecraft.network.chat.FontDescription
import net.minecraft.network.chat.MutableComponent
import net.minecraft.network.chat.Style
import net.minecraft.network.chat.TextColor
import net.minecraft.network.protocol.game.ClientboundSetActionBarTextPacket
import net.minecraft.resources.Identifier
import net.minecraft.server.level.ServerLevel
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.entity.EntityType
import net.minecraft.world.entity.EntityTypes
import net.minecraft.world.entity.vehicle.boat.AbstractChestBoat
import net.minecraft.world.entity.vehicle.boat.ChestBoat
import net.minecraft.world.entity.vehicle.boat.ChestRaft
import net.minecraft.world.item.Items
import net.minecraft.world.level.GameType
import net.minecraft.world.phys.Vec3
import java.math.RoundingMode
import java.util.Queue
import java.util.UUID


// This isn't level-tied, but I don't forsee any actual problems.
private val oldPosMap: MutableMap<UUID, ArrayDeque<Vec3>> = mutableMapOf()

// Bamboo is a "ChestRaft" not ChestBoat :sob:
private val chestBoatTypes: Set<EntityType<out ChestBoat>> = setOf(
    EntityTypes.ACACIA_CHEST_BOAT,
    EntityTypes.BIRCH_CHEST_BOAT,
    EntityTypes.CHERRY_CHEST_BOAT,
    EntityTypes.JUNGLE_CHEST_BOAT,
    EntityTypes.MANGROVE_CHEST_BOAT,
    EntityTypes.OAK_CHEST_BOAT,
    EntityTypes.POPLAR_CHEST_BOAT,
    EntityTypes.SPRUCE_CHEST_BOAT,
    EntityTypes.DARK_OAK_CHEST_BOAT,
    EntityTypes.PALE_OAK_CHEST_BOAT
)
private fun spawnRandomChestBoat(level: ServerLevel, prespawn: (ChestBoat) -> Unit = {}): ChestBoat {
    return ChestBoat(chestBoatTypes.random(), level){Items.AIR}.also(prespawn)
        .also(level::addFreshEntityWithPassengers)
}

private fun handlePlayerFinish(player: ServerPlayer) {
    player.controlledVehicle?.kill(player.level())
    player.setGameMode(GameType.SPECTATOR)
}


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
        
        oldPosMap[playerId]?.let{
            if (nextNode.passedBy(it.last(), player.position())) {
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
            
            val velocity = it.last().subtract(player.position()).length().toBigDecimal().setScale(3, RoundingMode.FLOOR)
            player.connection.send(ClientboundSetActionBarTextPacket(
                Component.literal("Velocity: ").append(
                    Component.literal("$velocity".padEnd(5)).withStyle(Style.EMPTY
                    // monospaced font
                    .withFont(FontDescription.Resource(Identifier.withDefaultNamespace("uniform")))
                    .withColor(TextColor.GRAY)
                    .withBold(true)
                    .withUnderlined(true)
                    )
                )
            ))
        } ?: run { oldPosMap[playerId] = ArrayDeque(3) }
        oldPosMap[playerId]?.let{
            it.add(player.position())
            if (it.size > 3)  {
                it.removeFirstOrNull()
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
                handlePlayerFinish(player)
            } else {
                player.sendSystemMessage(Component.literal("${finishedPlayerData.playerName} finished in position $position!"))
            }
        }
    }
}

fun ServerLevel.startRound(mapId: String): Int {
    val mapData = this.mapData.getMapData(mapId) ?: return 1
    val firstNodeData = level.mapData.getNode(mapData.firstNodeId) ?: return 1
        
    if (this.raceData.activeMapKey.isEmpty()) {
        this.endRound()
    }
    
    val playerData = this.playerData

    // if not enough start positions
    if (playerData.activePlayers.size > mapData.startPositions.size) return 2
    this.raceData.setActiveMap(mapId)
    
    val startPositions = mapData.startPositions.iterator()
    for (player in playerData.activePlayers) {
        val startPos = startPositions.next()
        val playerData = playerData.getOrInitPlayerData(player).also {
            it.resetData()
            it.currNode = mapData.firstNodeId
        }
    
        val player = this.server.playerList.getPlayer(player) ?: continue
        player.setGameMode(GameType.ADVENTURE)
        player.teleportTo(this, startPos.x, startPos.y, startPos.z, emptySet(), firstNodeData.yawToNext, 0F, false)
        spawnRandomChestBoat(this).also{
            it.teleportTo(this, player.x, player.y, player.z, emptySet(), firstNodeData.yawToNext, 0F, false)
            player.startRiding(it, true, true)
        }
    }
    updateTimeDisplay(this)
    
    return 0
}

fun ServerLevel.endRound() {
    // clear player data
    this.playerData.activePlayerData.forEach(PlayerData::resetData)
    this.playerData.activePlayers.mapNotNull(this.server.playerList::getPlayer).forEach(::handlePlayerFinish)
    this.raceData.setInactive()
    updateTimeDisplay(this)
}