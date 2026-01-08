package com.ccs.radarpoc.ui.main

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.ccs.radarpoc.data.AppSettings
import com.ccs.radarpoc.data.repository.DroneGpsTarget
import com.ccs.radarpoc.data.repository.DroneRepository
import com.ccs.radarpoc.data.repository.RadarRepository
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
                
                // Send GPS to drone if track is locked
                lockedId?.let { id ->
                    tracks.find { it.id == id }?.let { track ->
                        sendGpsToDrone(track.id)
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
                _uiState.update {
                    it.copy(
                        cameraState = if (isStreaming) ConnectionState.Connected else ConnectionState.Disconnected
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
            is MainUiEvent.ViewModeChanged -> {
                _uiState.update { it.copy(viewMode = event.mode) }
            }
            
            is MainUiEvent.ToggleFullScreen -> {
                _uiState.update { it.copy(isFullScreen = !it.isFullScreen) }
            }
            
            is MainUiEvent.TrackSelected -> {
                // Navigation to track details handled by UI
            }
            
            is MainUiEvent.TrackLocked -> {
                lockTrack(event.trackId)
            }
            
            is MainUiEvent.TrackUnlocked -> {
                unlockTrack()
            }
            
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
                    toastMessage = "Locked Track $trackId"
                )
            }
            
            // Send GPS immediately
            sendGpsToDrone(trackId)
        }
    }
    
    /**
     * Unlock the currently locked track
     */
    private fun unlockTrack() {
        _uiState.update { state ->
            val updatedTracks = state.tracks.map { it.copy(isLocked = false) }
            state.copy(
                tracks = updatedTracks,
                lockedTrackId = null,
                toastMessage = "Track unlocked"
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
                _uiState.update { 
                    it.copy(toastMessage = "Drone tracking Track $trackId")
                }
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
            val droneRepository = DroneRepository()
            
            return MainViewModel(radarRepository, droneRepository, appSettings) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
