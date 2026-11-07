package mcsoc.iceboat.datatrackers.gameevents

import com.mojang.serialization.Codec
import com.mojang.serialization.MapCodec
import com.mojang.serialization.codecs.RecordCodecBuilder
import mcsoc.iceboat.datatrackers.eventsQueue
import mcsoc.iceboat.datatrackers.playerData
import mcsoc.iceboat.utils.CODEC
import mcsoc.iceboat.gameeffects.beginRace
import mcsoc.iceboat.gameeffects.showNodeTo
import mcsoc.iceboat.gameeffects.showPosTo
import mcsoc.iceboat.utils.ticks
import net.minecraft.core.Holder
import net.minecraft.core.UUIDUtil
import net.minecraft.network.chat.Component
import net.minecraft.network.protocol.game.ClientboundSetTitleTextPacket
import net.minecraft.network.protocol.game.ClientboundSoundPacket
import net.minecraft.server.level.ServerLevel
import net.minecraft.sounds.SoundEvents
import net.minecraft.sounds.SoundSource
import net.minecraft.world.phys.Vec3
import java.util.UUID
import kotlin.reflect.KClass
import kotlin.reflect.full.companionObjectInstance
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

/**
 * Interface for the companion object of a GameEvent to implement
 */
private interface GameEventCompanion<T : GameEvent> {
    val id: String
    val codec: MapCodec<T>
}

fun <T : GameEvent> KClass<T>.getAllSealedSubclasses(): List<KClass<out T>> {
    return this.sealedSubclasses.flatMap {
        if (it.isSealed) {
            it.getAllSealedSubclasses()
        } else {
            listOf(it)
        }
    }
}

/**
 * Class to represent game events; actions that can be scheduled to trigger after some time (once `triggerTime` has
 * passed). These are queued up to the `GameEventTracker`.
 */
sealed class GameEvent(protected var triggerTime: Duration, private val id: String): Comparable<GameEvent> {
    companion object {
        val REGISTRY: Map<String, MapCodec<out GameEvent>> by lazy {
            GameEvent::class.getAllSealedSubclasses()
                .mapNotNull { it.companionObjectInstance as? GameEventCompanion<*> }
                .associate { it.id to it.codec }
        }
        val CODEC: Codec<GameEvent> = Codec.STRING.dispatch(GameEvent::id, REGISTRY::get)
    }
    

    abstract fun trigger(level: ServerLevel)
    
    fun hasExpired(currTime: Duration): Boolean {
        return triggerTime <= currTime
    }

    override fun compareTo(other: GameEvent): Int {
        return triggerTime.compareTo(other.triggerTime)
    }

    /**
     * Class to represent repeating game events, actions that repeat every `interval`, `count` amount of times.
     */
    sealed class RecursiveGameEvent<T: GameEvent>(triggerTime: Duration, id: String, protected var count: Long, private val interval: Duration) : GameEvent(triggerTime, id) {
        protected abstract fun recurseTrigger(level: ServerLevel)
        protected abstract fun concludeTrigger(level: ServerLevel)

        // protected abstract fun createEvent(triggerTime: Duration, count: Long): RecursiveGameEvent<T>
        
        override fun trigger(level: ServerLevel) {
            if (count > 0) {
                recurseTrigger(level)
                // level.eventsQueue.queueEvent(createEvent(triggerTime + interval, count - 1))
                triggerTime += interval
                count -= 1
                level.eventsQueue.queueEvent(this)
            } else {
                concludeTrigger(level)
            }
        }
    }
    
    class GameStartCounterEvent private constructor(triggerTime: Duration, count: Long) :
            RecursiveGameEvent<GameStartCounterEvent>(triggerTime, id, count, INTERVAL) {
        constructor(triggerTime: Duration, startTime: Duration, _d: Unit = Unit) : this(
            triggerTime,
            (startTime / INTERVAL).toLong()
        )
        companion object : GameEventCompanion<GameStartCounterEvent> {
            val INTERVAL = 1.seconds
            
            override val id: String = "gameStart"
            override val codec: MapCodec<GameStartCounterEvent> = RecordCodecBuilder.mapCodec{it.group(
                Duration.CODEC.fieldOf("time").forGetter(GameStartCounterEvent::triggerTime),
                Codec.LONG.fieldOf("second").forGetter(GameStartCounterEvent::count)
            ).apply(it, ::GameStartCounterEvent)}
        }
        
        override fun recurseTrigger(level: ServerLevel) {
            level.playerData.activePlayers.mapNotNull(level.server.playerList::getPlayer).forEach{player ->
                player.connection.send(
                    ClientboundSetTitleTextPacket(
                        Component.literal(count.toString())
                    )
                )
                player.connection.send(
                    ClientboundSoundPacket(
                        Holder.direct(SoundEvents.NOTE_BLOCK_PLING.value()),
                        SoundSource.MASTER, player.x, player.y, player.z,
                        1.0F, 1.0F, level.getRandom().nextLong()
                    )
                )
            }
        }
        override fun concludeTrigger(level: ServerLevel) {
            level.playerData.activePlayers.mapNotNull(level.server.playerList::getPlayer).forEach{player ->
                player.connection.send(
                    ClientboundSetTitleTextPacket(
                        Component.literal("GO!")
                    )
                )
                player.connection.send(
                    ClientboundSoundPacket(
                        Holder.direct(SoundEvents.NOTE_BLOCK_PLING.value()),
                        SoundSource.MASTER, player.x, player.y, player.z,
                        1.0F, 1.0F, level.getRandom().nextLong()
                    )
                )
            }
            level.beginRace()
        }
        // override fun createEvent(triggerTime: Duration, count: Long) = GameStartCounterEvent(triggerTime, count)
    }

