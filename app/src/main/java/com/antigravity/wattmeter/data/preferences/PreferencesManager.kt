package com.antigravity.wattmeter.data.preferences

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.antigravity.wattmeter.data.model.NotificationMetric
import com.antigravity.wattmeter.data.model.ThemeMode
import com.antigravity.wattmeter.data.model.UnitMultiplier
import com.antigravity.wattmeter.data.model.UserSettings
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import java.io.IOException

val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "wattmeter_settings")

class PreferencesManager(private val context: Context) {
    companion object {
        private val KEY_THEME_MODE = stringPreferencesKey("theme_mode")
        private val KEY_POLLING_INTERVAL_MS = longPreferencesKey("polling_interval_ms")
        private val KEY_UNIT_MULTIPLIER = stringPreferencesKey("unit_multiplier")
        private val KEY_INVERT_CURRENT_SIGN = booleanPreferencesKey("invert_current_sign")
        private val KEY_MOVING_AVG_WINDOW = intPreferencesKey("moving_avg_window")
        private val KEY_NOTIFICATION_METRIC = stringPreferencesKey("notification_metric")
        private val KEY_ALARM_FULL_BATTERY = booleanPreferencesKey("alarm_full_battery")
        private val KEY_ALARM_LEVEL_THRESHOLD = intPreferencesKey("alarm_level_threshold")
        private val KEY_ALARM_TEMP_THRESHOLD = floatPreferencesKey("alarm_temp_threshold")
        private val KEY_AUTO_START_ON_BOOT = booleanPreferencesKey("auto_start_on_boot")
        private val KEY_AUTO_START_ON_PLUG = booleanPreferencesKey("auto_start_on_plug")
    }

    val userSettingsFlow: Flow<UserSettings> = context.dataStore.data
        .catch { exception ->
            if (exception is IOException) {
                emit(emptyPreferences())
            } else {
                throw exception
            }
        }
        .map { preferences ->
            val themeModeStr = preferences[KEY_THEME_MODE] ?: ThemeMode.SYSTEM.name
            val themeMode = runCatching { ThemeMode.valueOf(themeModeStr) }.getOrDefault(ThemeMode.SYSTEM)

            val pollingInterval = preferences[KEY_POLLING_INTERVAL_MS] ?: 1000L

            val unitMultiplierStr = preferences[KEY_UNIT_MULTIPLIER] ?: UnitMultiplier.AUTO.name
            val unitMultiplier = runCatching { UnitMultiplier.valueOf(unitMultiplierStr) }.getOrDefault(UnitMultiplier.AUTO)

            val invertCurrentSign = preferences[KEY_INVERT_CURRENT_SIGN] ?: false
            val movingAvgWindow = preferences[KEY_MOVING_AVG_WINDOW] ?: 5

            val notifMetricStr = preferences[KEY_NOTIFICATION_METRIC] ?: NotificationMetric.WATT.name
            val notifMetric = runCatching { NotificationMetric.valueOf(notifMetricStr) }.getOrDefault(NotificationMetric.WATT)

            val alarmFull = preferences[KEY_ALARM_FULL_BATTERY] ?: true
            val alarmLevel = preferences[KEY_ALARM_LEVEL_THRESHOLD] ?: 80
            val alarmTemp = preferences[KEY_ALARM_TEMP_THRESHOLD] ?: 42.0f
            val autoStartBoot = preferences[KEY_AUTO_START_ON_BOOT] ?: false
            val autoStartPlug = preferences[KEY_AUTO_START_ON_PLUG] ?: true

            UserSettings(
                themeMode = themeMode,
                pollingIntervalMs = pollingInterval,
                unitMultiplier = unitMultiplier,
                invertCurrentSign = invertCurrentSign,
                movingAvgWindow = movingAvgWindow,
                notificationMetric = notifMetric,
                alarmFullBattery = alarmFull,
                alarmLevelThreshold = alarmLevel,
                alarmTempThreshold = alarmTemp,
                autoStartOnBoot = autoStartBoot,
                autoStartOnPlug = autoStartPlug
            )
        }

    suspend fun setThemeMode(themeMode: ThemeMode) {
        context.dataStore.edit { it[KEY_THEME_MODE] = themeMode.name }
    }

    suspend fun setPollingIntervalMs(intervalMs: Long) {
        context.dataStore.edit { it[KEY_POLLING_INTERVAL_MS] = intervalMs }
    }

    suspend fun setUnitMultiplier(unitMultiplier: UnitMultiplier) {
        context.dataStore.edit { it[KEY_UNIT_MULTIPLIER] = unitMultiplier.name }
    }

    suspend fun setInvertCurrentSign(invert: Boolean) {
        context.dataStore.edit { it[KEY_INVERT_CURRENT_SIGN] = invert }
    }

    suspend fun setMovingAvgWindow(window: Int) {
        context.dataStore.edit { it[KEY_MOVING_AVG_WINDOW] = window }
    }

    suspend fun setNotificationMetric(metric: NotificationMetric) {
        context.dataStore.edit { it[KEY_NOTIFICATION_METRIC] = metric.name }
    }

    suspend fun setAlarmFullBattery(enabled: Boolean) {
        context.dataStore.edit { it[KEY_ALARM_FULL_BATTERY] = enabled }
    }

    suspend fun setAlarmLevelThreshold(threshold: Int) {
        context.dataStore.edit { it[KEY_ALARM_LEVEL_THRESHOLD] = threshold }
    }

    suspend fun setAlarmTempThreshold(threshold: Float) {
        context.dataStore.edit { it[KEY_ALARM_TEMP_THRESHOLD] = threshold }
    }

    suspend fun setAutoStartOnBoot(enabled: Boolean) {
        context.dataStore.edit { it[KEY_AUTO_START_ON_BOOT] = enabled }
    }

    suspend fun setAutoStartOnPlug(enabled: Boolean) {
        context.dataStore.edit { it[KEY_AUTO_START_ON_PLUG] = enabled }
    }
}
