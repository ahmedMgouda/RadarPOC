package com.ccs.radarpoc.ui.main

import com.ccs.radarpoc.data.RadarTrack
import com.ccs.radarpoc.util.GeoUtils

/**
 * Represents which view is currently the main (full screen) view
 */
enum class MainView {
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
    
    val displayTitle: String get() = "Track ${track.id}"
    
    val quickInfo: String get() = buildString {
        append("📍 ${String.format("%.2f", latitude)}°, ${String.format("%.2f", longitude)}°")
        append("  📏 ${String.format("%.0f", altitude)}m")
        append("  💨 ${String.format("%.1f", speed)}m/s")
    }
    
    /**
     * Calculate distance to this track from a given position
     */
    fun distanceFrom(lat: Double, lon: Double): Double {
        return GeoUtils.distanceMeters(lat, lon, latitude, longitude)
    }
}

/**
 * Represents drone location
 */
data class DroneLocationUi(
    val latitude: Double,
    val longitude: Double,
    val altitude: Double
)

/**
 * Represents drone battery state for UI
 */
data class DroneBatteryUi(
    val percentage: Int,
    val isLow: Boolean,
    val isCritical: Boolean,
    val remainingFlightMinutes: Int
) {
    val displayText: String get() = "$percentage%"
    
    val statusIcon: String get() = when {
        isCritical -> "🪫"  // Empty battery
        isLow -> "🔋"       // Low battery
        else -> "🔋"        // Normal battery
    }
}

/**
 * Main UI State - Single source of truth for the main screen
 */
data class MainUiState(
    // View state
    val mainView: MainView = MainView.MAP,
    val isPipVisible: Boolean = false,
    val isTopBarVisible: Boolean = true,
    
    // Connection states
    val radarState: ConnectionState = ConnectionState.Disconnected,
    val droneState: ConnectionState = ConnectionState.Disconnected,
    val cameraState: ConnectionState = ConnectionState.Disconnected,
    
    // Tracks
    val tracks: List<TrackUiModel> = emptyList(),
    val lockedTrackId: String? = null,
    val lockedTrackStale: Boolean = false,  // P1: Track if locked track is stale
    val selectedTrackId: String? = null,
    
    // Drone state
    val droneLocation: DroneLocationUi? = null,
    val droneBattery: DroneBatteryUi? = null,
    
    // Tracking state
    val isTrackingActive: Boolean = false,
    
    // UI feedback
    val isLoading: Boolean = false,
    val errorMessage: String? = null,
    val toastMessage: String? = null
) {
    /**
     * Get the currently locked track
     */
    val lockedTrack: TrackUiModel?
        get() = tracks.find { it.id == lockedTrackId }
    
    /**
     * Get the currently selected track (for bottom sheet)
     */
    val selectedTrack: TrackUiModel?
        get() = tracks.find { it.id == selectedTrackId }
    
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
     * Check if camera is available (drone connected and streaming)
     */
    val isCameraAvailable: Boolean
        get() = cameraState.isConnected
    
    /**
     * Get active (non-stale) tracks count
     */
    val activeTracksCount: Int
        get() = tracks.count { !it.isStale }
    
    /**
     * What should PiP show (opposite of main view)
     */
    val pipContent: MainView
        get() = if (mainView == MainView.MAP) MainView.CAMERA else MainView.MAP
    
    /**
     * Check if currently tracking a target (locked and not stale)
     */
    val isActivelyTracking: Boolean
        get() = lockedTrackId != null && !lockedTrackStale && isTrackingActive
    
    /**
     * Check if battery warning should be shown
     */
    val showBatteryWarning: Boolean
        get() = droneBattery?.isLow == true || droneBattery?.isCritical == true
}

/**
 * Events that can be triggered from the UI
 */
sealed class MainUiEvent {
    // View events
    object SwapViews : MainUiEvent()
    object HidePip : MainUiEvent()
    object ShowPip : MainUiEvent()
    object ToggleTopBar : MainUiEvent()
    
    // Track events
    data class TrackSelected(val trackId: String) : MainUiEvent()
    data class TrackLocked(val trackId: String) : MainUiEvent()
    object TrackUnlocked : MainUiEvent()
    object CloseTrackInfo : MainUiEvent()
    object CenterOnSelectedTrack : MainUiEvent()
    
    // Other events
    object SettingsClicked : MainUiEvent()
    object ToastShown : MainUiEvent()
    object ErrorDismissed : MainUiEvent()
}
