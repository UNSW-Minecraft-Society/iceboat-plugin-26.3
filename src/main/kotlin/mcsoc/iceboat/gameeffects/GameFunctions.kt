package mcsoc.iceboat.gameeffects

import mcsoc.iceboat.datatrackers.eventsQueue
import mcsoc.iceboat.datatrackers.gameClock
import mcsoc.iceboat.datatrackers.levelBossBars
import mcsoc.iceboat.datatrackers.mapData
import mcsoc.iceboat.datatrackers.mapdata.NodeData
import mcsoc.iceboat.datatrackers.playerData
import mcsoc.iceboat.datatrackers.raceData
import mcsoc.iceboat.utils.formatMinuteSecondMillis
import net.minecraft.ChatFormatting
import net.minecraft.core.particles.ParticleTypes
import net.minecraft.network.chat.Component
import net.minecraft.network.chat.FontDescription
import net.minecraft.network.chat.Style
import net.minecraft.network.chat.TextColor
import net.minecraft.network.protocol.game.ClientboundAddEntityPacket
import net.minecraft.network.protocol.game.ClientboundLevelParticlesPacket
import net.minecraft.network.protocol.game.ClientboundRemoveEntitiesPacket
import net.minecraft.network.protocol.game.ClientboundSetActionBarTextPacket
import net.minecraft.network.protocol.game.ClientboundSetEntityDataPacket
import net.minecraft.resources.Identifier
import net.minecraft.server.level.ServerLevel
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.entity.Display
import net.minecraft.world.entity.EntityType
import net.minecraft.world.entity.EntityTypes
import net.minecraft.world.entity.ai.attributes.Attributes
import net.minecraft.world.entity.animal.equine.Horse
import net.minecraft.world.entity.vehicle.boat.ChestBoat
import net.minecraft.world.item.Items
import net.minecraft.world.level.GameType
import net.minecraft.world.level.block.Blocks
import net.minecraft.world.phys.Vec3
import java.math.RoundingMode
import java.util.UUID
import kotlin.math.roundToInt
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds


// The finish position assigned to players who didn't finish on game end
private const val DNF_POSITION = 999u

// Number of ticks to average player velocities over. Higher => smoother, but also less responsive
private const val VELOCITY_AVERAGE_TICKS = 5

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
            level.raceData.vehicleToDisplayMap[it.uuid] = Pair(it.id + 1000, UUID.randomUUID())
        }
}


private fun spawnRandomTamedHorse(level: ServerLevel, prespawn: (Horse) -> Unit = {}): Horse {
    return Horse(EntityTypes.HORSE, level).also(prespawn)
        .also(level::addFreshEntityWithPassengers)
        .also {
            level.raceData.vehicleToDisplayMap[it.uuid] = Pair(it.id + 1000, UUID.randomUUID())
            it.isTamed = true
            it.equipItemIfPossible(level, Items.SADDLE.defaultInstance)
            it.getAttribute(Attributes.MOVEMENT_SPEED)?.baseValue = 0.35
            it.getAttribute(Attributes.JUMP_STRENGTH)?.baseValue = 0.1
            it.getAttribute(Attributes.MAX_HEALTH)?.baseValue = 10.0
        }
}


fun showNodeTo(level: ServerLevel, player: UUID, startPos: Vec3, between: Vec3, endsSplit: Boolean, lengthScaledRounded: Int) {
    val player = level.server.playerList.getPlayer(player) ?: return
    
    for (p in 0..lengthScaledRounded) {
        val pos = startPos.add(between.scale(p.toDouble() / lengthScaledRounded))
        player.connection.send(ClientboundLevelParticlesPacket(
            ParticleTypes.CRIT, true, true,
            pos.x, pos.y + 0.1, pos.z,
            0.1F, 0.1F, 0.1F,
            0.0F, 2
        ))
        if (endsSplit) player.connection.send(ClientboundLevelParticlesPacket(
            ParticleTypes.HAPPY_VILLAGER, true, true,
            pos.x, pos.y, pos.z,
            0.1F, 0.1F, 0.1F,
            0.0F, 2
        ))
    }
}

