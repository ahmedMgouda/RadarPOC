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
        
        // Map Display Settings
        private const val KEY_SHOW_COMPASS = "show_compass"
        private const val KEY_SHOW_ZOOM_BUTTONS = "show_zoom_buttons"
        private const val KEY_SHOW_SCALE_BAR = "show_scale_bar"
        private const val KEY_ENABLE_MAP_ROTATION = "enable_map_rotation"
        
        // Default values
        const val DEFAULT_RADAR_BASE_URL = "http://192.168.1.100:8080"
        const val DEFAULT_POLL_INTERVAL = 1 // seconds
        const val DEFAULT_STALE_TIMEOUT = 5 // seconds
        const val DEFAULT_MISSION_UPDATE_INTERVAL = 3 // seconds
        const val DEFAULT_MINIMUM_DISTANCE = 5.0 // meters
        
        // Map Display Defaults
        const val DEFAULT_SHOW_COMPASS = true
        const val DEFAULT_SHOW_ZOOM_BUTTONS = true
        const val DEFAULT_SHOW_SCALE_BAR = true
        const val DEFAULT_ENABLE_MAP_ROTATION = false // Professional radar standard
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
    
    // ========================================
    // Map Display Settings
    // ========================================
    
    /**
     * Show/hide compass button on map
     */
    var showCompass: Boolean
        get() = prefs.getBoolean(KEY_SHOW_COMPASS, DEFAULT_SHOW_COMPASS)
        set(value) = prefs.edit().putBoolean(KEY_SHOW_COMPASS, value).apply()
    
    /**
     * Show/hide zoom in/out buttons on map
     */
    var showZoomButtons: Boolean
        get() = prefs.getBoolean(KEY_SHOW_ZOOM_BUTTONS, DEFAULT_SHOW_ZOOM_BUTTONS)
        set(value) = prefs.edit().putBoolean(KEY_SHOW_ZOOM_BUTTONS, value).apply()
    
    /**
     * Show/hide scale bar overlay on map
     */
    var showScaleBar: Boolean
        get() = prefs.getBoolean(KEY_SHOW_SCALE_BAR, DEFAULT_SHOW_SCALE_BAR)
        set(value) = prefs.edit().putBoolean(KEY_SHOW_SCALE_BAR, value).apply()
    
    /**
     * Enable/disable map rotation with gestures
     * Professional radar apps typically keep this disabled (North-up)
     */
    var enableMapRotation: Boolean
        get() = prefs.getBoolean(KEY_ENABLE_MAP_ROTATION, DEFAULT_ENABLE_MAP_ROTATION)
        set(value) = prefs.edit().putBoolean(KEY_ENABLE_MAP_ROTATION, value).apply()
}
