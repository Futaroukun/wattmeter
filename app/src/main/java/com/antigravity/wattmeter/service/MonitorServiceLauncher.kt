package com.antigravity.wattmeter.service

import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log

object MonitorServiceLauncher {
    private const val TAG = "MonitorServiceLauncher"

    fun start(context: Context): Boolean {
        val intent = Intent(context, ChargingMonitorService::class.java).apply {
            action = ChargingMonitorService.ACTION_START
        }
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
            true
        } catch (e: Exception) {
            Log.w(TAG, "Unable to start monitor service from background", e)
            false
        }
    }

    fun stop(context: Context) {
        context.stopService(Intent(context, ChargingMonitorService::class.java))
    }
}
