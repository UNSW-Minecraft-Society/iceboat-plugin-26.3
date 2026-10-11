package mcsoc.iceboat.datatrackers.timer

import com.mojang.serialization.Codec
import com.mojang.serialization.MapCodec
import com.mojang.serialization.codecs.RecordCodecBuilder
import mcsoc.iceboat.datatrackers.LevelDataType
import mcsoc.iceboat.datatrackers.LevelTiedData
import mcsoc.iceboat.utils.CODEC
import mcsoc.iceboat.utils.inWholeTicks
import kotlin.time.Duration
import kotlin.time.TimeSource


class GameTimer() : LevelTiedData(), TickHolder {
    override fun getType(): LevelDataType<*> = LevelDataType.GameClock
    companion object {
        val CODEC: MapCodec<GameTimer> = RecordCodecBuilder.mapCodec{it.group(
            Duration.CODEC.fieldOf("time").forGetter(GameTimer::time)
        ).apply(it, ::GameTimer)}
    }
    private constructor(time: Duration) : this() {
        this.time = time
    }
    
    override var time: Duration = Duration.ZERO
    var prevTickTime = TimeSource.Monotonic.markNow()
    override var timerTick: UInt = 0u
    override var timerSecond: UInt = 0u
    
    override fun tick() {
        if (prevTickTime.hasNotPassedNow()) return
        
        var tickDelta = prevTickTime.elapsedNow()
        val oldTime = time
        time += tickDelta
        timerTick = (time.inWholeTicks - oldTime.inWholeTicks).toUInt()
        timerSecond = (time.inWholeSeconds - oldTime.inWholeSeconds).toUInt()
        
        prevTickTime = TimeSource.Monotonic.markNow()
    }
    override fun reset() {
        this.time = Duration.ZERO
    }
}