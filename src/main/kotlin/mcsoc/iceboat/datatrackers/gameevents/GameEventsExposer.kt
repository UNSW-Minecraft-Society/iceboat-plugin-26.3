package mcsoc.iceboat.datatrackers.gameevents

import mcsoc.iceboat.datatrackers.gameClock
import net.minecraft.server.level.ServerLevel
import net.minecraft.world.phys.Vec3
import java.util.Queue
import java.util.UUID
import kotlin.time.Duration


interface GameEventsExposer {
    fun queueEvent(event: GameEvent)
    fun queueEvents(events: Iterable<GameEvent>)
    fun reset()
    
    fun queueGameStartCounter(countdownTime: Duration)
    fun queueShowNodePosition(displayTime: Duration, target: UUID, 
        startPos: Vec3, between: Vec3, 
        endsSplit: Boolean, lengthScaledRounded: Int
    )

    fun tick()
}

interface GameEventsHolder : GameEventsExposer {
    val level: ServerLevel
    val eventQueue: Queue<GameEvent>
    
    override fun queueEvent(event: GameEvent) {
        eventQueue.add(event)
    }
    override fun queueEvents(events: Iterable<GameEvent>) {
        eventQueue.addAll(events)
    }
    
    override fun queueGameStartCounter(countdownTime: Duration) {
        queueEvent(GameEvent.GameStartCounterEvent(level.gameClock.time, countdownTime))
    }
    
    override fun queueShowNodePosition(displayTime: Duration, target: UUID, 
        startPos: Vec3, between: Vec3, 
        endsSplit: Boolean, lengthScaledRounded: Int
    ) {
        queueEvent(GameEvent.DisplayNodePositionEvent(level.gameClock.time, displayTime, target, startPos, between, endsSplit, lengthScaledRounded))
    }
    
    private fun dequeueEventsToTrigger(): Iterable<GameEvent> {
        val eventsToTrigger: MutableCollection<GameEvent> = mutableSetOf()
        while (eventQueue.isNotEmpty() && eventQueue.peek().hasExpired(level.gameClock.time))
            eventsToTrigger.add(eventQueue.poll() ?: break)
        return eventsToTrigger
    }

    override fun reset() {
        this.eventQueue.clear()
    }
    
    override fun tick() {
        for (event in dequeueEventsToTrigger()) event.trigger(level)
    }
}