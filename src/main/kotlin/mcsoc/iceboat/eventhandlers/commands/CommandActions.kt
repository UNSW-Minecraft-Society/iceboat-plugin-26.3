package mcsoc.iceboat.eventhandlers.commands

import com.mojang.brigadier.arguments.BoolArgumentType
import com.mojang.brigadier.arguments.DoubleArgumentType
import com.mojang.brigadier.arguments.FloatArgumentType
import com.mojang.brigadier.arguments.IntegerArgumentType
import com.mojang.brigadier.arguments.StringArgumentType
import com.mojang.brigadier.context.CommandContext
import mcsoc.iceboat.IceboatPlugin
import mcsoc.iceboat.datatrackers.mapData
import mcsoc.iceboat.datatrackers.mapdata.NodeData
import mcsoc.iceboat.datatrackers.playerData
import mcsoc.iceboat.datatrackers.raceData
import mcsoc.iceboat.gameeffects.endRound
import mcsoc.iceboat.gameeffects.startRound
import net.minecraft.commands.CommandSourceStack
import net.minecraft.commands.arguments.EntityArgument
import net.minecraft.commands.arguments.UuidArgument
import net.minecraft.commands.arguments.coordinates.Vec3Argument
import net.minecraft.network.chat.Component
import net.minecraft.world.phys.Vec3


private val CANNOT_MODIFY_MAP: (String) -> Component = {Component.literal("Cannot modify active map '$it'!")}

private fun removeMap(source: CommandSourceStack, mapId: String): Int {
    if (source.level.raceData.activeMapKey == mapId) {
        source.sendFailure(CANNOT_MODIFY_MAP(mapId))
        return 0
    }
    
    source.level.mapData.clearMap(mapId)
    source.sendSuccess({Component.literal("Removed all nodes from map '$mapId'")}, true)
    return 1
}

private fun nodeFromArguments(ctx: CommandContext<CommandSourceStack>): NodeData {
    return try {
        NodeData(
            Vec3Argument.getVec3(ctx, VEC3_POSITION_ARGUMENT),
            BoolArgumentType.getBool(ctx, SPLIT_ARGUMENT),
            FloatArgumentType.getFloat(ctx, ROTATION_ARGUMENT),
            DoubleArgumentType.getDouble(ctx, LENGTH_ARGUMENT) / 2
        )
    } catch (e: Exception) {
        IceboatPlugin.LOGGER.error("nodeFromArguments: ", e)
        NodeData(Vec3.ZERO)
    }
} 


object CommandActions {
    fun listMaps(ctx: CommandContext<CommandSourceStack>): Int {
        val source = ctx.source
        source.sendSystemMessage(Component.literal("Available Maps:\n  ${source.level.mapData.getMapKeys().joinToString("\n  ")}"))
        return 1
    }
    
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
        val mapId = StringArgumentType.getString(ctx, MAP_NAME_ARGUMENT)
        if (source.level.raceData.activeMapKey == mapId) {
            source.sendFailure(CANNOT_MODIFY_MAP(mapId))
            return 0
        }
        
        source.level.mapData.appendNode(
            mapId,
            nodeFromArguments(ctx)
        )
        
        source.sendSuccess({Component.literal("Appended node to map '$mapId.'")}, true)
        return 1
    }
    
    fun insertById(ctx: CommandContext<CommandSourceStack>): Int {
        val source = ctx.source
        val nodeId = UuidArgument.getUuid(ctx, NODE_ARGUMENT)
        source.level.mapData.insertNodeAfter(
            nodeId,
            nodeFromArguments(ctx)
        )
        
        source.sendSuccess({Component.literal("Added node after node $nodeId.")}, true)
        return 1
    }
    
    fun insertByMapIndex(ctx: CommandContext<CommandSourceStack>): Int {
        val source = ctx.source
        val mapId = StringArgumentType.getString(ctx, MAP_NAME_ARGUMENT)
        if (source.level.raceData.activeMapKey == mapId) {
            source.sendFailure(CANNOT_MODIFY_MAP(mapId))
            return 0
        }
        val index = IntegerArgumentType.getInteger(ctx, INDEX_ARGUMENT).toUInt()
        source.level.mapData.insertNodeAfter(
            mapId,
            nodeFromArguments(ctx),
            index
        )
        
        source.sendSuccess({Component.literal("Added node to map '$mapId' after index $index.")}, true)
        return 1
    }
    
    fun addStartPos(ctx: CommandContext<CommandSourceStack>): Int {
        val mapId = StringArgumentType.getString(ctx, MAP_NAME_ARGUMENT)
        ctx.source.level.mapData.getMapData(mapId)?.startPositions?.add(Vec3Argument.getVec3(ctx, VEC3_POSITION_ARGUMENT))
        ctx.source.sendSuccess({Component.literal("Added start position to map '$mapId'.")}, true)
        return 1
    }
    
    fun fixMap(ctx: CommandContext<CommandSourceStack>): Int {
        val source = ctx.source
        val mapId = StringArgumentType.getString(ctx, MAP_NAME_ARGUMENT)
        source.level.mapData.fixMap(mapId)
        return 1
    }
    
    fun clearMap(ctx: CommandContext<CommandSourceStack>): Int {
        val source = ctx.source
        val mapId = StringArgumentType.getString(ctx, MAP_NAME_ARGUMENT)
        return removeMap(source, mapId)
    }

    fun clearAllMaps(ctx: CommandContext<CommandSourceStack>): Int {
        val source = ctx.source
        source.level.mapData.getMapKeys().forEach{removeMap(source, it)}
        source.sendSuccess({Component.literal("Removed all nodes from all maps")}, true)
        return 1
    }
    
    fun startGame(ctx: CommandContext<CommandSourceStack>): Int {
        val source = ctx.source
        val mapId = StringArgumentType.getString(ctx, MAP_NAME_ARGUMENT)
        val numLaps = IntegerArgumentType.getInteger(ctx, INT_COUNT_ARGUMENT).toUInt()
        return when (source.level.startRound(mapId, numLaps)) {
            0 -> {
                source.sendSuccess({Component.literal("Started a new round on map '$mapId.'")}, true)
                1
            }
            1 -> {
                source.sendFailure(Component.literal("Failed to start game: Map with key '$mapId' doesn't exist!"))
                0
            }
            2 -> {
                source.sendFailure(Component.literal("Failed to start game: First node of map '$mapId' does not exist!"))
                0
            }
            3 -> {
                source.sendFailure(Component.literal("Failed to start game: Map '$mapId' doesn't have enough start positions!"))
                0
            }
            else -> {
                source.sendFailure(Component.literal("Failed to start game: Reason Not Defined!"))
                0
            }
        }
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
            
            player.sendSystemMessage(Component.literal("You have been signed up for the next race!"))
            ctx.source.sendSuccess({Component.literal("Added ${player.displayName.string} to the next race.")}, true)
        }
        
        return 1
    }
    
    fun removePlayersFromGame(ctx: CommandContext<CommandSourceStack>): Int {        
        for (player in EntityArgument.getPlayers(ctx, PLAYERS_ARGUMENT)) {
            if (ctx.source.level.playerData.activePlayers.remove(player.uuid)) {
                player.sendSystemMessage(Component.literal("You are no longer signed up for the next race."))
                ctx.source.sendSuccess({Component.literal("Removed ${player.displayName.string} from the next race.")}, true)
            }
        }
        
        return 1
    }
}