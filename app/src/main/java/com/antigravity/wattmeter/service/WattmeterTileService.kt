package com.antigravity.wattmeter.service

import android.content.Intent
import android.os.Build
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import androidx.annotation.RequiresApi
import com.antigravity.wattmeter.data.model.BatterySnapshot
import com.antigravity.wattmeter.data.repository.BatteryRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import java.util.Locale

@RequiresApi(Build.VERSION_CODES.N)
class WattmeterTileService : TileService() {
    private val tileScope = CoroutineScope(Dispatchers.Main)
    private var observeJob: Job? = null

    override fun onStartListening() {
        super.onStartListening()
        updateTileState(ChargingMonitorService.isRunning, ChargingMonitorService.latestSnapshot)

        observeJob?.cancel()
        observeJob = tileScope.launch {
            BatteryRepository.getInstance(applicationContext).snapshotFlow.collectLatest { snapshot ->
                updateTileState(ChargingMonitorService.isRunning, snapshot)
            }
        }
    }

    override fun onStopListening() {
        observeJob?.cancel()
        observeJob = null
        super.onStopListening()
    }

    override fun onClick() {
        super.onClick()
        val currentlyRunning = ChargingMonitorService.isRunning
        val nextAction = if (currentlyRunning) {
            ChargingMonitorService.ACTION_STOP
        } else {
            ChargingMonitorService.ACTION_START
        }

        val serviceIntent = Intent(this, ChargingMonitorService::class.java).apply {
            action = nextAction
        }

        if (currentlyRunning) {
            startService(serviceIntent)
        } else {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                startForegroundService(serviceIntent)
            } else {
                startService(serviceIntent)
            }
        }

        updateTileState(!currentlyRunning, ChargingMonitorService.latestSnapshot)
    }

    private fun updateTileState(active: Boolean, snapshot: BatterySnapshot?) {
        val tile = qsTile ?: return
        tile.state = if (active) Tile.STATE_ACTIVE else Tile.STATE_INACTIVE
        tile.label = "Wattmeter"

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            if (active && snapshot != null) {
                tile.subtitle = String.format(Locale.US, "%.1f W", snapshot.smoothedWatt)
            } else if (active) {
                tile.subtitle = "Active"
            } else {
                tile.subtitle = "Off"
            }
        }

        tile.updateTile()
    }
}
