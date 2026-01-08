package com.ccs.radarpoc.ui.main

import com.ccs.radarpoc.data.RadarTrack

/**
 * Represents the different view modes available in the main screen
 */
enum class ViewMode {
    MAP,
    CAMERA,
    SPLIT
}

/**
 * Represents which view is currently in fullscreen
 */
enum class FullscreenTarget {
    NONE,
    MAP,
    CAMERA
}

/**
 * Represents the connection state of a component
 */
sealed class ConnectionState {
    object Disconnected : ConnectionState()
    object Connecting : ConnectionState()
    object Connected : ConnectionState()
    data class Error(val message: String) : ConnectionState()
    
    val isConnected: Boolean get() = this is Connected
}

/**
 * Represents a radar track with UI-specific state
 */
data class TrackUiModel(
    val track: RadarTrack,
    val isLocked: Boolean = false,
    val isStale: Boolean = false
) {
    val id: String get() = track.id
    val latitude: Double get() = track.geolocation.latitude
    val longitude: Double get() = track.geolocation.longitude
    val altitude: Double get() = track.geolocation.altitude
    val speed: Double get() = track.geolocation.speed
    val heading: Double get() = track.geolocation.heading
    
    val displayTitle: String get() = buildString {
        append("Track ${track.id}")
        if (isLocked) append(" [LOCKED]")
        if (isStale) append(" (Stale)")
    }
}

/**
 * Main UI State - Single source of truth for the main screen
 */
data class MainUiState(
    // View mode
    val viewMode: ViewMode = ViewMode.MAP,
    val fullscreenTarget: FullscreenTarget = FullscreenTarget.NONE,
    
    // Connection states
    val radarState: ConnectionState = ConnectionState.Disconnected,
    val droneState: ConnectionState = ConnectionState.Disconnected,
    val cameraState: ConnectionState = ConnectionState.Disconnected,
    
    // Tracks
    val tracks: List<TrackUiModel> = emptyList(),
    val lockedTrackId: String? = null,
    
    // UI feedback
    val isLoading: Boolean = false,
    val errorMessage: String? = null,
    val toastMessage: String? = null
) {
    /**
     * Check if in fullscreen mode
     */
    val isFullScreen: Boolean
        get() = fullscreenTarget != FullscreenTarget.NONE
    
    /**
     * Get the currently locked track
     */
    val lockedTrack: TrackUiModel?
        get() = tracks.find { it.id == lockedTrackId }
    
    /**
     * Check if radar is connected
     */
    val isRadarConnected: Boolean
        get() = radarState.isConnected
    
    /**
     * Check if drone is connected
     */
    val isDroneConnected: Boolean
        get() = droneState.isConnected
    
    /**
     * Check if camera is streaming
     */
    val isCameraStreaming: Boolean
        get() = cameraState.isConnected
    
    /**
     * Get active (non-stale) tracks count
     */
    val activeTracksCount: Int
        get() = tracks.count { !it.isStale }
}

/**
 * Events that can be triggered from the UI
 */
sealed class MainUiEvent {
    data class ViewModeChanged(val mode: ViewMode) : MainUiEvent()
    data class FullscreenChanged(val target: FullscreenTarget) : MainUiEvent()
    object ExitFullscreen : MainUiEvent()
    data class TrackSelected(val trackId: String) : MainUiEvent()
    data class TrackLocked(val trackId: String) : MainUiEvent()
    object TrackUnlocked : MainUiEvent()
    object SettingsClicked : MainUiEvent()
    object ToastShown : MainUiEvent()
    object ErrorDismissed : MainUiEvent()
}
