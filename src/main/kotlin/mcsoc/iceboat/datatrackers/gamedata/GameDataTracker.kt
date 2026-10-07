package mcsoc.iceboat.datatrackers.gamedata

import mcsoc.iceboat.datatrackers.LevelDataType
import mcsoc.iceboat.datatrackers.LevelTiedData
import java.util.UUID


class GameDataTracker : LevelTiedData(), GameDataHolder {
    override fun getType() = LevelDataType.RaceData

    override var activeMapKey: String? = null
    override var nodeProgress: MutableList<MutableMap<UUID, LapProgress>> = mutableListOf()
}