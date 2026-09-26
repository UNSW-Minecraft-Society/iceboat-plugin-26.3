package mcsoc.iceboat.eventhandlers

import mcsoc.iceboat.eventhandlers.commands.registerCommands


fun registerEvents() {
    registerCommands()
    registerTickEvents()
}
