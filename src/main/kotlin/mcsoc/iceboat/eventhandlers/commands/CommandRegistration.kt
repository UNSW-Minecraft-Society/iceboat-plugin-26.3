package mcsoc.iceboat.eventhandlers.commands

import com.mojang.brigadier.arguments.StringArgumentType
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback
import net.minecraft.commands.Commands
import net.minecraft.commands.arguments.EntityArgument
import net.minecraft.commands.arguments.coordinates.Vec3Argument


const val ROOT_NODE = "iceboat"

internal const val MAP_NAME_ARGUMENT = "map"
private val MAP_NAME_ARGUMENT_NODE = Commands.argument(MAP_NAME_ARGUMENT, StringArgumentType.word())
    .suggests(MapKeySuggestionProvider())

internal const val VEC3_POSITION_ARGUMENT = "pos1"
private val VEC3_POSITION_ARGUMENT_NODE = Commands.argument(VEC3_POSITION_ARGUMENT, Vec3Argument.vec3())

internal const val PLAYER_ARGUMENT = "player"
private val PLAYER_ARGUMENT_NODE = Commands.argument(PLAYER_ARGUMENT, EntityArgument.player())

fun registerCommands() {
    CommandRegistrationCallback.EVENT.register{dispatcher, buildContext, selection -> dispatcher.register(
    Commands.literal(ROOT_NODE)
        .then(Commands.literal("debug")
            .then(PLAYER_ARGUMENT_NODE
                .executes(CommandActions::debug)
            )
        )
        .then(Commands.literal("nodes")
            .then(Commands.literal("list")
                .then(MAP_NAME_ARGUMENT_NODE
                    .executes(CommandActions::printMapNodes)
                )
            )
            .then(Commands.literal("add")
                .then(MAP_NAME_ARGUMENT_NODE
                    .then(VEC3_POSITION_ARGUMENT_NODE
                        .executes(CommandActions::addMapNode)
                    )
                )
            )
        )
        .then(Commands.literal("game")
            .then(Commands.literal("start")
                .then(MAP_NAME_ARGUMENT_NODE
                    .executes(CommandActions::startGame)
                )
            )
            .then(Commands.literal("end")
                .then(MAP_NAME_ARGUMENT_NODE
                    .executes(CommandActions::endGame)
                )
            )
        )
    )}
}