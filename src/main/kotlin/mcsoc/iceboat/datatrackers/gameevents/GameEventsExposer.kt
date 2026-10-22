package mcsoc.iceboat.datatrackers.gameevents

import mcsoc.iceboat.datatrackers.gameClock
import net.minecraft.server.level.ServerLevel
import java.util.Queue
import kotlin.time.Duration


interface GameEventsExposer {
    fun queueEvent(event: GameEvent)
    fun queueEvents(events: Iterable<GameEvent>)
    fun reset()
    
    fun queueGameStartCounter(countdownTime: Duration)

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