package com.ccs.radarpoc

import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.view.View
import android.widget.FrameLayout
import android.widget.Toast
import androidx.activity.viewModels
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.autel.sdk.widget.AutelCodecView
import com.ccs.radarpoc.data.AppSettings
import com.ccs.radarpoc.databinding.ActivityMainBinding
import com.ccs.radarpoc.ui.main.*
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.OnMapReadyCallback
import com.google.android.gms.maps.SupportMapFragment
import com.google.android.gms.maps.model.*
import kotlinx.coroutines.launch

/**
 * Main Activity - Thin UI layer that observes ViewModel state
 */
class MainActivity : AppCompatActivity(), OnMapReadyCallback {
    
    companion object {
        private const val TAG = "MainActivity"
        private const val DEFAULT_ZOOM = 15f
        private const val DEFAULT_LAT = 30.0444  // Cairo, Egypt
        private const val DEFAULT_LNG = 31.2357
    }
    
    // View Binding
    private lateinit var binding: ActivityMainBinding
    
    // ViewModel
    private val viewModel: MainViewModel by viewModels {
        MainViewModelFactory(AppSettings(this))
    }
    
    // Google Maps
    private var googleMap: GoogleMap? = null
    private val trackMarkers = mutableMapOf<String, Marker>()
    
    // Camera Views
    private var mainCameraView: AutelCodecView? = null
    private var pipCameraView: AutelCodecView? = null
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        
        setupClickListeners()
        setupGoogleMaps()
        setupDroneCamera()
        observeState()
        observeNavigation()
    }
    
    /**
     * Setup click listeners for UI elements
     */
    private fun setupClickListeners() {
        binding.settingsButton.setOnClickListener {
            viewModel.onEvent(MainUiEvent.SettingsClicked)
        }
        
        binding.btnMapMode.setOnClickListener {
            viewModel.onEvent(MainUiEvent.ViewModeChanged(ViewMode.MAP))
        }
        
        binding.btnCameraMode.setOnClickListener {
            viewModel.onEvent(MainUiEvent.ViewModeChanged(ViewMode.CAMERA))
        }
        
        binding.btnSplitMode.setOnClickListener {
            viewModel.onEvent(MainUiEvent.ViewModeChanged(ViewMode.SPLIT))
        }
    }
    
    /**
     * Setup Google Maps
     */
    private fun setupGoogleMaps() {
        val mapFragment = supportFragmentManager
            .findFragmentById(R.id.mapFragment) as SupportMapFragment
        mapFragment.getMapAsync(this)
    }
    
    override fun onMapReady(map: GoogleMap) {
        googleMap = map
        
        // Set default location
        val defaultLocation = LatLng(DEFAULT_LAT, DEFAULT_LNG)
        googleMap?.moveCamera(CameraUpdateFactory.newLatLngZoom(defaultLocation, DEFAULT_ZOOM))
        
        // Enable zoom controls
        googleMap?.uiSettings?.isZoomControlsEnabled = true
        
        // Set marker click listener
        googleMap?.setOnMarkerClickListener { marker ->
            val trackId = marker.tag as? String
            if (trackId != null) {
                showTrackContextMenu(trackId)
            }
            true
        }
    }
    
    /**
     * Setup drone camera callbacks
     */
    private fun setupDroneCamera() {
        viewModel.getDroneRepository().setCameraCallbacks(
            onReady = { codec ->
                runOnUiThread {
                    initializeCameraViews()
                }
            },
            onDisconnected = {
                runOnUiThread {
                    cleanupCameraViews()
                }
            }
        )
    }
    
    /**
     * Initialize camera views
     */
    private fun initializeCameraViews() {
        mainCameraView = AutelCodecView(this).apply {
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )
        }
        
        pipCameraView = AutelCodecView(this).apply {
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )
        }
        
        updateCameraViews(viewModel.uiState.value.viewMode)
    }
    
    /**
     * Cleanup camera views
     */
    private fun cleanupCameraViews() {
        binding.cameraContainer.removeAllViews()
        binding.pipCameraContainer.removeAllViews()
        mainCameraView = null
        pipCameraView = null
    }
    
    /**
     * Observe ViewModel state
     */
    private fun observeState() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.uiState.collect { state ->
                    updateUI(state)
                }
            }
        }
    }
    
    /**
     * Observe navigation events
     */
    private fun observeNavigation() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.navigationEvent.collect { event ->
                    when (event) {
                        is NavigationEvent.OpenSettings -> {
                            startActivity(Intent(this@MainActivity, SettingsActivity::class.java))
                        }
                    }
                }
            }
        }
    }
    
    /**
     * Update UI based on state
     */
    private fun updateUI(state: MainUiState) {
        updateStatusBar(state)
        updateViewMode(state.viewMode)
        updateMapMarkers(state.tracks)
        updateLockedTrackIndicator(state)
        handleToast(state.toastMessage)
        handleError(state.errorMessage)
    }
    
    /**
     * Update status bar with connection states
     */
    private fun updateStatusBar(state: MainUiState) {
        binding.radarStatus.text = when (state.radarState) {
            is ConnectionState.Connected -> "🟢 Radar: Connected"
            is ConnectionState.Connecting -> "🟡 Radar: Connecting..."
            is ConnectionState.Disconnected -> "🔴 Radar: Disconnected"
            is ConnectionState.Error -> "🔴 Radar: Error"
        }
        
        binding.droneStatus.text = when (state.droneState) {
            is ConnectionState.Connected -> "🟢 Drone: Connected"
            is ConnectionState.Connecting -> "🟡 Drone: Connecting..."
            is ConnectionState.Disconnected -> "🔴 Drone: Disconnected"
            is ConnectionState.Error -> "🔴 Drone: Error"
        }
        
        binding.cameraStatus.text = when (state.cameraState) {
            is ConnectionState.Connected -> "🟢 Camera: Streaming"
            is ConnectionState.Connecting -> "🟡 Camera: Starting..."
            is ConnectionState.Disconnected -> "🔴 Camera: Off"
            is ConnectionState.Error -> "🔴 Camera: Error"
        }
    }
    
    /**
     * Update view mode (Map, Camera, Split)
     */
    private fun updateViewMode(mode: ViewMode) {
        // Update button backgrounds
        binding.btnMapMode.setBackgroundColor(
            if (mode == ViewMode.MAP) Color.LTGRAY else Color.TRANSPARENT
        )
        binding.btnCameraMode.setBackgroundColor(
            if (mode == ViewMode.CAMERA) Color.LTGRAY else Color.TRANSPARENT
        )
        binding.btnSplitMode.setBackgroundColor(
            if (mode == ViewMode.SPLIT) Color.LTGRAY else Color.TRANSPARENT
        )
        
        // Update camera views
        updateCameraViews(mode)
    }
    
    /**
     * Update camera container visibility based on view mode
     */
    private fun updateCameraViews(mode: ViewMode) {
        when (mode) {
            ViewMode.MAP -> {
                binding.cameraContainer.removeAllViews()
                binding.cameraContainer.visibility = View.GONE
                binding.pipCameraContainer.visibility = View.GONE
            }
            ViewMode.CAMERA -> {
                binding.pipCameraContainer.removeAllViews()
                binding.pipCameraContainer.visibility = View.GONE
                binding.cameraContainer.removeAllViews()
                mainCameraView?.let { binding.cameraContainer.addView(it) }
                binding.cameraContainer.visibility = View.VISIBLE
            }
            ViewMode.SPLIT -> {
                binding.cameraContainer.removeAllViews()
                binding.cameraContainer.visibility = View.GONE
                binding.pipCameraContainer.removeAllViews()
                pipCameraView?.let { binding.pipCameraContainer.addView(it) }
                binding.pipCameraContainer.visibility = View.VISIBLE
            }
        }
    }
    
    /**
     * Update map markers based on tracks
     */
    private fun updateMapMarkers(tracks: List<TrackUiModel>) {
        googleMap?.let { map ->
            // Remove markers for tracks that no longer exist
            val currentTrackIds = tracks.map { it.id }.toSet()
            val markersToRemove = trackMarkers.keys.filter { it !in currentTrackIds }
            markersToRemove.forEach { id ->
                trackMarkers[id]?.remove()
                trackMarkers.remove(id)
            }
            
            // Add or update markers
            tracks.forEach { trackUi ->
                val position = LatLng(trackUi.latitude, trackUi.longitude)
                
                val markerColor = when {
                    trackUi.isLocked -> BitmapDescriptorFactory.HUE_ORANGE
                    trackUi.isStale -> BitmapDescriptorFactory.HUE_AZURE
                    else -> BitmapDescriptorFactory.HUE_BLUE
                }
                
                val marker = trackMarkers[trackUi.id]
                if (marker != null) {
                    // Update existing marker
                    marker.position = position
                    marker.setIcon(BitmapDescriptorFactory.defaultMarker(markerColor))
                    marker.title = trackUi.displayTitle
                } else {
                    // Create new marker
                    val newMarker = map.addMarker(
                        MarkerOptions()
                            .position(position)
                            .title(trackUi.displayTitle)
                            .icon(BitmapDescriptorFactory.defaultMarker(markerColor))
                    )
                    newMarker?.tag = trackUi.id
                    if (newMarker != null) {
                        trackMarkers[trackUi.id] = newMarker
                    }
                }
            }
        }
    }
    
    /**
     * Update locked track indicator
     */
    private fun updateLockedTrackIndicator(state: MainUiState) {
        val lockedTrack = state.lockedTrack
        if (lockedTrack != null && state.viewMode == ViewMode.CAMERA) {
            binding.lockedTrackIndicator.text = "Locked: Track ${lockedTrack.id}"
            binding.lockedTrackIndicator.visibility = View.VISIBLE
        } else {
            binding.lockedTrackIndicator.visibility = View.GONE
        }
    }
    
    /**
     * Show track context menu
     */
    private fun showTrackContextMenu(trackId: String) {
        val trackUi = viewModel.getTrackById(trackId) ?: return
        
        val options = if (trackUi.isLocked) {
            arrayOf("Show Info", "Unlock")
        } else {
            arrayOf("Show Info", "Lock")
        }
        
        AlertDialog.Builder(this)
            .setTitle("Track ${trackUi.id}")
            .setItems(options) { _, which ->
                when (options[which]) {
                    "Show Info" -> showTrackInfo(trackUi)
                    "Lock" -> viewModel.onEvent(MainUiEvent.TrackLocked(trackId))
                    "Unlock" -> viewModel.onEvent(MainUiEvent.TrackUnlocked)
                }
            }
            .show()
    }
    
    /**
     * Show track information dialog
     */
    private fun showTrackInfo(trackUi: TrackUiModel) {
        val track = trackUi.track
        val topClassification = track.stats.classifications.maxByOrNull { it.confidence }
        
        val info = """
            Track ID: ${track.id}
            
            Position:
            Lat: ${String.format("%.6f", track.geolocation.latitude)}
            Lon: ${String.format("%.6f", track.geolocation.longitude)}
            Alt: ${String.format("%.2f", track.geolocation.altitude)} m
            
            Movement:
            Speed: ${String.format("%.2f", track.geolocation.speed)} m/s
            Heading: ${String.format("%.1f", track.geolocation.heading)}°
            
            Radar Data:
            Range: ${String.format("%.2f", track.observation.range)} m
            Azimuth: ${String.format("%.1f", track.observation.azimuthAngle)}°
            
            Classification:
            ${topClassification?.type ?: "Unknown"} (${String.format("%.1f", (topClassification?.confidence ?: 0.0) * 100)}%)
            
            Status: ${if (trackUi.isStale) "STALE" else "Active"}
        """.trimIndent()
        
        AlertDialog.Builder(this)
            .setTitle("Track ${track.id} Info")
            .setMessage(info)
            .setPositiveButton("OK", null)
            .show()
    }
    
    /**
     * Handle toast messages
     */
    private fun handleToast(message: String?) {
        message?.let {
            Toast.makeText(this, it, Toast.LENGTH_SHORT).show()
            viewModel.onEvent(MainUiEvent.ToastShown)
        }
    }
    
    /**
     * Handle error messages
     */
    private fun handleError(message: String?) {
        // Could show a Snackbar or dialog for errors
        // For now, just log it
        message?.let {
            viewModel.onEvent(MainUiEvent.ErrorDismissed)
        }
    }
    
    override fun onResume() {
        super.onResume()
        viewModel.startRadarPolling()
    }
    
    override fun onPause() {
        super.onPause()
        viewModel.stopRadarPolling()
    }
    
    override fun onDestroy() {
        super.onDestroy()
        cleanupCameraViews()
    }
}
