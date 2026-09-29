package mcsoc.iceboat.utils.gameeffects

import mcsoc.iceboat.datatrackers.mapData
import mcsoc.iceboat.datatrackers.playerData
import net.minecraft.server.level.ServerLevel
import net.minecraft.server.level.ServerPlayer
import java.util.UUID
import kotlin.time.Duration


fun trackPlayerTimeAtNode(level: ServerLevel, player: UUID, node: UUID, time: Duration) {
    level.playerData.trackPlayerTimeAtNode(player, node, time)
    level.mapData.trackPlayerTime(node, level, player)
}

fun ServerPlayer.trackTimeAtNode(node: UUID, time: Duration) 
    = trackPlayerTimeAtNode(this.level(), this.uuid, node, time)