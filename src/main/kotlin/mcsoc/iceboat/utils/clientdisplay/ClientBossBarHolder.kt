package mcsoc.iceboat.utils.clientdisplay

import mcsoc.iceboat.IceboatPlugin
import net.minecraft.network.chat.Component
import net.minecraft.resources.Identifier
import net.minecraft.server.bossevents.CustomBossEvent
import net.minecraft.server.level.ServerBossEvent
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.BossEvent
import java.util.UUID


interface ClientBossBarExposer {
    val bossEventMap: Map<UUID, CustomBossEvent>
    
    // get Player from UUID.
    // Can be implemented w/ a saved ServerLevel instance.
    fun getServerPlayer(playerId: UUID): ServerPlayer?
    
    // Constructs and saves a new boss bar.
    // Returns barId if supplied, otherwise generates a barId and returns that
    fun addBossBar(type: Identifier, displayName: Component, barId: UUID? = null): UUID
    
    // Drops the bar specified by the given ID 
    fun removeBossBar(barId: UUID)
    
    
    // To be registered in ServerPlayerEvents.JOIN
    fun onPlayerJoin(player: ServerPlayer) {
        for (bar in bossEventMap.values) {
            bar.onPlayerConnect(player)
        }
    }
    
    // To be registered in ServerPlayerEvents.LEAVE
    fun onPlayerLeave(player: ServerPlayer) {
        for (bar in bossEventMap.values) {
            bar.onPlayerDisconnect(player)
        }
    }
    
    
    /* Sets and transmits the progress of the bar to tracking players
     * Arguments:
     * - barId: The ID of the bar, as returned from addBossBar
     * - progress: A float between 0 and 1
     */
    fun setBarProgress(barId: UUID, progress: Float) {
        val bar = tryGetBar(barId) ?: return
        bar.progress = progress
    }
    
    /* Sets and transmits the bar's style and colour
     * Arguments:
     * - barId: The ID of the bar, as returned from addBossBar
     * - progress: A float between 0 and 1
     */
    fun setBarStyle(barId: UUID, style: BossEvent.BossBarOverlay, colour: BossEvent.BossBarColor) {
        val bar = tryGetBar(barId) ?: return
        bar.overlay = style
        bar.color = colour
    }
    
    
    /* Adds player to be tracked by the bar. This should handle all packets.
     * Arguments:
     * - barId: The ID of the bar, as returned from addBossBar
     * - player: The player to track
     */
    fun addPlayerTo(barId: UUID, player: ServerPlayer) {
        val bar = tryGetBar(barId) ?: return
        bar.addPlayer(player)
    }
    // Helper function to locate player from player UUID 
    fun addPlayerTo(barId: UUID, playerId: UUID) {
        val player = getServerPlayer(playerId) ?: return
        addPlayerTo(barId, player)
    }
    
    /* Removes player from being tracked by the bar. This should handle all packets.
     * Arguments
     * - barId: The ID of the bar, as returned from addBossBar
     * - player: The player to untrack
     */
    fun removePlayerFrom(barId: UUID, player: ServerPlayer) {
        val bar = tryGetBar(barId) ?: return
        bar.removePlayer(player)
    }
    // Helper function to locate player from player UUID
    fun removePlayerFrom(barId: UUID, playerId: UUID) {
        val player = getServerPlayer(playerId) ?: return
        removePlayerFrom(barId, player)
    }
    
    
    private fun tryGetBar(barId: UUID, printError: Boolean = true): ServerBossEvent? = bossEventMap[barId] ?: run {
        if (printError) IceboatPlugin.LOGGER.error("tryGetBar: ", StringIndexOutOfBoundsException("$barId"))
        null
    }
}


interface ClientBossBarHolder : ClientBossBarExposer {
    override val bossEventMap: MutableMap<UUID, CustomBossEvent>
    
    override fun addBossBar(type: Identifier, displayName: Component, barId: UUID?): UUID {
        return (barId ?: UUID.randomUUID()).also {
            bossEventMap[it] = CustomBossEvent(it, type, displayName){}
        }
    }
    
    override fun removeBossBar(barId: UUID) {
        bossEventMap.remove(barId)
    }
}