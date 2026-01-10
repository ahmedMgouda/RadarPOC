package com.ccs.radarpoc.data.repository

import android.util.Log
import com.ccs.radarpoc.data.model.RadarFOVData
import com.ccs.radarpoc.data.parser.KMLFOVParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL

/**
 * Repository for fetching and caching radar FOV data
 */
class RadarFOVRepository(
    private var baseUrl: String
) {
    companion object {
        private const val TAG = "RadarFOVRepository"
        private const val FOV_ENDPOINT = "/api/fovs.kml"
        private const val CONNECT_TIMEOUT = 10000 // 10 seconds
        private const val READ_TIMEOUT = 15000 // 15 seconds
    }
    
    // Cached FOV data
    private var cachedData: RadarFOVData? = null
    private var lastFetchTime: Long = 0
    
    /**
     * Update the base URL
     */
    fun updateBaseUrl(newBaseUrl: String) {
        if (baseUrl != newBaseUrl) {
            baseUrl = newBaseUrl
            // Clear cache when URL changes
            cachedData = null
            lastFetchTime = 0
        }
    }
    
    /**
     * Get the full FOV URL
     */
    fun getFOVUrl(): String {
        val base = baseUrl.trimEnd('/')
        return "$base$FOV_ENDPOINT"
    }
    
    /**
     * Fetch FOV data from server
     * @param forceRefresh If true, ignore cache and fetch fresh data
     * @param cacheValidityMs How long to consider cached data valid (default 30 seconds)
     */
    suspend fun fetchFOVData(
        forceRefresh: Boolean = false,
        cacheValidityMs: Long = 30000
    ): Result<RadarFOVData> = withContext(Dispatchers.IO) {
        
        // Return cached data if valid
        if (!forceRefresh && cachedData != null) {
            val age = System.currentTimeMillis() - lastFetchTime
            if (age < cacheValidityMs) {
                Log.d(TAG, "Returning cached FOV data (age: ${age}ms)")
                return@withContext Result.success(cachedData!!)
            }
        }
        
        val url = getFOVUrl()
        Log.d(TAG, "Fetching FOV data from: $url")
        
        try {
            val connection = URL(url).openConnection() as HttpURLConnection
            connection.apply {
                requestMethod = "GET"
                connectTimeout = CONNECT_TIMEOUT
                readTimeout = READ_TIMEOUT
                setRequestProperty("Accept", "application/vnd.google-earth.kml+xml, application/xml, text/xml")
            }
            
            val responseCode = connection.responseCode
            
            if (responseCode == HttpURLConnection.HTTP_OK) {
                val kmlContent = connection.inputStream.bufferedReader().use { it.readText() }
                connection.disconnect()
                
                Log.d(TAG, "Received KML response: ${kmlContent.length} chars")
                
                // Parse KML
                val parseResult = KMLFOVParser.parse(kmlContent)
                
                parseResult.onSuccess { data ->
                    // Update cache
                    cachedData = data
                    lastFetchTime = System.currentTimeMillis()
                    Log.d(TAG, "Successfully parsed FOV data: ${data.radarCount} radars")
                }
                
                parseResult
                
            } else {
                connection.disconnect()
                val error = "HTTP error: $responseCode"
                Log.e(TAG, error)
                Result.failure(Exception(error))
            }
            
        } catch (e: Exception) {
            Log.e(TAG, "Failed to fetch FOV data", e)
            
            // Return cached data if available, even if stale
            cachedData?.let { cached ->
                Log.d(TAG, "Returning stale cached data due to error")
                return@withContext Result.success(cached)
            }
            
            Result.failure(e)
        }
    }
    
    /**
     * Get cached data if available
     */
    fun getCachedData(): RadarFOVData? = cachedData
    
    /**
     * Clear the cache
     */
    fun clearCache() {
        cachedData = null
        lastFetchTime = 0
    }
    
    /**
     * Test connection to FOV endpoint
     */
    suspend fun testConnection(): Pair<Boolean, String> = withContext(Dispatchers.IO) {
        val url = getFOVUrl()
        
        try {
            val connection = URL(url).openConnection() as HttpURLConnection
            connection.apply {
                requestMethod = "GET"
                connectTimeout = 5000
                readTimeout = 5000
            }
            
            val responseCode = connection.responseCode
            val contentType = connection.contentType ?: "unknown"
            val contentLength = connection.contentLength
            
            connection.disconnect()
            
            if (responseCode == HttpURLConnection.HTTP_OK) {
                Pair(true, "✓ Connected\nContent-Type: $contentType\nSize: $contentLength bytes")
            } else {
                Pair(false, "✗ HTTP $responseCode")
            }
            
        } catch (e: Exception) {
            Pair(false, "✗ ${e.message}")
        }
    }
}
