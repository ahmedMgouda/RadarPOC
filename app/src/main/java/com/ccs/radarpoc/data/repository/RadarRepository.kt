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
    private val staleTimeoutSeconds: Int
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
                
                // Mark stale tracks
                val tracksWithStaleStatus = result.data.map { track ->
                    track.copy().apply {
                        isStale = (now - track.timestamp) > staleTimeoutMs
                    }
                }
                
                _tracks.value = tracksWithStaleStatus
                
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
