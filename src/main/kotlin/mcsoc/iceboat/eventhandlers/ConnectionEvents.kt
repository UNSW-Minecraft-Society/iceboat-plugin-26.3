package mcsoc.iceboat.eventhandlers

import mcsoc.iceboat.IceboatPlugin
import mcsoc.iceboat.datatrackers.raceData
import mcsoc.iceboat.gameeffects.sendScoreboardToPlayer
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents


fun registerConnectionEvents() {
    sendScoreboardOnJoin()
}

fun sendScoreboardOnJoin() {
    ServerPlayConnectionEvents.JOIN.register{listener, sender, server ->
        val level = listener.player.level()
        if (level.raceData.phase.isActive) level.sendScoreboardToPlayer(listener.player)
    }
}