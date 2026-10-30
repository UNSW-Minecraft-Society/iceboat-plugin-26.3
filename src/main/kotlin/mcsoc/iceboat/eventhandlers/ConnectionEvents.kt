package mcsoc.iceboat.eventhandlers

import mcsoc.iceboat.datatrackers.levelBossBars
import mcsoc.iceboat.datatrackers.levelScoreboard
import mcsoc.iceboat.datatrackers.raceData
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents


fun registerConnectionEvents() {
    sendDisplaysOnJoin()
    handlePlayerDisconnect()
}

fun sendDisplaysOnJoin() {
    ServerPlayConnectionEvents.JOIN.register{listener, sender, server ->
        val level = listener.player.level()
        if (level.raceData.phase.isActive) level.levelScoreboard
        level.levelBossBars.onPlayerJoin(listener.player)
        level.levelScoreboard.handlePlayerJoin(listener.player)
    }
}

fun handlePlayerDisconnect() {
    ServerPlayConnectionEvents.DISCONNECT.register{listener, server ->
        val level = listener.player.level()
        level.levelBossBars.onPlayerLeave(listener.player)
    }
}