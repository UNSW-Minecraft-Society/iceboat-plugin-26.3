package mcsoc.iceboat.utils

import net.minecraft.world.phys.Vec3


val Vec3.format: String get() = "(${this.x}, ${this.y}, ${this.z})"