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
        
        // Default values
        const val DEFAULT_RADAR_BASE_URL = "http://192.168.1.100:8080"
        const val DEFAULT_POLL_INTERVAL = 1 // seconds
        const val DEFAULT_STALE_TIMEOUT = 5 // seconds
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
}
