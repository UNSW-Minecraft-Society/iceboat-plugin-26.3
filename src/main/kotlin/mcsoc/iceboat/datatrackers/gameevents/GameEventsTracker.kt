package mcsoc.iceboat.datatrackers.gameevents

import com.mojang.serialization.MapCodec
import com.mojang.serialization.codecs.RecordCodecBuilder
import mcsoc.iceboat.datatrackers.LevelDataType
import mcsoc.iceboat.datatrackers.LevelTiedData
import net.minecraft.server.level.ServerLevel
import java.util.PriorityQueue


class GameEventsTracker() : LevelTiedData(), GameEventsHolder {
    companion object {
        val CODEC: MapCodec<GameEventsTracker> = RecordCodecBuilder.mapCodec{it.group(
            GameEvent.CODEC.listOf().fieldOf("queuedEvents").forGetter(GameEventsTracker::listQueuedEvents)
        ).apply(it, ::GameEventsTracker)}
    }
    override fun getType() = LevelDataType.EventsQueue
    
    override lateinit var level: ServerLevel
    
    override val eventQueue = PriorityQueue<GameEvent>()
    private fun listQueuedEvents(): List<GameEvent> = eventQueue.toList()
    
    private constructor(eventList: List<GameEvent>) : this() {
        queueEvents(eventList)
    }
}