package mcsoc.iceboat.utils

import com.mojang.serialization.Codec

val UInt.Companion.CODEC: Codec<UInt> get() = Codec.INT.xmap(Int::toUInt, UInt::toInt)