fun showPosTo(level: ServerLevel, player: UUID, position: Vec3) {
    val player = level.server.playerList.getPlayer(player) ?: return
    
    player.connection.send(ClientboundLevelParticlesPacket(
        ParticleTypes.CRIT, true, true,
        position.x, position.y + 0.1, position.z,
        0.1F, 0.1F, 0.1F,
        0.0F, 2
    ))
}

// Displays the position of the next checkpoint via targetted particle packets
fun ServerPlayer.showNodeBoundary(node: NodeData, duration: Duration? = null, scale: Double = 10.0) {
    val range = node.ends
    val between = range.second.subtract(range.first)
    val lengthScaledRounded = (between.length() * scale).roundToInt()
    if (duration == null) {
        showNodeTo(this.level(), this.uuid, range.first, between, node.endsSplit, lengthScaledRounded)
    } else {
        this.level().eventsQueue.queueShowNodePosition(duration, this.uuid, range.first, between, node.endsSplit, lengthScaledRounded)
    }
}

// Displays some position via targetted particle packets
fun ServerPlayer.showPosition(position: Vec3, duration: Duration? = null) {
    if (duration == null) {
        showPosTo(this.level(), this.uuid, position)
    } else {
        this.level().eventsQueue.queueShowPosition(duration, this.uuid, position)
    }
}


fun ServerPlayer.sendActionbarHUD(velocity: Double, position: Int, time: Duration) {
    val velocityString = velocity.toBigDecimal().setScale(3, RoundingMode.FLOOR).toString().substring(0 until 5)
    val posString = position.toString()
    val timeString = time.formatMinuteSecondMillis
    
    this.connection.send(ClientboundSetActionBarTextPacket(
        Component.empty()
            .append(Component.literal("Speed: ").append(
                Component.literal(velocityString.padEnd(5)).withStyle(Style.EMPTY
                    // monospaced font
                    .withFont(FontDescription.Resource(Identifier.withDefaultNamespace("uniform")))
                    .withColor(TextColor.AQUA)
                    .withBold(true)
                )
            ).append(" km/h"))
            .append(Component.literal(" | "))
            .append(Component.literal("#").append(
                Component.literal(posString.padEnd(2)).withStyle(Style.EMPTY
                    // monospaced font
                    .withFont(FontDescription.Resource(Identifier.withDefaultNamespace("uniform")))
                    .withBold(true)
                )
            ))
            .append(Component.literal(" | "))
            .append(Component.literal("Time: ").append(
                Component.literal(timeString).withStyle(Style.EMPTY
                    // monospaced font
                    .withFont(FontDescription.Resource(Identifier.withDefaultNamespace("uniform")))
                    .withColor(TextColor.GREEN)
                    .withBold(true)
                )
            ))
            .withStyle(ChatFormatting.UNDERLINE)
    ))
}


// Broadcast "true" boat position via copper rod block display
fun ServerLevel.broadcastPositionVisually(playerId: UUID) {
    val vehicleId = this.raceData.playerToBoatMap[playerId] ?: return
    
    val displayIds = this.raceData.vehicleToDisplayMap[vehicleId] ?: return
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
            updateProgressBarDisplay(playerId)
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
    
    // skip if player is offline
    val player = level.server.playerList.getPlayer(playerId) ?: return
    
    // this shouldn't fail if currNode exists
    val next = level.mapData.getNode(curr)?.next ?: throw StringIndexOutOfBoundsException("tick")
    val nextNode = level.mapData.getNode(next) ?: throw StringIndexOutOfBoundsException("tick")
    
    oldPosMap[playerId]?.let{
        val raceTime = this.raceData.toRaceTime(this.gameClock.time)
        if (nextNode.passedBy(it.last(), player.position())) {
            player.trackTimeAtNode(next, raceTime)
            if (playerData.lapsCompleted >= level.raceData.numLaps) {
                // trigger win
                finishedPlayers.add(playerId)
                playerData.currNode = null
            } else {
                playerData.currNode = next
            }
        } else {
            // beacon beam at next checkpoint?
            player.showNodeBoundary(nextNode)
        }
        
        // find average velocity over last N ticks
        val iter = it.iterator()
        var curr: Vec3 = iter.next()
        val diffs: MutableCollection<Vec3> = mutableListOf()
        while (iter.hasNext()) {
            curr = iter.next().also{
                diffs.add(it.subtract(curr))
            }
        }
        //  *72 to convert from m/t to km/h
        val velocity: Double = diffs.sumOf(Vec3::length) * 72.0 / it.size
        val position: Int = this.raceData.playerPositions.indexOf(playerId) + 1
        val time = playerData.toSplitTime(raceTime)
        player.sendActionbarHUD(velocity, position, time)
        
    } ?: run { oldPosMap[playerId] = ArrayDeque(VELOCITY_AVERAGE_TICKS) }
    
    oldPosMap[playerId]?.let{
        if (it.size > VELOCITY_AVERAGE_TICKS) it.removeFirstOrNull()
        else if (it.isEmpty()) it.add(player.position())
        
        it.add(player.position())
    }        
}

