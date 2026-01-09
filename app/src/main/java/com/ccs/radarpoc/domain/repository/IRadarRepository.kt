package com.ccs.radarpoc.domain.repository

import com.ccs.radarpoc.data.RadarTrack
import com.ccs.radarpoc.domain.model.Result
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * Interface for radar data operations.
 * Provides a clean contract for fetching and observing radar tracks.
 * This abstraction enables:
 * - Unit testing with mock implementations
 * - Dependency injection
 * - Future implementation swapping (e.g., different radar APIs)
 */
interface IRadarRepository {
    
    /**
     * Observable list of current radar tracks
     */
    val tracks: StateFlow<List<RadarTrack>>
    
    /**
     * Observable connection state
     */
    val connectionState: StateFlow<Boolean>
    
    /**
     * Observable error events
     */
    val error: SharedFlow<String>
    
    /**
     * Start polling for radar tracks
     * @param scope CoroutineScope to launch polling in
     */
    fun startPolling(scope: CoroutineScope)
    
    /**
     * Stop polling for radar tracks
     */
    fun stopPolling()
    
    /**
     * Fetch tracks once (for testing connection)
     * @return Result containing list of tracks or error
     */
    suspend fun fetchTracks(): Result<List<RadarTrack>>
    
    /**
     * Test connection to radar API
     * @return Result containing track count or error
     */
    suspend fun testConnection(): Result<Int>
    
    /**
     * Get a specific track by ID
     * @param trackId The track ID to find
     * @return The track if found, null otherwise
     */
    fun getTrackById(trackId: String): RadarTrack?
}
