package mcsoc.iceboat.utils

import com.mojang.serialization.Codec
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds
import kotlin.time.DurationUnit
import kotlin.time.Instant
import kotlin.time.toDuration


inline val Int.ticks: Duration 
    get() = this.milliseconds * 50

inline val Long.ticks: Duration
    get() = this.milliseconds * 50

inline val Duration.inWholeTicks: Long
    get() = (this * 20).inWholeSeconds
    
val Duration.Companion.CODEC: Codec<Duration> 
    get() = Codec.STRING.xmap(Duration::parseIsoString, Duration::toIsoString)