    class DisplayNodePositionEvent private constructor(triggerTime: Duration, count: Long,
        val target: UUID, val startPos: Vec3, val between: Vec3, val endsSplit: Boolean, val lengthScaledRounded: Int
    ) : RecursiveGameEvent<DisplayNodePositionEvent>(triggerTime, id, count, INTERVAL) {
    constructor(triggerTime: Duration, startTime: Duration, target: UUID, startPos: Vec3, between: Vec3, endsSplit: Boolean, lengthScaledRounded: Int,
    _d: Unit = Unit) : this(
            triggerTime, (startTime / INTERVAL).toLong(), target, startPos, between, endsSplit, lengthScaledRounded
        )
        companion object : GameEventCompanion<DisplayNodePositionEvent> {
            val INTERVAL = 5.ticks
            
            override val id: String = "gameStart"
            override val codec: MapCodec<DisplayNodePositionEvent> = RecordCodecBuilder.mapCodec{it.group(
                Duration.CODEC.fieldOf("time").forGetter(DisplayNodePositionEvent::triggerTime),
                Codec.LONG.fieldOf("period").forGetter(DisplayNodePositionEvent::count),
                UUIDUtil.CODEC.fieldOf("target").forGetter(DisplayNodePositionEvent::target),
                Vec3.CODEC.fieldOf("startPos").forGetter(DisplayNodePositionEvent::startPos),
                Vec3.CODEC.fieldOf("between").forGetter(DisplayNodePositionEvent::between),
                Codec.BOOL.fieldOf("endsSplit").forGetter(DisplayNodePositionEvent::endsSplit),
                Codec.INT.fieldOf("lengthScaledRounded").forGetter(DisplayNodePositionEvent::lengthScaledRounded),
            ).apply(it, ::DisplayNodePositionEvent)}
        }
        
        override fun recurseTrigger(level: ServerLevel) = showNodeTo(level, target, startPos, between, endsSplit, lengthScaledRounded)
        override fun concludeTrigger(level: ServerLevel) = recurseTrigger(level)
        // override fun createEvent(triggerTime: Duration, count: Long) = DisplayNodePositionEvent(triggerTime, count, target, startPos, between, endsSplit, lengthScaledRounded)
    }

    class DisplayPositionEvent private constructor(triggerTime: Duration, count: Long,
        val target: UUID, val pos: Vec3
    ) : RecursiveGameEvent<DisplayPositionEvent>(triggerTime, id, count, INTERVAL) {
    constructor(triggerTime: Duration, startTime: Duration, target: UUID, position: Vec3,
    _d: Unit = Unit) : this(
            triggerTime, (startTime / INTERVAL).toLong(), target, position
        )
        companion object : GameEventCompanion<DisplayPositionEvent> {
            val INTERVAL = 5.ticks
            
            override val id: String = "gameStart"
            override val codec: MapCodec<DisplayPositionEvent> = RecordCodecBuilder.mapCodec{it.group(
                Duration.CODEC.fieldOf("time").forGetter(DisplayPositionEvent::triggerTime),
                Codec.LONG.fieldOf("period").forGetter(DisplayPositionEvent::count),
                UUIDUtil.CODEC.fieldOf("target").forGetter(DisplayPositionEvent::target),
                Vec3.CODEC.fieldOf("position").forGetter(DisplayPositionEvent::pos),
            ).apply(it, ::DisplayPositionEvent)}
        }
        
        override fun recurseTrigger(level: ServerLevel) = showPosTo(level, target, pos)
        override fun concludeTrigger(level: ServerLevel) = recurseTrigger(level)
        // override fun createEvent(triggerTime: Duration, count: Long) = DisplayPositionEvent(triggerTime, count, target, pos)
    }
}