package mcsoc.iceboat.datatrackers.timer

import kotlin.time.Duration


// some value which holds some instant of time
// comparison assumes that both time values are relative to same instant
interface TimeHolder : Comparable<TimeHolder> {
    val time: Duration
    override fun compareTo(other: TimeHolder): Int = time.compareTo(other.time)
    
    // time should never decrease after running this
    fun tick()
    // time should never increase after running this
    fun reset()
}

// a tick accumulator, monotonically increasing except when reset()
interface TickExposer : TimeHolder {
    // exposes the ticks since last tick() invocation
    val timerTick: UInt
    // exposes the seconds since last tick() invocation
    val timerSecond: UInt
}


internal interface TickHolder : TickExposer {
    override var timerTick: UInt
    override var timerSecond: UInt
}