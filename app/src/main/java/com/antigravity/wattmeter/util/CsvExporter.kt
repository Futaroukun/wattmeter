package com.antigravity.wattmeter.util

import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import com.antigravity.wattmeter.data.model.ChargingSession
import java.io.File
import java.io.FileWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object CsvExporter {

    fun formatToCsv(sessions: List<ChargingSession>): String {
        val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US)
        val sb = StringBuilder()
        sb.append("Session ID,Start Time,End Time,Duration (min),Start Level (%),End Level (%),Peak Watt (W),Avg Watt (W),Min Watt (W),Peak Current (mA),Avg Current (mA),Max Temp (°C),Plug Type,Energy (Wh)\n")

        for (s in sessions) {
            val startDate = dateFormat.format(Date(s.startTime))
            val endDate = dateFormat.format(Date(s.endTime))
            val durationMin = String.format(Locale.US, "%.1f", (s.endTime - s.startTime) / 60000.0)
            val peakWatt = String.format(Locale.US, "%.2f", s.peakWatt)
            val avgWatt = String.format(Locale.US, "%.2f", s.avgWatt)
            val minWatt = String.format(Locale.US, "%.2f", s.minWatt)
            val peakCurrent = String.format(Locale.US, "%.1f", s.peakCurrentMa)
            val avgCurrent = String.format(Locale.US, "%.1f", s.avgCurrentMa)
            val maxTemp = String.format(Locale.US, "%.1f", s.maxTemperature)
            val energy = String.format(Locale.US, "%.3f", s.energyWh)

            sb.append("${s.id},\"$startDate\",\"$endDate\",$durationMin,${s.startLevel},${s.endLevel},$peakWatt,$avgWatt,$minWatt,$peakCurrent,$avgCurrent,$maxTemp,\"${s.plugType}\",$energy\n")
        }
        return sb.toString()
    }

    fun exportAndShare(context: Context, sessions: List<ChargingSession>): Boolean {
        if (sessions.isEmpty()) return false
        return try {
            val exportDir = File(context.cacheDir, "exports")
            if (!exportDir.exists()) {
                exportDir.mkdirs()
            }
            val fileName = "wattmeter_history_${SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())}.csv"
            val file = File(exportDir, fileName)
            FileWriter(file).use { writer ->
                writer.write(formatToCsv(sessions))
            }

            val uri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                file
            )

            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                type = "text/csv"
                putExtra(Intent.EXTRA_STREAM, uri)
                putExtra(Intent.EXTRA_SUBJECT, "Wattmeter Charging History")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }

            val chooser = Intent.createChooser(shareIntent, "Export Charging History").apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(chooser)
            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }
}
