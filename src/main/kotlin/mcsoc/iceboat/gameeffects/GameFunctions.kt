package mcsoc.iceboat.gameeffects

import mcsoc.iceboat.datatrackers.eventsQueue
import mcsoc.iceboat.datatrackers.mapData
import mcsoc.iceboat.datatrackers.mapdata.NodeData
import mcsoc.iceboat.datatrackers.playerData
import mcsoc.iceboat.datatrackers.raceData
import net.minecraft.core.particles.ParticleTypes
import net.minecraft.network.chat.Component
import net.minecraft.network.chat.FontDescription
import net.minecraft.network.chat.Style
import net.minecraft.network.chat.TextColor
import net.minecraft.network.protocol.game.ClientboundAddEntityPacket
import net.minecraft.network.protocol.game.ClientboundEntityEventPacket
import net.minecraft.network.protocol.game.ClientboundLevelParticlesPacket
import net.minecraft.network.protocol.game.ClientboundRemoveEntitiesPacket
import net.minecraft.network.protocol.game.ClientboundSetActionBarTextPacket
import net.minecraft.network.protocol.game.ClientboundSetEntityDataPacket
import net.minecraft.resources.Identifier
import net.minecraft.server.level.ServerLevel
import net.minecraft.server.level.ServerPlayer
import net.minecraft.util.Mth
import net.minecraft.world.entity.Display
import net.minecraft.world.entity.Entity
import net.minecraft.world.entity.EntityEvent
import net.minecraft.world.entity.EntityType
import net.minecraft.world.entity.EntityTypes
import net.minecraft.world.entity.vehicle.boat.ChestBoat
import net.minecraft.world.item.Items
import net.minecraft.world.level.GameType
import net.minecraft.world.level.block.Blocks
import net.minecraft.world.phys.Vec3
import java.math.RoundingMode
import java.util.UUID
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds


// The finish position assigned to players who didn't finish on game end
private const val DNF_POSITION = 999u

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
        .also {
            level.raceData.boatToDisplayMap[it.uuid] = Pair(it.id + 1000, UUID.randomUUID())
        }
}

private fun ServerPlayer.onRaceFinished() {
    val level = this.level()
    
    this.controlledVehicle?.kill(level)
    // "kill" any leftover indicators
    level.broadcastPositionVisually(this.uuid)
    
    this.setGameMode(GameType.SPECTATOR)
}


// Displays the position of the next checkpoint via targetted particle packets
fun ServerPlayer.displayNextCheckpoint(nextNode: NodeData) {
    val range = nextNode.ends
    for (p in 0..100 step 5) {
        val pos = Mth.lerp(p.toDouble() / 100.0, range.first, range.second)
        this.connection.send(ClientboundLevelParticlesPacket(
            ParticleTypes.CRIT, true, true,
            pos.x, pos.y + 0.1, pos.z,
            0.1F, 0.1F, 0.1F,
            0.0F, 2
        ))
        if (nextNode.endsSplit) this.connection.send(ClientboundLevelParticlesPacket(
            ParticleTypes.HAPPY_VILLAGER, true, true,
            pos.x, pos.y, pos.z,
            0.1F, 0.1F, 0.1F,
            0.0F, 2
        ))
    }
}

fun ServerPlayer.sendActionbarHUD(velocity: Double, time: Duration) {
    val velocityString = velocity.toBigDecimal().setScale(3, RoundingMode.FLOOR).toString().substring(0 until 5)
    val timeString = time.toComponents{ minutes, seconds, nanos -> 
        "$minutes".padStart(2, '0') + ":" + "$seconds".padStart(2, '0') + ":" + "$nanos".padStart(2, '0').substring(0 until 2)
    }
    
    this.connection.send(ClientboundSetActionBarTextPacket(
        Component.empty()
            .append(Component.literal("Speed: ").append(
                Component.literal(velocityString.padEnd(5)).withStyle(Style.EMPTY
                // monospaced font
                .withFont(FontDescription.Resource(Identifier.withDefaultNamespace("uniform")))
                .withColor(TextColor.AQUA)
                .withBold(true)
                .withUnderlined(true)
                )
            ).append(" km/h")
            )
            .append(Component.literal(" | "))
            .append(Component.literal("Time: ").append(
                Component.literal(timeString).withStyle(Style.EMPTY
                // monospaced font
                .withFont(FontDescription.Resource(Identifier.withDefaultNamespace("uniform")))
                .withColor(TextColor.GREEN)
                .withBold(true)
                .withUnderlined(true)
                )
            ))
    ))
}


