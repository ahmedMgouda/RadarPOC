package com.ccs.radarpoc.data

import android.content.Context
import android.content.SharedPreferences

/**
 * Manages app settings using SharedPreferences
 */
class AppSettings(context: Context) {
    private val prefs: SharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    
    companion object {
        private const val PREFS_NAME = "radar_poc_settings"
        private const val KEY_RADAR_BASE_URL = "radar_base_url"
        private const val KEY_POLL_INTERVAL = "poll_interval"
        private const val KEY_STALE_TIMEOUT = "stale_timeout"
        private const val KEY_MISSION_UPDATE_INTERVAL = "mission_update_interval"
        private const val KEY_MINIMUM_DISTANCE = "minimum_distance"
        private const val KEY_ACTIVE_MAP_FILE = "active_map_file"
        
        // Default values
        const val DEFAULT_RADAR_BASE_URL = "http://192.168.1.100:8080"
        const val DEFAULT_POLL_INTERVAL = 1 // seconds
        const val DEFAULT_STALE_TIMEOUT = 5 // seconds
        const val DEFAULT_MISSION_UPDATE_INTERVAL = 3 // seconds
        const val DEFAULT_MINIMUM_DISTANCE = 5.0 // meters
    }
    
    var radarBaseUrl: String
        get() = prefs.getString(KEY_RADAR_BASE_URL, DEFAULT_RADAR_BASE_URL) ?: DEFAULT_RADAR_BASE_URL
        set(value) = prefs.edit().putString(KEY_RADAR_BASE_URL, value).apply()
    
    var pollInterval: Int
        get() = prefs.getInt(KEY_POLL_INTERVAL, DEFAULT_POLL_INTERVAL)
        set(value) = prefs.edit().putInt(KEY_POLL_INTERVAL, value).apply()
    
    var staleTimeout: Int
        get() = prefs.getInt(KEY_STALE_TIMEOUT, DEFAULT_STALE_TIMEOUT)
        set(value) = prefs.edit().putInt(KEY_STALE_TIMEOUT, value).apply()
    
    var missionUpdateInterval: Int
        get() = prefs.getInt(KEY_MISSION_UPDATE_INTERVAL, DEFAULT_MISSION_UPDATE_INTERVAL)
        set(value) = prefs.edit().putInt(KEY_MISSION_UPDATE_INTERVAL, value).apply()
    
    var minimumDistanceMeters: Double
        get() = prefs.getFloat(KEY_MINIMUM_DISTANCE, DEFAULT_MINIMUM_DISTANCE.toFloat()).toDouble()
        set(value) = prefs.edit().putFloat(KEY_MINIMUM_DISTANCE, value.toFloat()).apply()
    
    /**
     * Active offline map file path (null means use default online tiles)
     */
    var activeMapFilePath: String?
        get() = prefs.getString(KEY_ACTIVE_MAP_FILE, null)
        set(value) = prefs.edit().putString(KEY_ACTIVE_MAP_FILE, value).apply()
}
