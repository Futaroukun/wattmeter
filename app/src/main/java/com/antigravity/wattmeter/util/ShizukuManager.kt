package com.antigravity.wattmeter.util

import android.content.pm.PackageManager
import rikka.shizuku.Shizuku
import java.io.BufferedReader
import java.io.InputStreamReader

object ShizukuManager {
    fun isAvailable(): Boolean {
        return try {
            Shizuku.pingBinder()
        } catch (e: Exception) {
            false
        }
    }

    fun hasPermission(): Boolean {
        return try {
            if (!isAvailable()) return false
            if (Shizuku.isPreV11()) {
                false
            } else {
                Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED
            }
        } catch (e: Exception) {
            false
        }
    }

    fun requestPermission(requestCode: Int = 1001) {
        try {
            if (isAvailable() && !hasPermission()) {
                Shizuku.requestPermission(requestCode)
            }
        } catch (e: Exception) {
        }
    }

    fun execCommand(command: String): String? {
        if (!hasPermission()) return null
        return try {
            val process = Shizuku.newProcess(arrayOf("sh", "-c", command), null, null)
            val reader = BufferedReader(InputStreamReader(process.inputStream))
            val output = StringBuilder()
            var line: String?
            while (reader.readLine().also { line = it } != null) {
                output.append(line).append("\n")
            }
            process.waitFor()
            reader.close()
            output.toString().trim()
        } catch (e: Exception) {
            null
        }
    }

    fun readSysfs(path: String): String? {
        return execCommand("cat $path 2>/dev/null")
    }

    fun readDumpsysBattery(): String? {
        return execCommand("dumpsys battery 2>/dev/null")
    }
}
