package mcsoc.iceboat.tests

import mcsoc.iceboat.IceboatPlugin
import mcsoc.iceboat.datatrackers.mapdata.NodeData
import mcsoc.iceboat.eventhandlers.commands.VEC3_POSITION_ARGUMENT
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback
import net.minecraft.commands.Commands
import net.minecraft.commands.arguments.coordinates.RotationArgument
import net.minecraft.commands.arguments.coordinates.Vec3Argument


private const val ROOT_NODE = "testboat"

internal const val VEC3_POSITION_ARGUMENT = "pos1"
private val VEC3_POSITION_ARGUMENT_NODE = Commands.argument(VEC3_POSITION_ARGUMENT, Vec3Argument.vec3())

internal const val ROTATION_ARGUMENT = "rot"
private val ROTATION_ARGUMENT_NODE = Commands.argument(ROTATION_ARGUMENT, RotationArgument.rotation())

fun registerTestCommands() {
    CommandRegistrationCallback.EVENT.register{dispatcher, buildContext, selection -> dispatcher.register(
    Commands.literal(ROOT_NODE)
    )}
}