// Broadcast "true" boat position via copper rod block display
fun ServerLevel.broadcastPositionVisually(playerId: UUID) {
    val vehicleId = this.raceData.playerToBoatMap[playerId] ?: return
    
    val displayIds = this.raceData.boatToDisplayMap[vehicleId] ?: return
    val display = Display.BlockDisplay(EntityTypes.BLOCK_DISPLAY, this).also{
        it.blockState = Blocks.LIGHTNING_ROD.waxed.unaffected.defaultBlockState()
    }
    
    this.getEntity(vehicleId)?.let{
        for (player in this.server.playerList.players) {
            // if (player.uuid == playerId) continue
            player.connection.send(
                ClientboundAddEntityPacket(
                    displayIds.first, displayIds.second, 
                    it.x, it.y, it.z, 0F, 0F,
                    EntityTypes.BLOCK_DISPLAY, 0,
                    it.knownMovement, 0.0
                )
            )
            player.connection.send(
                ClientboundSetEntityDataPacket(
                    displayIds.first,
                    display.entityData.nonDefaultValues!!
                )
            )
        }
    } ?: run {
        for (player in this.server.playerList.players) {
            player.connection.send(
                ClientboundRemoveEntitiesPacket(
                    displayIds.first
                )
            )
        }
    }
}


fun ServerLevel.ensureBoatRiding(playerId: UUID) {
    val player = this.server.playerList.getPlayer(playerId) ?: return
    if (player.vehicle == null) {
        val vehicle = this.getEntity(this.raceData.playerToBoatMap[playerId] ?: return) ?: return
        player.startRiding(vehicle, true, true)
    }
    
}

fun ServerLevel.tickActivePlayers() {
    val finishedPlayers: MutableCollection<UUID> = mutableListOf()
    for (playerId in this.playerData.activePlayers) {
        if (this.raceData.phase.isActive) {
            ensureBoatRiding(playerId)
            broadcastPositionVisually(playerId)
            tickPlayerNodeTracking(playerId, finishedPlayers)
        }
    }
    broadcastFinishedPlayers(finishedPlayers)
}

/* Arguments
 *  playerId - PlayerData of an active player
 *  finishedPlayers - A list to append players to finished to
 */
fun ServerLevel.tickPlayerNodeTracking(playerId: UUID, finishedPlayers: MutableCollection<UUID>) {
    
    // skip if player doesn't exist :skull:
    val playerData = level.playerData.player_data[playerId] ?: return
    // skip if player isn't part of the race or has finished
    val curr = playerData.currNode ?: return
    
    // tick player time (even if offline)
    playerData.timer.tick()
    
    // skip if player is offline
    val player = level.server.playerList.getPlayer(playerId) ?: return
    
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
                playerData.currNode = next
                if (nextNode.endsSplit) playerData.timer.reset()
            }
        } else {
            // beacon beam at next checkpoint?
            player.displayNextCheckpoint(nextNode)
        }
        
        // TODO sum last 3 deltas instead of just last
        //  *72 to convert from m/t to km/h
        val velocity = it.last().subtract(player.position()).length() * 72.0          
        val time = playerData.timer.time
        player.sendActionbarHUD(velocity, time)
        
        
    } ?: run { oldPosMap[playerId] = ArrayDeque(3) }
    oldPosMap[playerId]?.let{
        it.add(player.position())
        if (it.size > 3)  {
            it.removeFirstOrNull()
        }
    }        
}

/* Arguments
 *  finishedPlayers - A list of players who finished
 */