/* ArgumentsLeave
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
                target.onLeaveRace()
            } else {
                target.sendSystemMessage(Component.literal("${finishedPlayerData.playerName} finished in position $position!"))
            }
        }
    }
}


// for setting up player
private fun ServerLevel.setupPlayerForRace(player: ServerPlayer, numLaps: UInt, numSplits: UInt, startNodeData: NodeData, startPos: Vec3) {
    val playerData = this.playerData.getOrInitPlayerData(player)
    // clear player data
    playerData.resetData()
    playerData.initGameData(numLaps, numSplits)

    // spawn and setup vehicle
    // forceload vehicle chunk since players will be in spectator
    val isSillyMode = this.raceData.isSillyMode
    (if (isSillyMode) spawnRandomTamedHorse(this) else spawnRandomChestBoat(this)).also{
        it.teleportTo(this, startPos.x, startPos.y, startPos.z, emptySet(), startNodeData.yawToNext, 30F, true)
        this.setChunkForced(startPos.x.toInt() shr 4, startPos.z.toInt() shr 4, true)
        this.raceData.playerToBoatMap[player.uuid] = it.uuid
    }
}

// for start round
fun ServerPlayer.onStartRound(level: ServerLevel, numLaps: UInt, numSplits: UInt, startNodeData: NodeData, startPos: Vec3) {
    level.setupPlayerForRace(this, numLaps, numSplits, startNodeData, startPos)

    // tp player above vehicle and set gamemode to spectator
    this.setGameMode(GameType.SPECTATOR)
    this.teleportTo(level, startPos.x, startPos.y, startPos.z, emptySet(), startNodeData.yawToNext, 30F, true)
}

// for after countdown
fun ServerPlayer.onBeginRace(level: ServerLevel, startNodeId: UUID) {
    // set player node
    level.playerData.getOrInitPlayerData(this).currNode = startNodeId
    
    // add to player positions
    level.raceData.playerPositions.addFirst(this.uuid)
    
    // set to adventure mode
    // mount player into vehicle
    val vehicle = level.getEntity(level.raceData.playerToBoatMap[this.uuid]!!)!!
    this.setGameMode(GameType.ADVENTURE)
    this.startRiding(vehicle, true, true)
    
    // unforceload chunk
    val chunkPos = vehicle.chunkPosition()
    level.setChunkForced(chunkPos.x, chunkPos.z, false)
    
    // add to scoreboard tracking
    // add to bossbar tracking
    level.initScoreboardFor(this.uuid)
    level.initProgressBarFor(this.uuid)
}

fun ServerLevel.addPlayerToRace(player: ServerPlayer) {
    level.playerData.activePlayers.add(player.uuid)
    
    if (this.raceData.phase.isRunning) {
        // if these values are null, something terrible has happened!
        val startNodeId = this.mapData.getMapData(this.raceData.activeMapKey)!!.firstNodeId
        player.onJoinRunningRace(this, startNodeId, this.mapData.getNode(startNodeId)!!)
    }
}
// for joining mid race (lobby)
fun ServerPlayer.onJoinRunningRace(level: ServerLevel, startNodeId: UUID, startNodeData: NodeData) {    
    val numLaps = level.raceData.numLaps
    val numSplits = level.mapData.getAllMapNodes(level.raceData.activeMapKey).count{it.second.endsSplit}.toUInt()
    level.setupPlayerForRace(this, numLaps, numSplits, startNodeData, startNodeData.pos)
    this.onBeginRace(level, startNodeId)
}


fun ServerPlayer.onLeaveRace() {
    val level = this.level()
    
    this.controlledVehicle?.discard()
    // "kill" any leftover indicators
    level.broadcastPositionVisually(this.uuid)
    level.levelBossBars.removePlayerFrom(this.uuid, this)
    level.removeScoreboardFor(this.uuid)
    
    this.setGameMode(GameType.SPECTATOR)
}


/* Codes:
 *  0 - Success
 *  1 - Map key does not exist
 *  2 - First map node does not exist
 *  3 - Not enough start positions
 */
