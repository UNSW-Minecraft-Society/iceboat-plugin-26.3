package mcsoc.iceboat.eventhandlers.commands

import com.mojang.brigadier.arguments.StringArgumentType
import com.mojang.brigadier.context.CommandContext
import mcsoc.iceboat.datatrackers.mapData
import mcsoc.iceboat.datatrackers.mapdata.NodeData
import net.minecraft.commands.CommandSourceStack
import net.minecraft.commands.arguments.coordinates.Vec3Argument
import net.minecraft.network.chat.Component


object CommandActions {
    fun printMapNodes(ctx: CommandContext<CommandSourceStack>): Int {
        val source = ctx.source
        source.level.mapData.printNodes(StringArgumentType.getString(ctx, MAP_NAME_ARGUMENT)){
            source.sendSystemMessage(Component.literal("node: $it"))
        }
        
        return 1
    }
    
    fun addMapNode(ctx: CommandContext<CommandSourceStack>): Int {
        val source = ctx.source
        source.level.mapData.appendNode(
            StringArgumentType.getString(ctx, MAP_NAME_ARGUMENT),
            NodeData(Vec3Argument.getVec3(ctx, VEC3_POSITION_ARGUMENT))
        )
        
        return 1
    }
}