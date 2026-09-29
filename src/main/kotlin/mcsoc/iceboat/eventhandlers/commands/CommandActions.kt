package mcsoc.iceboat.eventhandlers.commands

import com.mojang.brigadier.arguments.StringArgumentType
import com.mojang.brigadier.context.CommandContext
import mcsoc.iceboat.datatrackers.mapData
import mcsoc.iceboat.datatrackers.mapdata.NodeData
import mcsoc.iceboat.datatrackers.playerData
import mcsoc.iceboat.utils.gameeffects.endRound
import mcsoc.iceboat.utils.gameeffects.startRound
import net.minecraft.commands.CommandSourceStack
import net.minecraft.commands.arguments.EntityArgument
import net.minecraft.commands.arguments.coordinates.Vec3Argument
import net.minecraft.network.chat.Component
import net.minecraft.world.entity.player.Player


object CommandActions {
    fun printMapNodes(ctx: CommandContext<CommandSourceStack>): Int {
        val source = ctx.source
        val map_key = StringArgumentType.getString(ctx, MAP_NAME_ARGUMENT)
        source.sendSystemMessage(Component.literal("Map $map_key:"))
        source.level.mapData.printNodes(map_key){
            source.sendSystemMessage(Component.literal("$it"))
        }
        
        return 1
    }
    
    fun addMapNode(ctx: CommandContext<CommandSourceStack>): Int {
        val source = ctx.source
        val map_key = StringArgumentType.getString(ctx, MAP_NAME_ARGUMENT)
        source.level.mapData.appendNode(
            StringArgumentType.getString(ctx, MAP_NAME_ARGUMENT),
            NodeData(Vec3Argument.getVec3(ctx, VEC3_POSITION_ARGUMENT))
        )
        
        source.sendSuccess({Component.literal("Added node to map '$map_key.'")}, true)
        return 1
    }
    
    fun startGame(ctx: CommandContext<CommandSourceStack>): Int {
        val source = ctx.source
        val start_key = StringArgumentType.getString(ctx, MAP_NAME_ARGUMENT)
        val start = source.level.mapData.getFirstMapNode(start_key) ?: run {
            source.sendFailure(Component.literal("Failed to start game: Map with key '$start_key' doesn't exist!"))
            return 0
        }
        source.level.startRound(start)
        
        source.sendSuccess({Component.literal("Started a new round on map '$start_key.'")}, true)
        return 1
    }
    
    fun endGame(ctx: CommandContext<CommandSourceStack>): Int {
        val source = ctx.source
        source.level.endRound()
        source.sendSuccess({Component.literal("Ended the current round.")}, true)
        return 1
    }
    
    fun debugPlayer(ctx: CommandContext<CommandSourceStack>): Int {
        val source = ctx.source
        val playerData = source.level.playerData.player_data[EntityArgument.getPlayer(ctx, PLAYER_ARGUMENT).uuid]
        source.sendSystemMessage(Component.literal("$playerData"))
        
        return 1
    }
    
    fun addPlayersToGame(ctx: CommandContext<CommandSourceStack>): Int {
        val level = ctx.source.level
        for (player in EntityArgument.getPlayers(ctx, PLAYERS_ARGUMENT)) {
            level.playerData.getOrInitPlayerData(player)
            level.playerData.activePlayers.add(player.uuid)
        }
        
        return 1
    }
    
    fun removePlayersFromGame(ctx: CommandContext<CommandSourceStack>): Int {
        ctx.source.level.playerData.activePlayers.removeAll(
            EntityArgument.getPlayers(ctx, PLAYERS_ARGUMENT).map(Player::getUUID).toSet()
        )
        
        return 1
    }
}