fun ServerLevel.broadcastFinishedPlayers(finishedPlayers: MutableCollection<UUID>) {
    for (targetId in this.playerData.activePlayers) {
        val target = level.server.playerList.getPlayer(targetId) ?: return
        for (finishedPlayerId in finishedPlayers) {
            val finishedPlayerData = level.playerData.player_data[finishedPlayerId] ?: continue
            val position = finishedPlayerData.nodePosition + 1u
            if (targetId == finishedPlayerId) {
                target.sendSystemMessage(Component.literal("You finished in position $position!"))
                target.onRaceFinished()
            } else {
                target.sendSystemMessage(Component.literal("${finishedPlayerData.playerName} finished in position $position!"))
            }
        }
    }
}

/* Codes:
 *  0 - Success
 *  1 - Map key does not exist
 *  2 - First map node does not exist
 *  3 - Not enough start positions
 */
fun ServerLevel.startRound(mapId: String, numLaps: UInt = 3u): Int {
    val mapData = this.mapData.getMapData(mapId) ?: return 1
    val firstNodeData = level.mapData.getNode(mapData.firstNodeId) ?: return 2
        
    if (this.raceData.phase.isRunning) {
        this.endRound(true)
    }
    
    val playerData = this.playerData

    // if not enough start positions
    if (playerData.activePlayers.size > mapData.startPositions.size) return 3
    this.raceData.setActiveMap(mapId)
    
    this.raceData.numLaps = numLaps
    
    val startPositions = mapData.startPositions.iterator()
    for (player in playerData.activePlayers.shuffled()) {
        val startPos = startPositions.next()
        
        spawnRandomChestBoat(this).also{
            it.teleportTo(this, startPos.x, startPos.y, startPos.z, emptySet(), firstNodeData.yawToNext, 0F, false)
            this.setChunkForced(startPos.x.toInt() shr 4, startPos.z.toInt() shr 4, true)
            this.raceData.playerToBoatMap[player] = it.uuid
        }
        
        this.server.playerList.getPlayer(player)?.let{
            it.setGameMode(GameType.SPECTATOR)
            it.teleportTo(this, startPos.x, startPos.y, startPos.z, emptySet(), firstNodeData.yawToNext, 0F, false)
        }
    }
    

    // TODO make this trigger a countdown
    this.eventsQueue.queueGameStartCounter(5.seconds)
    
    return 0
}


fun ServerLevel.beginRace() {
    val mapData = this.mapData.getMapData(this.raceData.activeMapKey) ?: return
    val firstNodeData = level.mapData.getNode(mapData.firstNodeId) ?: return
    this.updateTimeDisplay()
    this.sendScoreboardToAll()
    
    for (player in playerData.activePlayers) {
        val playerData = playerData.getOrInitPlayerData(player).also {
            it.resetData()
            it.currNode = mapData.firstNodeId
        }
        
        val vehicle = this.getEntity(this.raceData.playerToBoatMap[player]!!)!!
        val chunkPos = vehicle.chunkPosition()
        this.setChunkForced(chunkPos.x, chunkPos.z, false)
        
        val player = this.server.playerList.getPlayer(player) ?: continue
        player.setGameMode(GameType.ADVENTURE)
        player.startRiding(vehicle, true, true)
    }
    this.raceData.startRace()
}

fun ServerLevel.endRound(keepPlayers: Boolean = false) {
    val finalPositions: MutableList<Pair<UInt, String>> = mutableListOf()
    for (playerData in this.playerData.activePlayerData) {
        if (playerData.lapsCompleted >= this.raceData.numLaps) {
            finalPositions.add(Pair(playerData.nodePosition + 1u, playerData.playerName))
        } else {
            finalPositions.add(Pair(DNF_POSITION, playerData.playerName))
        }
        
        // clear player data
        playerData.resetData()
    }
    finalPositions.sortWith{p1, p2 -> p1.first.compareTo(p2.first)}

    for (player in this.playerData.activePlayers.mapNotNull(this.server.playerList::getPlayer)) {
        player.onRaceFinished()
        for (position in finalPositions) {
            val posString = if (position.first != DNF_POSITION) "${position.first}".padStart(3) else "DNF"
            player.sendSystemMessage(Component.literal("$posString: ${position.second}"))
        }
    }

    this.raceData.setInactive()
    this.updateTimeDisplay()
    if (!keepPlayers) this.playerData.activePlayers.clear()
}