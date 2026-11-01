package mcsoc.iceboat.eventhandlers.commands

import com.mojang.brigadier.arguments.BoolArgumentType
import com.mojang.brigadier.arguments.DoubleArgumentType
import com.mojang.brigadier.arguments.FloatArgumentType
import com.mojang.brigadier.arguments.IntegerArgumentType
import com.mojang.brigadier.arguments.StringArgumentType
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback
import net.minecraft.commands.Commands
import net.minecraft.commands.arguments.EntityArgument
import net.minecraft.commands.arguments.UuidArgument
import net.minecraft.commands.arguments.coordinates.Vec3Argument


const val ROOT_NODE = "iceboat"

internal const val MAP_NAME_ARGUMENT = "map"
private val MAP_NAME_ARGUMENT_NODE get() = Commands.argument(MAP_NAME_ARGUMENT, StringArgumentType.word())
    .suggests(MapKeySuggestionProvider())
    
internal const val NODE_ARGUMENT = "node"
private val NODE_ARGUMENT_NODE get() = Commands.argument(NODE_ARGUMENT, UuidArgument.uuid())

internal const val INDEX_ARGUMENT = "index"
private val INDEX_ARGUMENT_NODE get() = Commands.argument(INDEX_ARGUMENT, IntegerArgumentType.integer(0))

internal const val INT_COUNT_ARGUMENT = "number"
private val INT_ARGUMENT_NODE get() = Commands.argument(INT_COUNT_ARGUMENT, IntegerArgumentType.integer(1))

internal const val VEC3_POSITION_ARGUMENT = "pos1"
private val VEC3_POSITION_ARGUMENT_NODE get() = Commands.argument(VEC3_POSITION_ARGUMENT, Vec3Argument.vec3())

internal const val ROTATION_ARGUMENT = "rot"
private val ROTATION_ARGUMENT_NODE get() = Commands.argument(ROTATION_ARGUMENT, FloatArgumentType.floatArg(-180F, 180F))

internal const val LENGTH_ARGUMENT = "length"
private val LENGTH_ARGUMENT_NODE get() = Commands.argument(LENGTH_ARGUMENT, DoubleArgumentType.doubleArg(0.0))

internal const val PLAYER_ARGUMENT = "player"
private val PLAYER_ARGUMENT_NODE get() = Commands.argument(PLAYER_ARGUMENT, EntityArgument.player())

internal const val PLAYERS_ARGUMENT = "players"
private val PLAYERS_ARGUMENT_NODE get() = Commands.argument(PLAYERS_ARGUMENT, EntityArgument.players())

internal const val SPLIT_ARGUMENT = "endsSplit"
private val SPLIT_ARGUMENT_NODE get() = Commands.argument(SPLIT_ARGUMENT, BoolArgumentType.bool())

internal const val STATE_ARGUMENT = "state"
private val STATE_ARGUMENT_NODE get() = Commands.argument(STATE_ARGUMENT, BoolArgumentType.bool())


fun registerCommands() {
    CommandRegistrationCallback.EVENT.register{dispatcher, buildContext, selection -> dispatcher.register(
    Commands.literal(ROOT_NODE)
        .then(Commands.literal("maps")
            .then(Commands.literal("list")
                .executes(CommandActions::listMaps)
            )
            .then(Commands.literal("clearAll"))
        )
        .then(Commands.literal("map")
            .then(Commands.literal("listNodes")
                .then(MAP_NAME_ARGUMENT_NODE
                    .executes(CommandActions::printMapNodes)
                )
            )
            .then(Commands.literal("appendTo")
                .then(MAP_NAME_ARGUMENT_NODE
                    .then(VEC3_POSITION_ARGUMENT_NODE
                        .then(ROTATION_ARGUMENT_NODE
                            .then(LENGTH_ARGUMENT_NODE
                                .then(SPLIT_ARGUMENT_NODE
                                    .executes(CommandActions::addMapNode)
                                )
                            )
                        )
                    )
                )
            )
            .then(Commands.literal("insertNodeAfter")
                .then(MAP_NAME_ARGUMENT_NODE
                    .then(INDEX_ARGUMENT_NODE
                        .then(VEC3_POSITION_ARGUMENT_NODE
                            .then(ROTATION_ARGUMENT_NODE
                                .then(LENGTH_ARGUMENT_NODE
                                    .then(SPLIT_ARGUMENT_NODE
                                        .executes(CommandActions::insertByMapIndex)
                                    )
                                )
                            )
                        )
                    )
                )
            )
            .then(Commands.literal("addStartPos")
                .then(MAP_NAME_ARGUMENT_NODE
                    .then(VEC3_POSITION_ARGUMENT_NODE
                        .executes(CommandActions::addStartPos)
                    )
                )
            )
            .then(Commands.literal("fix")
                .then(MAP_NAME_ARGUMENT_NODE
                    .executes(CommandActions::fixMap)
                )
            )
            .then(Commands.literal("clear")
                .then(MAP_NAME_ARGUMENT_NODE
                    .executes(CommandActions::clearMap)
                )
            )
        )
        .then(Commands.literal("node")
            .then(Commands.literal("insertAfter")
                .then(NODE_ARGUMENT_NODE
                    .then(VEC3_POSITION_ARGUMENT_NODE
                        .then(ROTATION_ARGUMENT_NODE
                            .then(LENGTH_ARGUMENT_NODE
                                .then(SPLIT_ARGUMENT_NODE
                                    .executes(CommandActions::insertById)
                                )
                            )
                        )
                    )
                )
            )
        )
        .then(Commands.literal("game")
            .then(Commands.literal("start")
                .then(MAP_NAME_ARGUMENT_NODE
                    .then(INT_ARGUMENT_NODE
                        .executes(CommandActions::startGame)
                    )
                )
            )
            .then(Commands.literal("end")
                .executes(CommandActions::endGame)
            )
            .then(Commands.literal("sillymode")
                .then(STATE_ARGUMENT_NODE
                    .executes(CommandActions::setSillyMode)
                )
            )
        )
        .then(Commands.literal("players")
            .then(Commands.literal("debug")
                .then(PLAYER_ARGUMENT_NODE
                    .executes(CommandActions::debugPlayer)
                )
            )
            .then(Commands.literal("add")
                .then(PLAYERS_ARGUMENT_NODE
                    .executes(CommandActions::addPlayersToGame)
                )
            )
            .then(Commands.literal("remove")
                .then(PLAYERS_ARGUMENT_NODE
                    .executes(CommandActions::removePlayersFromGame)
                )
            )
        )
    )}
}