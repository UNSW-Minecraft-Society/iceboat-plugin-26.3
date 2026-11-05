package mcsoc.iceboat.utils

import net.minecraft.world.phys.Vec3
import kotlin.time.Duration


val Vec3.format: String get() = "(${this.x}, ${this.y}, ${this.z})"

val Duration.formatMinuteSecondMillis get() = this.toComponents{ minutes, seconds, nanos -> 
        "$minutes".padStart(2, '0') + ":" + "$seconds".padStart(2, '0') + ":" + "$nanos".padEnd(2, '0').substring(0..<2)
    }

val Duration.formatSecondMillis get() = this.toComponents{ seconds, nanos -> 
    "$seconds" + "." + "$nanos".padStart(2, '0').substring(0..<2) + "s"
}