package com.ccs.radarpoc.data.repository

import android.util.Log
import com.ccs.radarpoc.data.RadarTrack
import com.ccs.radarpoc.domain.model.Result
import com.ccs.radarpoc.domain.repository.IRadarRepository
import com.ccs.radarpoc.network.RadarApiService
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import java.io.IOException
import javax.inject.Inject

/**
 * Repository for radar data operations.
 * Implements IRadarRepository interface for proper abstraction.
 * Uses Retrofit for async network operations.
 */
class RadarRepository @Inject constructor(
    private val apiService: RadarApiService,
    private val config: RadarConfig
) : IRadarRepository {
    
    companion object {
        private const val TAG = "RadarRepository"
    }
    
    // State flows
    private val _tracks = MutableStateFlow<List<RadarTrack>>(emptyList())
    override val tracks: StateFlow<List<RadarTrack>> = _tracks.asStateFlow()
    
    private val _connectionState = MutableStateFlow(false)
    override val connectionState: StateFlow<Boolean> = _connectionState.asStateFlow()
    
    private val _error = MutableSharedFlow<String>()
    override val error: SharedFlow<String> = _error.asSharedFlow()
    
    private var pollingJob: Job? = null
    
    /**
     * Start polling for radar tracks
     */
    override fun startPolling(scope: CoroutineScope) {
        stopPolling()
        
        pollingJob = scope.launch {
            while (isActive) {
                fetchAndUpdateTracks()
                delay(config.pollIntervalSeconds * 1000L)
            }
        }
    }
    
    /**
     * Stop polling for radar tracks
     */
    override fun stopPolling() {
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
                val staleTimeoutMs = config.staleTimeoutSeconds * 1000L
                
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
     * Fetch tracks from the API using Retrofit (async)
     */
    override suspend fun fetchTracks(): Result<List<RadarTrack>> = withContext(Dispatchers.IO) {
        try {
            val response = apiService.getTracks()
            
            if (response.isSuccessful) {
                val body = response.body()
                if (body != null) {
                    Result.Success(body.result)
                } else {
                    Result.Error(IOException("Empty response body"))
                }
            } else {
                Result.Error(IOException("HTTP error: ${response.code()}"))
            }
        } catch (e: IOException) {
            Log.e(TAG, "Network error", e)
            Result.Error(e, "Network error: ${e.message}")
        } catch (e: Exception) {
            Log.e(TAG, "Unexpected error", e)
            Result.Error(e, "Unexpected error: ${e.message}")
        }
    }
    
    /**
     * Test connection to radar API
     */
    override suspend fun testConnection(): Result<Int> {
        return when (val result = fetchTracks()) {
            is Result.Success -> Result.Success(result.data.size)
            is Result.Error -> result
            is Result.Loading -> Result.Loading
        }
    }
    
    /**
     * Get a specific track by ID
     */
    override fun getTrackById(trackId: String): RadarTrack? {
        return _tracks.value.find { it.id == trackId }
    }
}

/**
 * Configuration data class for RadarRepository
 */
data class RadarConfig(
    val pollIntervalSeconds: Int,
    val staleTimeoutSeconds: Int
)
