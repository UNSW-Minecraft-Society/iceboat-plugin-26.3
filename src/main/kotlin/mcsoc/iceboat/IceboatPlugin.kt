package mcsoc.iceboat

import mcsoc.iceboat.tests.registerNodeInsertionTest
import net.fabricmc.api.ModInitializer
import net.minecraft.resources.Identifier
import org.slf4j.Logger
import org.slf4j.LoggerFactory


object IceboatPlugin : ModInitializer {
	const val MOD_ID: String = "iceboat-plugin"
	val LOGGER: Logger = LoggerFactory.getLogger(MOD_ID)

	override fun onInitialize() {
		// This code runs as soon as Minecraft is in a mod-load-ready state.
		// However, some things (like resources) may still be uninitialized.
		// Proceed with mild caution.

		LOGGER.info("Hello Icy world!")
		registerNodeInsertionTest()
	}

	fun id(path: String) = Identifier.fromNamespaceAndPath(MOD_ID, path)
}
