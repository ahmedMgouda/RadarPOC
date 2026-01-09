package com.ccs.radarpoc.domain.tracking

import android.util.Log
import com.ccs.radarpoc.data.RadarTrack
import com.ccs.radarpoc.data.repository.DroneGpsTarget
import com.ccs.radarpoc.data.repository.DroneRepository
import com.ccs.radarpoc.data.repository.TrackingState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Events emitted by TrackingManager for UI feedback
 */
sealed class TrackingEvent {
    data class TrackLocked(val trackId: String, val trackName: String) : TrackingEvent()
    object TrackUnlocked : TrackingEvent()
    data class TargetLost(val trackId: String) : TrackingEvent()
    data class TrackStale(val trackId: String, val autoUnlockSeconds: Int) : TrackingEvent()
    data class TrackRecovered(val trackId: String) : TrackingEvent()
    data class StaleAutoUnlock(val trackId: String) : TrackingEvent()
    data class Error(val message: String) : TrackingEvent()
    object LowBattery : TrackingEvent()
    object CriticalBattery : TrackingEvent()
}

/**
 * State of the tracking manager
 */
data class TrackingManagerState(
    val lockedTrackId: String? = null,
    val isTrackStale: Boolean = false,
    val isTracking: Boolean = false,
    val droneTrackingState: TrackingState = TrackingState.IDLE
)

/**
 * Manages target tracking logic separated from ViewModel
 * Handles:
 * - Lock/unlock track
 * - Send GPS to drone
 * - Handle target lost
 * - Handle stale track with auto-unlock
 * - Battery monitoring
 */
