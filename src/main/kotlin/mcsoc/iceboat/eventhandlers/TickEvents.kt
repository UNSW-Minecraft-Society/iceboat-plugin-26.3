package mcsoc.iceboat.eventhandlers

import mcsoc.iceboat.datatrackers.gameClock
import mcsoc.iceboat.utils.gameeffects.tickPlayerNodeTracking
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents


fun registerTickEvents() {
    registerGameClockTick()
    registerPlayerTrackerTick()
}

fun registerGameClockTick() {
    ServerTickEvents.START_LEVEL_TICK.register{ it.gameClock.tick() }
}

fun registerPlayerTrackerTick() {
    ServerTickEvents.END_LEVEL_TICK.register{level ->
        for (i in 0u until level.gameClock.timerTick) {
            level.tickPlayerNodeTracking()
        }
    }
}