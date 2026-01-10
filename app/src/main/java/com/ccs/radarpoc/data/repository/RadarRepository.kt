package com.ccs.radarpoc.data.repository

import com.ccs.radarpoc.data.RadarTrack
import com.ccs.radarpoc.data.RadarTracksResponse
import com.google.gson.Gson
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException
import java.util.concurrent.TimeUnit

/**
 * Result wrapper for repository operations
 */
sealed class Result<out T> {
    data class Success<T>(val data: T) : Result<T>()
    data class Error(val exception: Throwable, val message: String = exception.message ?: "Unknown error") : Result<Nothing>()
    object Loading : Result<Nothing>()
}

/**
 * Repository for radar data operations
 * Provides a clean API for fetching and observing radar tracks
 */
class RadarRepository(
    private val baseUrl: String,
    private val pollIntervalSeconds: Int,
    private val staleTimeoutSeconds: Int,
    private val staleRemovalTimeoutSeconds: Int = 60 // Default 60s, 0 = never remove
) {
    companion object {
        private const val TAG = "RadarRepository"
        private const val CONNECT_TIMEOUT_SECONDS = 5L
        private const val READ_TIMEOUT_SECONDS = 5L
    }
    
    private val client = OkHttpClient.Builder()
        .connectTimeout(CONNECT_TIMEOUT_SECONDS, TimeUnit.SECONDS)
        .readTimeout(READ_TIMEOUT_SECONDS, TimeUnit.SECONDS)
        .build()
    
    private val gson = Gson()
    
    // State flows
    private val _tracks = MutableStateFlow<List<RadarTrack>>(emptyList())
    val tracks: StateFlow<List<RadarTrack>> = _tracks.asStateFlow()
    
    private val _connectionState = MutableStateFlow(false)
    val connectionState: StateFlow<Boolean> = _connectionState.asStateFlow()
    
    private val _error = MutableSharedFlow<String>()
    val error: SharedFlow<String> = _error.asSharedFlow()
    
    private var pollingJob: Job? = null
    
    /**
     * Start polling for radar tracks
     */
    fun startPolling(scope: CoroutineScope) {
        stopPolling()
        
        pollingJob = scope.launch {
            while (isActive) {
                fetchAndUpdateTracks()
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
     * Fetch tracks once and update state
     */
    private suspend fun fetchAndUpdateTracks() {
        when (val result = fetchTracks()) {
            is Result.Success -> {
                val now = System.currentTimeMillis()
                val staleTimeoutMs = staleTimeoutSeconds * 1000L
                val removalTimeoutMs = staleRemovalTimeoutSeconds * 1000L
                
                // Mark stale tracks and auto-remove old ones
                val filteredTracks = result.data
                    .filter { track ->
                        // If removal timeout is 0, never remove (keep all tracks)
                        if (staleRemovalTimeoutSeconds == 0) {
                            true
                        } else {
                            // Remove tracks older than removal timeout
                            val age = now - track.timestamp
                            age < removalTimeoutMs
                        }
                    }
                    .map { track ->
                        track.copy().apply {
                            isStale = (now - track.timestamp) > staleTimeoutMs
                        }
                    }
                
                // Log auto-removal if tracks were removed
                val removedCount = result.data.size - filteredTracks.size
                if (removedCount > 0) {
                    android.util.Log.d(TAG, "Auto-removed $removedCount stale tracks (older than ${staleRemovalTimeoutSeconds}s)")
                }
                
                _tracks.value = filteredTracks
                
                if (!_connectionState.value) {
                    _connectionState.value = true
                }
            }
            is Result.Error -> {
                if (_connectionState.value) {
                    _connectionState.value = false
                }
                _error.emit(result.message)
            }
            is Result.Loading -> {
                // Do nothing
            }
        }
    }
    
    /**
     * Fetch tracks from the API
     */
    suspend fun fetchTracks(): Result<List<RadarTrack>> = withContext(Dispatchers.IO) {
        try {
            val url = "$baseUrl/api/tracks.json"
            val request = Request.Builder()
                .url(url)
                .build()
            
            val response = client.newCall(request).execute()
            
            if (response.isSuccessful) {
                val body = response.body?.string()
                if (body != null) {
                    val tracksResponse = gson.fromJson(body, RadarTracksResponse::class.java)
                    Result.Success(tracksResponse.result)
                } else {
                    Result.Error(IOException("Empty response body"))
                }
            } else {
                Result.Error(IOException("HTTP error: ${response.code}"))
            }
        } catch (e: IOException) {
            Result.Error(e, "Network error: ${e.message}")
        } catch (e: Exception) {
            Result.Error(e, "Unexpected error: ${e.message}")
        }
    }
    
    /**
     * Test connection to radar API
     */
    suspend fun testConnection(): Result<Int> {
        return when (val result = fetchTracks()) {
            is Result.Success -> Result.Success(result.data.size)
            is Result.Error -> result
            is Result.Loading -> Result.Loading
        }
    }
    
    /**
     * Get a specific track by ID
     */
    fun getTrackById(trackId: String): RadarTrack? {
        return _tracks.value.find { it.id == trackId }
    }
    
    /**
     * Create a new instance with updated configuration
     */
    fun withConfig(
        baseUrl: String = this.baseUrl,
        pollIntervalSeconds: Int = this.pollIntervalSeconds,
        staleTimeoutSeconds: Int = this.staleTimeoutSeconds
    ): RadarRepository {
        return RadarRepository(baseUrl, pollIntervalSeconds, staleTimeoutSeconds)
    }
}
