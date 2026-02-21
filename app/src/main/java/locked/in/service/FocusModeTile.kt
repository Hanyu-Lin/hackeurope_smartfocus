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
import locked.`in`.data.repository.SettingsRepository

class FocusModeTile : TileService() {

    @EntryPoint
    @InstallIn(SingletonComponent::class)
    interface TileEntryPoint {
        fun settingsRepository(): SettingsRepository
        fun focusModeController(): FocusModeController
    }

    private val tileScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val entryPoint by lazy {
        EntryPointAccessors.fromApplication(applicationContext, TileEntryPoint::class.java)
    }

    private val settingsRepository by lazy { entryPoint.settingsRepository() }
    private val controller by lazy { entryPoint.focusModeController() }

    override fun onStartListening() {
        super.onStartListening()
        tileScope.launch {
            val isActive = settingsRepository.focusModeEnabled.first()
            updateTileState(isActive)
        }
    }

    override fun onClick() {
        super.onClick()
        tileScope.launch {
            val wasFocused = settingsRepository.focusModeEnabled.first()
            controller.toggle()
            updateTileState(!wasFocused)
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