class TrackingManager(
    private val droneRepository: DroneRepository,
    private val scope: CoroutineScope,
    private val staleAutoUnlockDelayMs: Long = 10_000L,  // 10 seconds
    private val lowBatteryThreshold: Int = 20,           // 20%
    private val criticalBatteryThreshold: Int = 10       // 10%
) {
    companion object {
        private const val TAG = "TrackingManager"
    }
    
    // State
    private val _state = MutableStateFlow(TrackingManagerState())
    val state: StateFlow<TrackingManagerState> = _state.asStateFlow()
    
    // Events
    private val _events = MutableSharedFlow<TrackingEvent>()
    val events: SharedFlow<TrackingEvent> = _events.asSharedFlow()
    
    // Stale auto-unlock timer
    private var staleAutoUnlockJob: Job? = null
    private var lockedTrackStaleTimestamp: Long? = null
    
    // Battery warning flags (to avoid spamming)
    private var lowBatteryWarningShown = false
    private var criticalBatteryWarningShown = false
    
    init {
        observeDroneState()
    }
    
    /**
     * Observe drone tracking state
     */
    private fun observeDroneState() {
        scope.launch {
            droneRepository.trackingState.collect { trackingState ->
                _state.value = _state.value.copy(
                    droneTrackingState = trackingState,
                    isTracking = trackingState == TrackingState.TRACKING
                )
            }
        }
    }
    
    /**
     * Lock a track for tracking
     * @param track The track to lock
     * @return true if lock was successful, false if track is stale
     */
    fun lockTrack(track: RadarTrack): Boolean {
        if (track.isStale) {
            Log.w(TAG, "Cannot lock stale track ${track.id}")
            scope.launch {
                _events.emit(TrackingEvent.Error("Cannot lock stale track"))
            }
            return false
        }
        
        Log.d(TAG, "Locking track ${track.id}")
        
        // Reset state
        cancelStaleAutoUnlockTimer()
        lockedTrackStaleTimestamp = null
        lowBatteryWarningShown = false
        criticalBatteryWarningShown = false
        
        _state.value = _state.value.copy(
            lockedTrackId = track.id,
            isTrackStale = false
        )
        
        // Send GPS immediately
        sendGpsToDrone(track)
        
        scope.launch {
            _events.emit(TrackingEvent.TrackLocked(track.id, "Track ${track.id}"))
        }
        
        return true
    }
    
    /**
     * Unlock the currently locked track
     * @param reason Optional reason for unlocking (for logging)
     */
    fun unlockTrack(reason: String = "user request") {
        val lockedId = _state.value.lockedTrackId ?: return
        
        Log.d(TAG, "Unlocking track $lockedId (reason: $reason)")
        
        // Cancel stale timer
        cancelStaleAutoUnlockTimer()
        lockedTrackStaleTimestamp = null
        
        // Update state
        _state.value = _state.value.copy(
            lockedTrackId = null,
            isTrackStale = false
        )
        
        // Stop drone and hover
        droneRepository.stopTrackingAndHover(
            onSuccess = {
                Log.d(TAG, "Drone now hovering after unlock")
            },
            onError = { error ->
                Log.e(TAG, "Failed to stop drone: $error")
                scope.launch {
                    _events.emit(TrackingEvent.Error("Failed to stop drone: $error"))
                }
            }
        )
        
        scope.launch {
            _events.emit(TrackingEvent.TrackUnlocked)
        }
    }
    
    /**
     * Update tracking with new track data
     * Call this when radar data is updated
     * @param tracks Current list of tracks from radar
     */
    fun updateTracks(tracks: List<RadarTrack>) {
        val lockedId = _state.value.lockedTrackId ?: return
        
        val lockedTrack = tracks.find { it.id == lockedId }
        
        when {
            // Track completely disappeared
            lockedTrack == null -> {
                handleTargetLost(lockedId)
            }
            
            // Track became stale
            lockedTrack.isStale && !_state.value.isTrackStale -> {
                handleTrackStale(lockedTrack)
            }
            
            // Track recovered from stale
            !lockedTrack.isStale && _state.value.isTrackStale -> {
                handleTrackRecovered(lockedTrack)
            }
            
            // Track is active - send GPS
            !lockedTrack.isStale -> {
                sendGpsToDrone(lockedTrack)
            }
        }
    }
    
    /**
     * Handle target lost (disappeared from radar)
     */
    private fun handleTargetLost(trackId: String) {
        Log.w(TAG, "Target $trackId lost from radar")
        
        cancelStaleAutoUnlockTimer()
        lockedTrackStaleTimestamp = null
        
        _state.value = _state.value.copy(
            lockedTrackId = null,
            isTrackStale = false
        )
        
        // Stop drone
        droneRepository.stopTrackingAndHover(
            onSuccess = {
                Log.d(TAG, "Drone hovering after target lost")
            },
            onError = { error ->
                Log.e(TAG, "Failed to stop drone after target lost: $error")
            }
        )
        
        scope.launch {
            _events.emit(TrackingEvent.TargetLost(trackId))
        }
    }
    
    /**
     * Handle track becoming stale
     */
    private fun handleTrackStale(track: RadarTrack) {
        Log.w(TAG, "Track ${track.id} became stale")
        
        lockedTrackStaleTimestamp = System.currentTimeMillis()
        
        _state.value = _state.value.copy(isTrackStale = true)
        
        scope.launch {
            _events.emit(TrackingEvent.TrackStale(
                trackId = track.id,
                autoUnlockSeconds = (staleAutoUnlockDelayMs / 1000).toInt()
            ))
        }
        
        // Start auto-unlock timer
        startStaleAutoUnlockTimer(track.id)
    }
    
    /**
     * Handle track recovering from stale
     */
    private fun handleTrackRecovered(track: RadarTrack) {
        Log.d(TAG, "Track ${track.id} recovered from stale")
        
        cancelStaleAutoUnlockTimer()
        lockedTrackStaleTimestamp = null
        
        _state.value = _state.value.copy(isTrackStale = false)
        
        scope.launch {
            _events.emit(TrackingEvent.TrackRecovered(track.id))
        }
        
        // Resume tracking
        sendGpsToDrone(track)
    }
    
    /**
     * Send GPS coordinates to drone
     */
    private fun sendGpsToDrone(track: RadarTrack) {
        val target = DroneGpsTarget(
            latitude = track.geolocation.latitude,
            longitude = track.geolocation.longitude,
            altitude = track.geolocation.altitude,
            trackId = track.id
        )
        
        droneRepository.sendGpsTarget(
            target = target,
            onSuccess = {
                // Silent success
            },
            onError = { error ->
                Log.e(TAG, "Failed to send GPS: $error")
                scope.launch {
                    _events.emit(TrackingEvent.Error(error))
                }
            }
        )
    }
    
    /**
     * Start timer to auto-unlock stale track
     */
    private fun startStaleAutoUnlockTimer(trackId: String) {
        cancelStaleAutoUnlockTimer()
        
        staleAutoUnlockJob = scope.launch {
            delay(staleAutoUnlockDelayMs)
            
            // Check if still stale
            if (_state.value.isTrackStale && _state.value.lockedTrackId == trackId) {
                Log.w(TAG, "Auto-unlocking stale track $trackId")
                
                _events.emit(TrackingEvent.StaleAutoUnlock(trackId))
                
                delay(500) // Small delay for UI feedback
                
                unlockTrack("stale timeout")
            }
        }
    }
    
    /**
     * Cancel stale auto-unlock timer
     */
    private fun cancelStaleAutoUnlockTimer() {
        staleAutoUnlockJob?.cancel()
        staleAutoUnlockJob = null
    }
    
    /**
     * Update battery level and check thresholds
     * @param batteryPercent Battery percentage (0-100)
     */
    fun updateBatteryLevel(batteryPercent: Int) {
        when {
            batteryPercent <= criticalBatteryThreshold && !criticalBatteryWarningShown -> {
                criticalBatteryWarningShown = true
                Log.w(TAG, "Critical battery: $batteryPercent%")
                scope.launch {
                    _events.emit(TrackingEvent.CriticalBattery)
                }
                // Auto-unlock on critical battery
                if (_state.value.lockedTrackId != null) {
                    unlockTrack("critical battery")
                }
            }
            batteryPercent <= lowBatteryThreshold && !lowBatteryWarningShown -> {
                lowBatteryWarningShown = true
                Log.w(TAG, "Low battery: $batteryPercent%")
                scope.launch {
                    _events.emit(TrackingEvent.LowBattery)
                }
            }
        }
    }
    
    /**
     * Check if a track is currently locked
     */
    fun isTrackLocked(trackId: String): Boolean {
        return _state.value.lockedTrackId == trackId
    }
    
    /**
     * Get the currently locked track ID
     */
    fun getLockedTrackId(): String? = _state.value.lockedTrackId
    
    /**
     * Check if currently tracking
     */
    fun isTracking(): Boolean = _state.value.isTracking
    
    /**
     * Cleanup resources
     */
    fun cleanup() {
        cancelStaleAutoUnlockTimer()
        if (_state.value.lockedTrackId != null) {
            unlockTrack("cleanup")
        }
    }
}
