package com.ccs.radarpoc.ui.main

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.ccs.radarpoc.data.AppSettings
import com.ccs.radarpoc.data.repository.DroneGpsTarget
import com.ccs.radarpoc.data.repository.DroneRepository
import com.ccs.radarpoc.data.repository.RadarRepository
import com.ccs.radarpoc.data.repository.TrackingState
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
                val trackUiModels = tracks.map { track ->
                    TrackUiModel(
                        track = track,
                        isLocked = track.id == lockedId,
                        isStale = track.isStale
                    )
                }
                _uiState.update { it.copy(tracks = trackUiModels) }
                
                // Send GPS to drone if track is locked and not stale
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
            _uiState.update { state ->
                val updatedTracks = state.tracks.map { 
                    it.copy(isLocked = it.id == trackId)
                }
                state.copy(
                    tracks = updatedTracks,
                    lockedTrackId = trackId,
                    toastMessage = "Tracking ${track.displayTitle}"
                )
            }
            
            // Send GPS immediately
            sendGpsToDrone(trackId)
        }
    }
    
    /**
     * Unlock the currently locked track and stop drone tracking
     */
    private fun unlockTrack() {
        val wasLocked = _uiState.value.lockedTrackId != null
        
        // Update UI state first
        _uiState.update { state ->
            val updatedTracks = state.tracks.map { it.copy(isLocked = false) }
            state.copy(
                tracks = updatedTracks,
                lockedTrackId = null,
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
                missionUpdateIntervalSeconds = appSettings.missionUpdateInterval
            )
            
            return MainViewModel(radarRepository, droneRepository, appSettings) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
