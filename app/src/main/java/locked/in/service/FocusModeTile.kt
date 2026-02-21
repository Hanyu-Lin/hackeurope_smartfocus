package locked.`in`.service

import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import locked.`in`.data.repository.FocusModeRepository
import locked.`in`.data.repository.SettingsRepository

class FocusModeTile : TileService() {

    @EntryPoint
    @InstallIn(SingletonComponent::class)
    interface TileEntryPoint {
        fun settingsRepository(): SettingsRepository
        fun focusModeRepository(): FocusModeRepository
        fun focusModeController(): FocusModeController
    }

    private val tileScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val entryPoint by lazy {
        EntryPointAccessors.fromApplication(applicationContext, TileEntryPoint::class.java)
    }

    override fun onStartListening() {
        super.onStartListening()
        tileScope.launch {
            val activeModeId = entryPoint.settingsRepository().activeFocusModeId.first()
            updateTileState(activeModeId != null)
        }
    }

    override fun onClick() {
        super.onClick()
        tileScope.launch {
            val activeModeId = entryPoint.settingsRepository().activeFocusModeId.first()
            val controller = entryPoint.focusModeController()
            if (activeModeId != null) {
                controller.deactivate()
                updateTileState(false)
            } else {
                // Activate first available mode
                val modes = entryPoint.focusModeRepository().observeAll().first()
                val firstMode = modes.firstOrNull()
                if (firstMode != null) {
                    controller.activate(firstMode.id)
                    updateTileState(true)
                }
            }
        }
    }

    private fun updateTileState(isActive: Boolean) {
        val tile = qsTile ?: return
        tile.state = if (isActive) Tile.STATE_ACTIVE else Tile.STATE_INACTIVE
        tile.label = "SmartFocus"
        tile.subtitle = if (isActive) "Focus On" else "Focus Off"
        tile.updateTile()
    }

    override fun onDestroy() {
        super.onDestroy()
        tileScope.cancel()
    }
}
