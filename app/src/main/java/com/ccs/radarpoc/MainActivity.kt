package com.ccs.radarpoc

import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.activity.viewModels
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
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
        private const val FULLSCREEN_UI_HIDE_DELAY = 3000L
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
    
    // Fullscreen UI auto-hide handler
    private val hideHandler = Handler(Looper.getMainLooper())
    private val hideRunnable = Runnable { hideFullscreenUI() }
    
    // Window insets controller for immersive mode
    private lateinit var windowInsetsController: WindowInsetsControllerCompat
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        
        setupWindowInsetsController()
        setupClickListeners()
        setupFullscreenListeners()
        setupBackPressHandler()
        setupGoogleMaps()
        setupDroneCamera()
        observeState()
        observeNavigation()
    }
    
    /**
     * Setup window insets controller for immersive mode
     */
    private fun setupWindowInsetsController() {
        WindowCompat.setDecorFitsSystemWindows(window, false)
        windowInsetsController = WindowInsetsControllerCompat(window, binding.root)
        windowInsetsController.systemBarsBehavior = 
            WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
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
     * Setup fullscreen-related click listeners
     */
    private fun setupFullscreenListeners() {
        // Map fullscreen button
        binding.btnMapFullscreen.setOnClickListener {
            viewModel.onEvent(MainUiEvent.FullscreenChanged(FullscreenTarget.MAP))
        }
        
        // Camera fullscreen button
        binding.btnCameraFullscreen.setOnClickListener {
            viewModel.onEvent(MainUiEvent.FullscreenChanged(FullscreenTarget.CAMERA))
        }
        
        // PiP expand button
        binding.btnExpandPip.setOnClickListener {
            viewModel.onEvent(MainUiEvent.FullscreenChanged(FullscreenTarget.CAMERA))
        }
        
        // Exit fullscreen button
        binding.btnExitFullscreen.setOnClickListener {
            viewModel.onEvent(MainUiEvent.ExitFullscreen)
        }
        
        // Tap on map container to show/hide UI in fullscreen
        binding.mapContainer.setOnClickListener {
            if (viewModel.uiState.value.isFullScreen) {
                toggleFullscreenUI()
            }
        }
        
        // Tap on camera container to show/hide UI in fullscreen
        binding.cameraContainer.setOnClickListener {
            if (viewModel.uiState.value.isFullScreen) {
                toggleFullscreenUI()
            }
        }
    }
    
    /**
     * Setup back press handler for fullscreen exit
     */
    private fun setupBackPressHandler() {
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (viewModel.uiState.value.isFullScreen) {
                    viewModel.onEvent(MainUiEvent.ExitFullscreen)
                } else {
                    isEnabled = false
                    onBackPressedDispatcher.onBackPressed()
                }
            }
        })
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
        
        // Tap on map to toggle fullscreen UI
        googleMap?.setOnMapClickListener {
            if (viewModel.uiState.value.isFullScreen) {
                toggleFullscreenUI()
            }
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
        
        updateCameraViews(viewModel.uiState.value)
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
        updateViewMode(state)
        updateFullscreenMode(state)
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
            is ConnectionState.Connected -> getString(R.string.status_radar_connected)
            is ConnectionState.Connecting -> getString(R.string.status_radar_connecting)
            is ConnectionState.Disconnected -> getString(R.string.status_radar_disconnected)
            is ConnectionState.Error -> getString(R.string.status_radar_error)
        }
        
        binding.droneStatus.text = when (state.droneState) {
            is ConnectionState.Connected -> getString(R.string.status_drone_connected)
            is ConnectionState.Connecting -> getString(R.string.status_drone_connecting)
            is ConnectionState.Disconnected -> getString(R.string.status_drone_disconnected)
            is ConnectionState.Error -> getString(R.string.status_drone_error)
        }
        
        binding.cameraStatus.text = when (state.cameraState) {
            is ConnectionState.Connected -> getString(R.string.status_camera_streaming)
            is ConnectionState.Connecting -> getString(R.string.status_camera_starting)
            is ConnectionState.Disconnected -> getString(R.string.status_camera_off)
            is ConnectionState.Error -> getString(R.string.status_camera_error)
        }
    }
    
    /**
     * Update view mode (Map, Camera, Split)
     */
    private fun updateViewMode(state: MainUiState) {
        val mode = state.viewMode
        
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
        updateCameraViews(state)
    }
    
    /**
     * Update camera container visibility based on view mode
     */
    private fun updateCameraViews(state: MainUiState) {
        val mode = state.viewMode
        val fullscreenTarget = state.fullscreenTarget
        
        when {
            // Fullscreen camera mode
            fullscreenTarget == FullscreenTarget.CAMERA -> {
                binding.mapContainer.visibility = View.GONE
                binding.cameraContainer.removeAllViews()
                mainCameraView?.let { binding.cameraContainer.addView(it) }
                binding.cameraContainer.visibility = View.VISIBLE
                binding.pipCameraContainer.visibility = View.GONE
            }
            // Fullscreen map mode
            fullscreenTarget == FullscreenTarget.MAP -> {
                binding.mapContainer.visibility = View.VISIBLE
                binding.cameraContainer.visibility = View.GONE
                binding.pipCameraContainer.visibility = View.GONE
            }
            // Normal MAP mode
            mode == ViewMode.MAP -> {
                binding.mapContainer.visibility = View.VISIBLE
                binding.cameraContainer.removeAllViews()
                binding.cameraContainer.visibility = View.GONE
                binding.pipCameraContainer.visibility = View.GONE
            }
            // Normal CAMERA mode
            mode == ViewMode.CAMERA -> {
                binding.mapContainer.visibility = View.GONE
                binding.pipCameraContainer.removeAllViews()
                binding.pipCameraContainer.visibility = View.GONE
                binding.cameraContainer.removeAllViews()
                mainCameraView?.let { binding.cameraContainer.addView(it) }
                binding.cameraContainer.visibility = View.VISIBLE
            }
            // SPLIT mode
            mode == ViewMode.SPLIT -> {
                binding.mapContainer.visibility = View.VISIBLE
                binding.cameraContainer.removeAllViews()
                binding.cameraContainer.visibility = View.GONE
                binding.pipCameraContainer.removeAllViews()
                pipCameraView?.let { binding.pipCameraContainer.addView(it) }
                binding.pipCameraContainer.visibility = View.VISIBLE
            }
        }
        
        // Update fullscreen button visibility
        binding.btnMapFullscreen.visibility = 
            if (mode == ViewMode.MAP && !state.isFullScreen) View.VISIBLE else View.GONE
        binding.btnCameraFullscreen.visibility = 
            if (mode == ViewMode.CAMERA && !state.isFullScreen) View.VISIBLE else View.GONE
    }
    
    /**
     * Update fullscreen mode UI
     */
    private fun updateFullscreenMode(state: MainUiState) {
        if (state.isFullScreen) {
            enterFullscreen()
        } else {
            exitFullscreen()
        }
    }
    
    /**
     * Enter fullscreen mode
     */
    private fun enterFullscreen() {
        // Hide status bar and bottom toolbar
        binding.statusBar.visibility = View.GONE
        binding.bottomToolbar.visibility = View.GONE
        
        // Show exit fullscreen button
        binding.btnExitFullscreen.visibility = View.VISIBLE
        
        // Enter immersive mode
        windowInsetsController.hide(WindowInsetsCompat.Type.systemBars())
        
        // Keep screen on
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        
        // Schedule UI hide
        scheduleFullscreenUIHide()
    }
    
    /**
     * Exit fullscreen mode
     */
    private fun exitFullscreen() {
        // Show status bar and bottom toolbar
        binding.statusBar.visibility = View.VISIBLE
        binding.bottomToolbar.visibility = View.VISIBLE
        
        // Hide exit fullscreen button
        binding.btnExitFullscreen.visibility = View.GONE
        
        // Exit immersive mode
        windowInsetsController.show(WindowInsetsCompat.Type.systemBars())
        
        // Allow screen to turn off
        window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        
        // Cancel any pending hide
        hideHandler.removeCallbacks(hideRunnable)
    }
    
    /**
     * Toggle fullscreen UI visibility
     */
    private fun toggleFullscreenUI() {
        if (binding.btnExitFullscreen.visibility == View.VISIBLE) {
            hideFullscreenUI()
        } else {
            showFullscreenUI()
        }
    }
    
    /**
     * Show fullscreen UI controls
     */
    private fun showFullscreenUI() {
        binding.btnExitFullscreen.visibility = View.VISIBLE
        binding.btnExitFullscreen.animate()
            .alpha(1f)
            .setDuration(200)
            .start()
        
        scheduleFullscreenUIHide()
    }
    
    /**
     * Hide fullscreen UI controls
     */
    private fun hideFullscreenUI() {
        binding.btnExitFullscreen.animate()
            .alpha(0f)
            .setDuration(200)
            .withEndAction {
                binding.btnExitFullscreen.visibility = View.INVISIBLE
            }
            .start()
    }
    
    /**
     * Schedule hiding fullscreen UI after delay
     */
    private fun scheduleFullscreenUIHide() {
        hideHandler.removeCallbacks(hideRunnable)
        hideHandler.postDelayed(hideRunnable, FULLSCREEN_UI_HIDE_DELAY)
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
        val isInCameraMode = state.viewMode == ViewMode.CAMERA || 
            state.fullscreenTarget == FullscreenTarget.CAMERA
        
        if (lockedTrack != null && isInCameraMode) {
            binding.lockedTrackIndicator.text = getString(R.string.track_locked, lockedTrack.id)
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
            arrayOf(getString(R.string.track_info), getString(R.string.track_unlock))
        } else {
            arrayOf(getString(R.string.track_info), getString(R.string.track_lock))
        }
        
        AlertDialog.Builder(this)
            .setTitle("Track ${trackUi.id}")
            .setItems(options) { _, which ->
                when (which) {
                    0 -> showTrackInfo(trackUi)
                    1 -> {
                        if (trackUi.isLocked) {
                            viewModel.onEvent(MainUiEvent.TrackUnlocked)
                        } else {
                            viewModel.onEvent(MainUiEvent.TrackLocked(trackId))
                        }
                    }
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
            
            Status: ${if (trackUi.isStale) getString(R.string.track_info_status_stale) else getString(R.string.track_info_status_active)}
        """.trimIndent()
        
        AlertDialog.Builder(this)
            .setTitle(getString(R.string.track_info_title, track.id))
            .setMessage(info)
            .setPositiveButton(getString(R.string.dialog_ok), null)
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
        hideHandler.removeCallbacks(hideRunnable)
    }
    
    override fun onDestroy() {
        super.onDestroy()
        cleanupCameraViews()
        hideHandler.removeCallbacks(hideRunnable)
    }
}
