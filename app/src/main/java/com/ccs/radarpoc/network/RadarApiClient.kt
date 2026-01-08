package com.ccs.radarpoc.network

import android.util.Log
import com.ccs.radarpoc.data.RadarTracksResponse
import com.google.gson.Gson
import kotlinx.coroutines.*
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException
import java.util.concurrent.TimeUnit

/**
 * Client for fetching radar track data from the API
 */
class RadarApiClient(
    private val baseUrl: String,
    private val pollIntervalSeconds: Int
) {
    private val TAG = "RadarApiClient"
    private val client = OkHttpClient.Builder()
        .connectTimeout(5, TimeUnit.SECONDS)
        .readTimeout(5, TimeUnit.SECONDS)
        .build()
    
    private val gson = Gson()
    private var pollingJob: Job? = null
    
    interface RadarDataListener {
        fun onTracksReceived(tracks: RadarTracksResponse)
        fun onError(error: String)
        fun onConnectionStatusChanged(isConnected: Boolean)
    }
    
    private var listener: RadarDataListener? = null
    private var isConnected = false
    
    fun setListener(listener: RadarDataListener?) {
        this.listener = listener
    }
    
    /**
     * Start polling for radar tracks
     */
    fun startPolling(scope: CoroutineScope) {
        stopPolling()
        
        pollingJob = scope.launch {
            while (isActive) {
                try {
                    val response = fetchTracks()
                    if (response != null) {
                        if (!isConnected) {
                            isConnected = true
                            withContext(Dispatchers.Main) {
                                listener?.onConnectionStatusChanged(true)
                            }
                        }
                        withContext(Dispatchers.Main) {
                            listener?.onTracksReceived(response)
                        }
                    } else {
                        if (isConnected) {
                            isConnected = false
                            withContext(Dispatchers.Main) {
                                listener?.onConnectionStatusChanged(false)
                            }
                        }
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Error during polling", e)
                    if (isConnected) {
                        isConnected = false
                        withContext(Dispatchers.Main) {
                            listener?.onConnectionStatusChanged(false)
                            listener?.onError(e.message ?: "Unknown error")
                        }
                    }
                }
                
                delay(pollIntervalSeconds * 1000L)
            }
        }
    }
    
    /**
     * Stop polling for radar tracks
     */
    fun stopPolling() {
        pollingJob?.cancel()
        pollingJob = null
    }
    
    /**
     * Test connection to radar API
     */
    suspend fun testConnection(): Pair<Boolean, String> = withContext(Dispatchers.IO) {
        try {
            val response = fetchTracks()
            if (response != null) {
                Pair(true, "Connection successful. Found ${response.result.size} tracks.")
            } else {
                Pair(false, "Failed to fetch tracks")
            }
        } catch (e: Exception) {
            Pair(false, "Connection error: ${e.message}")
        }
    }
    
    /**
     * Fetch tracks from the API
     */
    private suspend fun fetchTracks(): RadarTracksResponse? = withContext(Dispatchers.IO) {
        try {
            val url = "$baseUrl/api/tracks.json"
            val request = Request.Builder()
                .url(url)
                .build()
            
            val response = client.newCall(request).execute()
            
            if (response.isSuccessful) {
                val body = response.body?.string()
                if (body != null) {
                    gson.fromJson(body, RadarTracksResponse::class.java)
                } else {
                    null
                }
            } else {
                Log.e(TAG, "HTTP error: ${response.code}")
                null
            }
        } catch (e: IOException) {
            Log.e(TAG, "Network error", e)
            null
        } catch (e: Exception) {
            Log.e(TAG, "Unexpected error", e)
            null
        }
    }
}
