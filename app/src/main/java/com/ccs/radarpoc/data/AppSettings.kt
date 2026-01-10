package com.ccs.radarpoc.data

import android.content.Context
import android.content.SharedPreferences
import android.util.Base64
import java.security.MessageDigest

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
        
        // FOV Display Settings
        private const val KEY_FOV_POLL_INTERVAL = "fov_poll_interval"
        private const val KEY_SHOW_FOV = "show_fov"
        private const val KEY_SHOW_BORESIGHT = "show_boresight"
        private const val KEY_SHOW_RADAR_MARKERS = "show_radar_markers"
        
        // Authentication Settings
        private const val KEY_AUTH_USERNAME = "auth_username"
        private const val KEY_AUTH_PASSWORD_HASH = "auth_password_hash"
        
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
        
        // FOV Display Defaults
        const val DEFAULT_FOV_POLL_INTERVAL = 30 // seconds (FOV doesn't change often)
        const val DEFAULT_SHOW_FOV = true
        const val DEFAULT_SHOW_BORESIGHT = true
        const val DEFAULT_SHOW_RADAR_MARKERS = true
        
        // Default credentials
        const val DEFAULT_USERNAME = "admin"
        const val DEFAULT_PASSWORD = "admin"
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
    
    // ========================================
    // FOV Display Settings
    // ========================================
    
    /**
     * FOV polling interval in seconds
     * FOV data doesn't change often, so longer intervals are OK
     */
    var fovPollInterval: Int
        get() = prefs.getInt(KEY_FOV_POLL_INTERVAL, DEFAULT_FOV_POLL_INTERVAL)
        set(value) = prefs.edit().putInt(KEY_FOV_POLL_INTERVAL, value).apply()
    
    /**
     * Show/hide radar FOV polygons on map
     */
    var showFOV: Boolean
        get() = prefs.getBoolean(KEY_SHOW_FOV, DEFAULT_SHOW_FOV)
        set(value) = prefs.edit().putBoolean(KEY_SHOW_FOV, value).apply()
    
    /**
     * Show/hide boresight lines on map
     */
    var showBoresight: Boolean
        get() = prefs.getBoolean(KEY_SHOW_BORESIGHT, DEFAULT_SHOW_BORESIGHT)
        set(value) = prefs.edit().putBoolean(KEY_SHOW_BORESIGHT, value).apply()
    
    /**
     * Show/hide radar location markers on map
     */
    var showRadarMarkers: Boolean
        get() = prefs.getBoolean(KEY_SHOW_RADAR_MARKERS, DEFAULT_SHOW_RADAR_MARKERS)
        set(value) = prefs.edit().putBoolean(KEY_SHOW_RADAR_MARKERS, value).apply()
    
    // ========================================
    // Authentication Settings
    // ========================================
    
    /**
     * Get stored username (default: admin)
     */
    var authUsername: String
        get() = prefs.getString(KEY_AUTH_USERNAME, DEFAULT_USERNAME) ?: DEFAULT_USERNAME
        set(value) = prefs.edit().putString(KEY_AUTH_USERNAME, value).apply()
    
    /**
     * Get stored password hash
     */
    private var authPasswordHash: String
        get() = prefs.getString(KEY_AUTH_PASSWORD_HASH, hashPassword(DEFAULT_PASSWORD)) 
            ?: hashPassword(DEFAULT_PASSWORD)
        set(value) = prefs.edit().putString(KEY_AUTH_PASSWORD_HASH, value).apply()
    
    /**
     * Validate credentials
     * @return true if username and password are correct
     */
    fun validateCredentials(username: String, password: String): Boolean {
        val storedUsername = authUsername
        val storedPasswordHash = authPasswordHash
        val inputPasswordHash = hashPassword(password)
        
        return username == storedUsername && inputPasswordHash == storedPasswordHash
    }
    
    /**
     * Update credentials
     * @return true if update was successful
     */
    fun updateCredentials(newUsername: String, newPassword: String): Boolean {
        return try {
            if (newUsername.isBlank() || newPassword.isBlank()) {
                return false
            }
            authUsername = newUsername.trim()
            authPasswordHash = hashPassword(newPassword)
            true
        } catch (e: Exception) {
            false
        }
    }
    
    /**
     * Check if credentials have been changed from default
     */
    fun isUsingDefaultCredentials(): Boolean {
        return authUsername == DEFAULT_USERNAME && 
               authPasswordHash == hashPassword(DEFAULT_PASSWORD)
    }
    
    /**
     * Hash password using SHA-256
     */
    private fun hashPassword(password: String): String {
        val bytes = MessageDigest.getInstance("SHA-256").digest(password.toByteArray())
        return Base64.encodeToString(bytes, Base64.NO_WRAP)
    }
}