fun ServerLevel.startRound(mapId: String, numLaps: UInt = 3u, practice: Boolean = false): Int {
    val mapData = this.mapData.getMapData(mapId) ?: return 1
    val firstNodeData = level.mapData.getNode(mapData.firstNodeId) ?: return 2
        
    if (this.raceData.phase.isRunning) {
        this.endRound(true)
    }
    
    // if not enough start positions
    if (playerData.activePlayers.size > mapData.startPositions.size) return 3
    this.raceData.startRoundWith(mapId, numLaps)
    
    val numSplits = this.mapData.getAllMapNodes(mapId).count{it.second.endsSplit}.toUInt()
    val startPositions = mapData.startPositions.iterator()
    for (player in playerData.activePlayers.shuffled().mapNotNull(this.server.playerList::getPlayer)) {
        player.onStartRound(this, numLaps, numSplits, firstNodeData, startPositions.next())
    }
    
    // if lobby or practice, start immediately, otherwise trigger the countdown
    if (practice) this.beginRace(true)
        else this.eventsQueue.queueGameStartCounter(5.seconds)
    
    return 0
}


fun ServerLevel.beginRace(practice: Boolean = false) {
    val mapData = this.mapData.getMapData(this.raceData.activeMapKey) ?: return
    
    if (practice) this.raceData.beginPracticeRace(this.gameClock.time)
        else this.raceData.beginRace(this.gameClock.time)
        
    this.updateTimeDisplay()
    this.addLapsIndicatorToSidebar()
    
    for (player in playerData.activePlayers.shuffled().mapNotNull(this.server.playerList::getPlayer)) {
        player.onBeginRace(this, mapData.firstNodeId)
    }
}

fun ServerLevel.endRound(keepPlayers: Boolean = false) {
    oldPosMap.clear()
    val finalPositions: MutableList<Pair<UInt, String>> = mutableListOf()
    for (playerId in this.raceData.playerPositions) {
        val playerData = this.playerData.getOrInitPlayerData(playerId)
        finalPositions.add(
            if (playerData.lapsCompleted >= this.raceData.numLaps) 
                Pair(playerData.nodePosition + 1u, playerData.playerName)
                else Pair(DNF_POSITION, playerData.playerName)
        )
        
        // clear player data
        playerData.resetData()
    }
    finalPositions.sortWith{p1, p2 -> p1.first.compareTo(p2.first)}

    for (player in this.playerData.activePlayers.mapNotNull(this.server.playerList::getPlayer)) {
        player.onLeaveRace()
        if (this.raceData.phase.isPractice == false) {
            for ((position, name) in finalPositions) {
                val posString = if (position != DNF_POSITION) "$position".padStart(3) else "DNF"
                player.sendSystemMessage(Component.literal("$posString: $name"))
            }
        }
    }

    this.raceData.setInactive()
    this.raceData.playerPositions.clear()
    this.updateTimeDisplay()
    if (!keepPlayers) this.playerData.activePlayers.clear()
}