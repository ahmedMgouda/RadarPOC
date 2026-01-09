package com.ccs.radarpoc.ui.main

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.ccs.radarpoc.data.AppSettings
import com.ccs.radarpoc.data.repository.DroneGpsTarget
import com.ccs.radarpoc.data.repository.DroneRepository
import com.ccs.radarpoc.data.repository.RadarRepository
import com.ccs.radarpoc.data.repository.TrackingState
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

/**
 * ViewModel for the main screen
 * Handles all business logic and state management
 */
class MainViewModel(
    private val radarRepository: RadarRepository,
    private val droneRepository: DroneRepository,
    private val appSettings: AppSettings
) : ViewModel() {
    
    companion object {
        private const val TAG = "MainViewModel"
        
        // Time to wait before auto-unlocking a stale track (milliseconds)
        private const val STALE_AUTO_UNLOCK_DELAY_MS = 10_000L  // 10 seconds
        
        // Battery thresholds
        private const val LOW_BATTERY_THRESHOLD = 20
        private const val CRITICAL_BATTERY_THRESHOLD = 10
    }
    
    // UI State
    private val _uiState = MutableStateFlow(MainUiState())
    val uiState: StateFlow<MainUiState> = _uiState.asStateFlow()
    
    // Navigation events
    private val _navigationEvent = MutableSharedFlow<NavigationEvent>()
    val navigationEvent: SharedFlow<NavigationEvent> = _navigationEvent.asSharedFlow()
    
    // Center on track event (for map)
    private val _centerOnTrackEvent = MutableSharedFlow<TrackUiModel>()
    val centerOnTrackEvent: SharedFlow<TrackUiModel> = _centerOnTrackEvent.asSharedFlow()
    
    // Job for stale track auto-unlock timer
    private var staleAutoUnlockJob: Job? = null
    
    // Track when locked track became stale
    private var lockedTrackStaleTimestamp: Long? = null
    
    // Battery warning flags (to avoid spamming)
    private var lowBatteryWarningShown = false
    private var criticalBatteryWarningShown = false
    
    init {
        observeRadarData()
        observeDroneState()
        initializeDrone()
    }
    
    /**
     * Observe radar data and update UI state
     */
    private fun observeRadarData() {
        // Observe tracks
        viewModelScope.launch {
            radarRepository.tracks.collect { tracks ->
                val lockedId = _uiState.value.lockedTrackId
                
                // P0: Check if locked track disappeared (target lost)
                if (lockedId != null) {
                    val lockedTrack = tracks.find { it.id == lockedId }
                    
                    when {
                        // Track completely disappeared from radar
                        lockedTrack == null -> {
                            handleLockedTrackLost()
                            return@collect
                        }
                        
                        // P1: Track became stale
                        lockedTrack.isStale -> {
                            handleLockedTrackStale(lockedTrack.id)
                        }
                        
                        // Track is active again (was stale, now recovered)
                        !lockedTrack.isStale && lockedTrackStaleTimestamp != null -> {
                            handleLockedTrackRecovered()
                        }
                    }
                }
                
                // Build track UI models
                val trackUiModels = tracks.map { track ->
                    TrackUiModel(
                        track = track,
                        isLocked = track.id == lockedId,
                        isStale = track.isStale
                    )
                }
                _uiState.update { it.copy(tracks = trackUiModels) }
                
                // Send GPS to drone if track is locked and NOT stale
                lockedId?.let { id ->
                    tracks.find { it.id == id }?.let { track ->
                        if (!track.isStale) {
                            sendGpsToDrone(track.id)
                        }
                    }
                }
            }
        }
        
        // Observe connection state
        viewModelScope.launch {
            radarRepository.connectionState.collect { isConnected ->
                _uiState.update { 
                    it.copy(
                        radarState = if (isConnected) ConnectionState.Connected else ConnectionState.Disconnected
                    )
                }
            }
        }
        
        // Observe errors
        viewModelScope.launch {
            radarRepository.error.collect { error ->
                _uiState.update { it.copy(errorMessage = error) }
            }
        }
    }
    
    /**
     * P0: Handle when locked track completely disappears from radar
     * This means target is lost - stop tracking and hover
     */
    private fun handleLockedTrackLost() {
        val lockedId = _uiState.value.lockedTrackId ?: return
        
        Log.w(TAG, "Locked track $lockedId lost from radar - stopping and hovering")
        
        // Cancel any stale timer
        cancelStaleAutoUnlockTimer()
        
        // Update UI state
        _uiState.update { state ->
            state.copy(
                lockedTrackId = null,
                lockedTrackStale = false,
                tracks = state.tracks.map { it.copy(isLocked = false) },
                toastMessage = "⚠️ Target lost - Drone hovering"
            )
        }
        
        // Stop drone and hover
        droneRepository.stopTrackingAndHover(
            onSuccess = {
                Log.d(TAG, "Drone now hovering after target lost")
            },
            onError = { error ->
                Log.e(TAG, "Failed to stop drone after target lost: $error")
                _uiState.update { it.copy(errorMessage = "Failed to stop drone: $error") }
            }
        )
    }
    
    /**
     * P1: Handle when locked track becomes stale
     * Shows warning and starts auto-unlock timer
     */
    private fun handleLockedTrackStale(trackId: String) {
        // Only process once when track first becomes stale
        if (lockedTrackStaleTimestamp != null) return
        
        Log.w(TAG, "Locked track $trackId became stale")
        
        lockedTrackStaleTimestamp = System.currentTimeMillis()
        
        _uiState.update { state ->
            state.copy(
                lockedTrackStale = true,
                toastMessage = "⚠️ Track stale - Will auto-unlock in ${STALE_AUTO_UNLOCK_DELAY_MS / 1000}s"
            )
        }
        
        // Start auto-unlock timer
        startStaleAutoUnlockTimer()
    }
    
    /**
     * P1: Handle when locked track recovers from stale state
     */
    private fun handleLockedTrackRecovered() {
        Log.d(TAG, "Locked track recovered from stale state")
        
        // Cancel auto-unlock timer
        cancelStaleAutoUnlockTimer()
        
        lockedTrackStaleTimestamp = null
        
        _uiState.update { state ->
            state.copy(
                lockedTrackStale = false,
                toastMessage = "✓ Track recovered - Resuming tracking"
            )
        }
    }
    
    /**
     * Start timer to auto-unlock stale track
     */
    private fun startStaleAutoUnlockTimer() {
        cancelStaleAutoUnlockTimer()
        
        staleAutoUnlockJob = viewModelScope.launch {
            delay(STALE_AUTO_UNLOCK_DELAY_MS)
            
            // Check if still stale after delay
            if (_uiState.value.lockedTrackStale && _uiState.value.lockedTrackId != null) {
                Log.w(TAG, "Auto-unlocking stale track after timeout")
                
                _uiState.update { it.copy(toastMessage = "⚠️ Track stale too long - Auto-unlocking") }
                
                // Small delay to show the toast
                delay(500)
                
                unlockTrack()
            }
        }
    }
    
    /**
     * Cancel stale auto-unlock timer
     */
    private fun cancelStaleAutoUnlockTimer() {
        staleAutoUnlockJob?.cancel()
        staleAutoUnlockJob = null
        lockedTrackStaleTimestamp = null
    }
    
    /**
     * Observe drone state and update UI state
     */
    private fun observeDroneState() {
        viewModelScope.launch {
            droneRepository.isConnected.collect { isConnected ->
                _uiState.update {
                    it.copy(
                        droneState = if (isConnected) ConnectionState.Connected else ConnectionState.Disconnected
                    )
                }
                // Reset battery warnings when drone disconnects
                if (!isConnected) {
                    lowBatteryWarningShown = false
                    criticalBatteryWarningShown = false
                }
            }
        }
        
        viewModelScope.launch {
            droneRepository.isCameraStreaming.collect { isStreaming ->
                _uiState.update { state ->
                    state.copy(
                        cameraState = if (isStreaming) ConnectionState.Connected else ConnectionState.Disconnected,
                        // Auto-show PiP when camera becomes available
                        isPipVisible = if (isStreaming && !state.isPipVisible && state.mainView == MainView.MAP) true else state.isPipVisible
                    )
                }
            }
        }
        
        // Observe drone GPS location
        viewModelScope.launch {
            droneRepository.droneLocation.collect { location ->
                _uiState.update { state ->
                    state.copy(
                        droneLocation = location?.let {
                            DroneLocationUi(
                                latitude = it.latitude,
                                longitude = it.longitude,
                                altitude = it.altitude
                            )
                        }
                    )
                }
            }
        }
        
        // P2: Observe battery state
        viewModelScope.launch {
            droneRepository.batteryState.collect { battery ->
                if (battery != null) {
                    // Update UI state
                    _uiState.update { state ->
                        state.copy(
                            droneBattery = DroneBatteryUi(
                                percentage = battery.percentage,
                                isLow = battery.isLow,
                                isCritical = battery.isCritical,
                                remainingFlightMinutes = battery.remainingFlightTime / 60
                            )
                        )
                    }
                    
                    // Handle battery warnings
                    handleBatteryWarnings(battery.percentage)
                } else {
                    _uiState.update { it.copy(droneBattery = null) }
                }
            }
        }
        
        // Observe tracking state
        viewModelScope.launch {
            droneRepository.trackingState.collect { trackingState ->
                _uiState.update { state ->
                    state.copy(
                        isTrackingActive = trackingState == TrackingState.TRACKING
                    )
                }
            }
        }
    }
    
    /**
     * P2: Handle battery warnings
     */
    private fun handleBatteryWarnings(batteryPercent: Int) {
        when {
            batteryPercent <= CRITICAL_BATTERY_THRESHOLD && !criticalBatteryWarningShown -> {
                criticalBatteryWarningShown = true
                Log.w(TAG, "Critical battery: $batteryPercent%")
                _uiState.update { it.copy(toastMessage = "🪫 CRITICAL BATTERY ($batteryPercent%) - Auto-unlocking") }
                
                // Auto-unlock on critical battery
                if (_uiState.value.lockedTrackId != null) {
                    viewModelScope.launch {
                        delay(1000) // Show warning first
                        unlockTrack()
                    }
                }
            }
            batteryPercent <= LOW_BATTERY_THRESHOLD && !lowBatteryWarningShown -> {
                lowBatteryWarningShown = true
                Log.w(TAG, "Low battery: $batteryPercent%")
                _uiState.update { it.copy(toastMessage = "🔋 Low battery ($batteryPercent%) - Consider landing soon") }
            }
        }
    }
    
    /**
     * Initialize drone SDK
     */
    private fun initializeDrone() {
        droneRepository.initialize()
    }
    
    /**
     * Start radar polling
     */
    fun startRadarPolling() {
        radarRepository.startPolling(viewModelScope)
    }
    
    /**
     * Stop radar polling
     */
    fun stopRadarPolling() {
        radarRepository.stopPolling()
    }
    
    /**
     * Handle UI events
     */
    fun onEvent(event: MainUiEvent) {
        when (event) {
            // View events
            is MainUiEvent.SwapViews -> {
                _uiState.update { state ->
                    val newMainView = if (state.mainView == MainView.MAP) MainView.CAMERA else MainView.MAP
                    state.copy(mainView = newMainView)
                }
            }
            
            is MainUiEvent.HidePip -> {
                _uiState.update { it.copy(isPipVisible = false) }
            }
            
            is MainUiEvent.ShowPip -> {
                _uiState.update { it.copy(isPipVisible = true) }
            }
            
            is MainUiEvent.ToggleTopBar -> {
                _uiState.update { it.copy(isTopBarVisible = !it.isTopBarVisible) }
            }
            
            // Track events
            is MainUiEvent.TrackSelected -> {
                _uiState.update { it.copy(selectedTrackId = event.trackId) }
            }
            
            is MainUiEvent.TrackLocked -> {
                lockTrack(event.trackId)
            }
            
            is MainUiEvent.TrackUnlocked -> {
                unlockTrack()
            }
            
            is MainUiEvent.CloseTrackInfo -> {
                _uiState.update { it.copy(selectedTrackId = null) }
            }
            
            is MainUiEvent.CenterOnSelectedTrack -> {
                _uiState.value.selectedTrack?.let { track ->
                    viewModelScope.launch {
                        _centerOnTrackEvent.emit(track)
                    }
                }
            }
            
            // Other events
            is MainUiEvent.SettingsClicked -> {
                viewModelScope.launch {
                    _navigationEvent.emit(NavigationEvent.OpenSettings)
                }
            }
            
            is MainUiEvent.ToastShown -> {
                _uiState.update { it.copy(toastMessage = null) }
            }
            
            is MainUiEvent.ErrorDismissed -> {
                _uiState.update { it.copy(errorMessage = null) }
            }
        }
    }
    
    /**
     * Lock a track for GPS sending
     */
    private fun lockTrack(trackId: String) {
        val track = _uiState.value.tracks.find { it.id == trackId }
        if (track != null) {
            // Don't allow locking stale tracks
            if (track.isStale) {
                _uiState.update { it.copy(toastMessage = "Cannot lock stale track") }
                return
            }
            
            // Check battery before locking
            val battery = _uiState.value.droneBattery
            if (battery?.isCritical == true) {
                _uiState.update { it.copy(toastMessage = "🪫 Cannot track - Battery critical") }
                return
            }
            
            _uiState.update { state ->
                val updatedTracks = state.tracks.map { 
                    it.copy(isLocked = it.id == trackId)
                }
                state.copy(
                    tracks = updatedTracks,
                    lockedTrackId = trackId,
                    lockedTrackStale = false,
                    toastMessage = "Tracking ${track.displayTitle}"
                )
            }
            
            // Reset stale tracking
            lockedTrackStaleTimestamp = null
            cancelStaleAutoUnlockTimer()
            
            // Send GPS immediately
            sendGpsToDrone(trackId)
        }
    }
    
    /**
     * Unlock the currently locked track and stop drone tracking
     */
    private fun unlockTrack() {
        val wasLocked = _uiState.value.lockedTrackId != null
        
        // Cancel stale timer
        cancelStaleAutoUnlockTimer()
        
        // Update UI state first
        _uiState.update { state ->
            val updatedTracks = state.tracks.map { it.copy(isLocked = false) }
            state.copy(
                tracks = updatedTracks,
                lockedTrackId = null,
                lockedTrackStale = false,
                toastMessage = if (wasLocked) "Track unlocked - Drone hovering" else null
            )
        }
        
        // Stop drone and hover in place
        if (wasLocked) {
            droneRepository.stopTrackingAndHover(
                onSuccess = {
                    // Drone is now hovering
                },
                onError = { error ->
                    _uiState.update { it.copy(errorMessage = "Failed to stop: $error") }
                }
            )
        }
    }
    
    /**
     * Send GPS coordinates to drone
     */
    private fun sendGpsToDrone(trackId: String) {
        val track = _uiState.value.tracks.find { it.id == trackId }?.track ?: return
        
        val target = DroneGpsTarget(
            latitude = track.geolocation.latitude,
            longitude = track.geolocation.longitude,
            altitude = track.geolocation.altitude,
            trackId = trackId
        )
        
        droneRepository.sendGpsTarget(
            target = target,
            onSuccess = {
                // Silent success - no toast spam during continuous tracking
            },
            onError = { error ->
                _uiState.update { it.copy(errorMessage = error) }
            }
        )
    }
    
    /**
     * Get track by ID
     */
    fun getTrackById(trackId: String): TrackUiModel? {
        return _uiState.value.tracks.find { it.id == trackId }
    }
    
    /**
     * Get drone repository for camera setup
     */
    fun getDroneRepository(): DroneRepository = droneRepository
    
    override fun onCleared() {
        super.onCleared()
        cancelStaleAutoUnlockTimer()
        radarRepository.stopPolling()
        droneRepository.cleanup()
    }
}

/**
 * Navigation events
 */
sealed class NavigationEvent {
    object OpenSettings : NavigationEvent()
}

/**
 * Factory for creating MainViewModel with dependencies
 */
class MainViewModelFactory(
    private val appSettings: AppSettings
) : ViewModelProvider.Factory {
    
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(MainViewModel::class.java)) {
            val radarRepository = RadarRepository(
                baseUrl = appSettings.radarBaseUrl,
                pollIntervalSeconds = appSettings.pollInterval,
                staleTimeoutSeconds = appSettings.staleTimeout
            )
            val droneRepository = DroneRepository(
                missionUpdateIntervalMs = appSettings.missionUpdateInterval * 1000L,
                minimumDistanceMeters = appSettings.minimumDistanceMeters
            )
            
            return MainViewModel(radarRepository, droneRepository, appSettings) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
