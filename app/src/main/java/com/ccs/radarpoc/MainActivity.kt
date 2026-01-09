package com.ccs.radarpoc

import android.annotation.SuppressLint
import android.content.Intent
import android.graphics.Color
import android.graphics.drawable.BitmapDrawable
import android.os.Bundle
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.Toast
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.GestureDetectorCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.autel.sdk.widget.AutelCodecView
import com.ccs.radarpoc.data.AppSettings
import com.ccs.radarpoc.databinding.ActivityMainBinding
import com.ccs.radarpoc.ui.main.*
import com.google.android.material.bottomsheet.BottomSheetBehavior
import org.osmdroid.config.Configuration
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker
import org.osmdroid.views.overlay.Overlay
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.pow
import kotlin.math.sqrt

/**
 * Main Activity - Material Design 3 UI with draggable PiP
 * Using OSMDroid for full offline map support
 */
class MainActivity : AppCompatActivity() {
    
    companion object {
        private const val TAG = "MainActivity"
        private const val DEFAULT_ZOOM = 15.0
        private const val DEFAULT_LAT = 30.0444  // Cairo, Egypt
        private const val DEFAULT_LNG = 31.2357
        private const val PIP_MARGIN = 16
        private const val MARKER_CLICK_THRESHOLD = 50f // pixels
        
        // Alpha values for status icons
        private const val ALPHA_CONNECTED = 1.0f
        private const val ALPHA_DISCONNECTED = 0.4f
    }
    
    // View Binding
    private lateinit var binding: ActivityMainBinding
    
    // ViewModel
    private val viewModel: MainViewModel by viewModels {
        MainViewModelFactory(AppSettings(this))
    }
    
    // OSMDroid MapView
    private var mapView: MapView? = null
    private val trackMarkers = mutableMapOf<String, Marker>()
    private var droneMarker: Marker? = null
    
    // Camera Views
    private var cameraView: AutelCodecView? = null
    
    // Bottom Sheet
    private lateinit var bottomSheetBehavior: BottomSheetBehavior<View>
    
    // PiP Gesture Detector
    private lateinit var pipGestureDetector: GestureDetectorCompat
    
    // PiP Drag state
    private var pipDragStartX = 0f
    private var pipDragStartY = 0f
    private var pipStartMarginX = 0
    private var pipStartMarginY = 0
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        
        setupBottomSheet()
        setupPipGestures()
        setupClickListeners()
        setupOSMDroid()
        setupDroneCamera()
        observeState()
        observeNavigation()
        observeCenterOnTrack()
    }
    
    /**
     * Setup bottom sheet behavior
     */
    private fun setupBottomSheet() {
        bottomSheetBehavior = BottomSheetBehavior.from(binding.trackInfoBottomSheet)
        bottomSheetBehavior.state = BottomSheetBehavior.STATE_HIDDEN
        
        bottomSheetBehavior.addBottomSheetCallback(object : BottomSheetBehavior.BottomSheetCallback() {
            override fun onStateChanged(bottomSheet: View, newState: Int) {
                if (newState == BottomSheetBehavior.STATE_HIDDEN) {
                    viewModel.onEvent(MainUiEvent.CloseTrackInfo)
                }
            }
            
            override fun onSlide(bottomSheet: View, slideOffset: Float) {
                // Optional: animate something based on slide
            }
        })
    }
    
    /**
     * Setup PiP gesture detection (tap, double-tap, drag)
     */
    @SuppressLint("ClickableViewAccessibility")
    private fun setupPipGestures() {
        pipGestureDetector = GestureDetectorCompat(this, object : GestureDetector.SimpleOnGestureListener() {
            override fun onSingleTapConfirmed(e: MotionEvent): Boolean {
                // Single tap = swap views
                viewModel.onEvent(MainUiEvent.SwapViews)
                return true
            }
            
            override fun onDoubleTap(e: MotionEvent): Boolean {
                // Double tap = hide PiP
                viewModel.onEvent(MainUiEvent.HidePip)
                return true
            }
        })
        
        binding.pipContainer.setOnTouchListener { view, event ->
            pipGestureDetector.onTouchEvent(event)
            
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    pipDragStartX = event.rawX
                    pipDragStartY = event.rawY
                    val params = view.layoutParams as ViewGroup.MarginLayoutParams
                    pipStartMarginX = params.marginStart
                    pipStartMarginY = params.topMargin
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = event.rawX - pipDragStartX
                    val dy = event.rawY - pipDragStartY
                    
                    // Only drag if moved enough (to not interfere with tap)
                    if (abs(dx) > 10 || abs(dy) > 10) {
                        val params = view.layoutParams as ViewGroup.MarginLayoutParams
                        params.marginStart = (pipStartMarginX + dx).toInt().coerceIn(PIP_MARGIN, binding.mainContentContainer.width - view.width - PIP_MARGIN)
                        params.topMargin = (pipStartMarginY + dy).toInt().coerceIn(PIP_MARGIN, binding.mainContentContainer.height - view.height - PIP_MARGIN)
                        view.layoutParams = params
                    }
                    true
                }
                else -> false
            }
        }
    }
    
    /**
     * Setup click listeners
     */
    private fun setupClickListeners() {
        // Top bar collapse button
        binding.btnCollapseTopBar.setOnClickListener {
            viewModel.onEvent(MainUiEvent.ToggleTopBar)
        }
        
        // Collapsed tab (expand top bar)
        binding.topBarCollapsedTab.setOnClickListener {
            viewModel.onEvent(MainUiEvent.ToggleTopBar)
        }
        
        // Settings button
        binding.btnSettings.setOnClickListener {
            viewModel.onEvent(MainUiEvent.SettingsClicked)
        }
        
        // Show PiP FAB
        binding.btnShowPip.setOnClickListener {
            viewModel.onEvent(MainUiEvent.ShowPip)
        }
        
        // Locked track chip - tap to show info
        binding.lockedTrackChip.setOnClickListener {
            viewModel.uiState.value.lockedTrackId?.let { trackId ->
                viewModel.onEvent(MainUiEvent.TrackSelected(trackId))
                showTrackBottomSheet()
            }
        }
        
        // Chip close icon = unlock
        binding.lockedTrackChip.setOnCloseIconClickListener {
            viewModel.onEvent(MainUiEvent.TrackUnlocked)
        }
        
        // Bottom sheet buttons
        binding.btnLockTrack.setOnClickListener {
            viewModel.uiState.value.selectedTrackId?.let { trackId ->
                val isLocked = viewModel.uiState.value.lockedTrackId == trackId
                if (isLocked) {
                    viewModel.onEvent(MainUiEvent.TrackUnlocked)
                } else {
                    viewModel.onEvent(MainUiEvent.TrackLocked(trackId))
                }
            }
        }
        
        binding.btnCloseTrackInfo.setOnClickListener {
            bottomSheetBehavior.state = BottomSheetBehavior.STATE_HIDDEN
        }
        
        binding.btnCenterOnTrack.setOnClickListener {
            viewModel.onEvent(MainUiEvent.CenterOnSelectedTrack)
        }
    }
    
    /**
     * Setup OSMDroid map for offline operation
     */
    private fun setupOSMDroid() {
        // Configure OSMDroid
        Configuration.getInstance().load(
            applicationContext,
            getSharedPreferences("osmdroid", MODE_PRIVATE)
        )
        
        // Get MapView reference from layout
        mapView = binding.mapView
        
        // Configure map
        mapView?.apply {
            // Set tile source (Mapnik = standard OpenStreetMap)
            setTileSource(TileSourceFactory.MAPNIK)
            
            // Enable multi-touch controls (pinch to zoom)
            setMultiTouchControls(true)
            
            // Set initial position
            controller.setZoom(DEFAULT_ZOOM)
            controller.setCenter(GeoPoint(DEFAULT_LAT, DEFAULT_LNG))
            
            // IMPORTANT: Enable offline mode for field deployment
            setUseDataConnection(false) // Set to true if you need online tiles
            
            // Add marker click handler overlay
            setupMarkerClickListener()
            
            // Add map tap listener for closing bottom sheet
            setupMapTapListener()
        }
    }
    
    /**
     * Setup marker click detection overlay
     */
    private fun setupMarkerClickListener() {
        mapView?.overlays?.add(object : Overlay() {
            override fun onSingleTapConfirmed(e: MotionEvent, mapView: MapView): Boolean {
                val projection = mapView.projection
                
                // Find closest marker within threshold
                var closestMarker: Marker? = null
                var minDistance = Float.MAX_VALUE
                
                mapView.overlays.filterIsInstance<Marker>().forEach { marker ->
                    val markerPoint = projection.toPixels(marker.position, null)
                    val distance = sqrt(
                        (markerPoint.x - e.x).pow(2) + (markerPoint.y - e.y).pow(2)
                    )
                    
                    if (distance < MARKER_CLICK_THRESHOLD && distance < minDistance) {
                        closestMarker = marker
                        minDistance = distance
                    }
                }
                
                // Handle marker click
                closestMarker?.let { marker ->
                    val trackId = marker.id
                    viewModel.onEvent(MainUiEvent.TrackSelected(trackId))
                    showTrackBottomSheet()
                    return true
                }
                
                return false
            }
        })
    }
    
    /**
     * Setup map tap listener for bottom sheet closing
     */
    @SuppressLint("ClickableViewAccessibility")
    private fun setupMapTapListener() {
        mapView?.setOnTouchListener { _, event ->
            if (event.action == MotionEvent.ACTION_UP) {
                if (bottomSheetBehavior.state != BottomSheetBehavior.STATE_HIDDEN) {
                    bottomSheetBehavior.state = BottomSheetBehavior.STATE_HIDDEN
                }
            }
            false // Allow other touch events
        }
    }
    
    /**
     * Setup drone camera callbacks
     */
    private fun setupDroneCamera() {
        viewModel.getDroneRepository().setCameraCallbacks(
            onReady = { codec ->
                runOnUiThread {
                    initializeCameraView()
                }
            },
            onDisconnected = {
                runOnUiThread {
                    cleanupCameraView()
                }
            }
        )
    }
    
    /**
     * Initialize camera view
     */
    private fun initializeCameraView() {
        cameraView = AutelCodecView(this).apply {
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )
        }
        updateViewContainers(viewModel.uiState.value)
    }
    
    /**
     * Cleanup camera view
     */
    private fun cleanupCameraView() {
        binding.cameraContainer.removeAllViews()
        binding.pipContentContainer.removeAllViews()
        cameraView = null
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
     * Observe center on track events
     */
    private fun observeCenterOnTrack() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.centerOnTrackEvent.collect { track ->
                    // OSMDroid: animate to position
                    mapView?.controller?.animateTo(GeoPoint(track.latitude, track.longitude))
                }
            }
        }
    }
    
    /**
     * Update UI based on state
     */
    private fun updateUI(state: MainUiState) {
        updateTopBar(state)
        updateStatusIcons(state)
        updateViewContainers(state)
        updatePipVisibility(state)
        updateShowPipButton(state)
        updateLockedTrackChip(state)
        updateDroneMarker(state.droneLocation)
        updateMapMarkers(state.tracks, state.lockedTrackId)
        updateBottomSheet(state)
        handleToast(state.toastMessage)
        handleError(state.errorMessage)
    }
    
    /**
     * Update top bar visibility
     */
    private fun updateTopBar(state: MainUiState) {
        if (state.isTopBarVisible) {
            binding.topBar.visibility = View.VISIBLE
            binding.topBarCollapsedTab.visibility = View.GONE
        } else {
            binding.topBar.visibility = View.GONE
            binding.topBarCollapsedTab.visibility = View.VISIBLE
        }
        
        // Update track count
        binding.trackCount.text = state.activeTracksCount.toString()
    }
    
    /**
     * Update status icons (radar and drone) based on connection state
     */
    private fun updateStatusIcons(state: MainUiState) {
        // Radar status icon
        binding.radarStatusIcon.alpha = when (state.radarState) {
            is ConnectionState.Connected -> ALPHA_CONNECTED
            else -> ALPHA_DISCONNECTED
        }
        binding.radarStatusIcon.contentDescription = getString(
            if (state.radarState.isConnected) R.string.status_radar_connected 
            else R.string.status_radar_disconnected
        )
        
        // Drone status icon
        binding.droneStatusIcon.alpha = when (state.droneState) {
            is ConnectionState.Connected -> ALPHA_CONNECTED
            else -> ALPHA_DISCONNECTED
        }
        binding.droneStatusIcon.contentDescription = getString(
            if (state.droneState.isConnected) R.string.status_drone_connected 
            else R.string.status_drone_disconnected
        )
    }
    
    /**
     * Update main and PiP view containers
     */
    private fun updateViewContainers(state: MainUiState) {
        when (state.mainView) {
            MainView.MAP -> {
                // Map is main view
                binding.mapContainer.visibility = View.VISIBLE
                binding.cameraContainer.visibility = View.GONE
                
                // Camera goes to PiP
                binding.pipContentContainer.removeAllViews()
                cameraView?.let { 
                    (it.parent as? ViewGroup)?.removeView(it)
                    binding.pipContentContainer.addView(it)
                }
            }
            MainView.CAMERA -> {
                // Camera is main view
                binding.mapContainer.visibility = View.GONE
                binding.cameraContainer.removeAllViews()
                cameraView?.let {
                    (it.parent as? ViewGroup)?.removeView(it)
                    binding.cameraContainer.addView(it)
                }
                binding.cameraContainer.visibility = View.VISIBLE
                
                // Hide PiP when camera is main
                binding.pipContentContainer.removeAllViews()
            }
        }
    }
    
    /**
     * Update PiP container visibility
     */
    private fun updatePipVisibility(state: MainUiState) {
        val shouldShowPip = state.isPipVisible && 
            state.isCameraAvailable && 
            state.mainView == MainView.MAP
        
        binding.pipContainer.visibility = if (shouldShowPip) View.VISIBLE else View.GONE
    }
    
    /**
     * Update show PiP FAB visibility
     */
    private fun updateShowPipButton(state: MainUiState) {
        val shouldShowButton = state.isDroneConnected && 
            !state.isPipVisible && 
            state.mainView == MainView.MAP
        
        if (shouldShowButton) {
            binding.btnShowPip.show()
            binding.btnShowPip.alpha = if (state.isCameraAvailable) 1.0f else 0.6f
        } else {
            binding.btnShowPip.hide()
        }
    }
    
    /**
     * Update locked track chip
     */
    private fun updateLockedTrackChip(state: MainUiState) {
        val lockedTrack = state.lockedTrack
        if (lockedTrack != null) {
            binding.lockedTrackChip.text = "Track ${lockedTrack.id}"
            binding.lockedTrackChip.visibility = View.VISIBLE
        } else {
            binding.lockedTrackChip.visibility = View.GONE
        }
    }
    
    /**
     * Update drone marker on map (OSMDroid version)
     */
    private fun updateDroneMarker(droneLocation: DroneLocationUi?) {
        mapView?.let { map ->
            if (droneLocation != null) {
                val position = GeoPoint(droneLocation.latitude, droneLocation.longitude)
                
                if (droneMarker == null) {
                    // Create new drone marker (green)
                    droneMarker = Marker(map).apply {
                        this.position = position
                        title = "Drone"
                        snippet = "Alt: ${String.format("%.1f", droneLocation.altitude)}m"
                        setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
                        // Use green tint for drone
                        icon = ContextCompat.getDrawable(this@MainActivity, R.drawable.ic_drone)?.apply {
                            setTint(Color.GREEN)
                        }
                        id = "drone"
                    }
                    map.overlays.add(droneMarker)
                } else {
                    // Update existing marker
                    droneMarker?.position = position
                    droneMarker?.snippet = "Alt: ${String.format("%.1f", droneLocation.altitude)}m"
                }
                map.invalidate()
            } else {
                // Remove marker if drone disconnected
                droneMarker?.let { map.overlays.remove(it) }
                droneMarker = null
                map.invalidate()
            }
        }
    }
    
    /**
     * Update map markers based on tracks (OSMDroid version)
     */
    private fun updateMapMarkers(tracks: List<TrackUiModel>, lockedTrackId: String?) {
        mapView?.let { map ->
            // Remove markers for tracks that no longer exist
            val currentTrackIds = tracks.map { it.id }.toSet()
            val markersToRemove = trackMarkers.keys.filter { it !in currentTrackIds }
            markersToRemove.forEach { id ->
                trackMarkers[id]?.let { map.overlays.remove(it) }
                trackMarkers.remove(id)
            }
            
            // Add or update markers
            tracks.forEach { trackUi ->
                val position = GeoPoint(trackUi.latitude, trackUi.longitude)
                val isLocked = trackUi.id == lockedTrackId
                
                val marker = trackMarkers[trackUi.id]
                if (marker != null) {
                    // Update existing marker
                    marker.position = position
                    marker.title = trackUi.displayTitle
                    // Update icon based on lock/stale state
                    marker.icon = createTrackMarkerIcon(trackUi.id, trackUi.isStale, isLocked)
                } else {
                    // Create new marker
                    val newMarker = Marker(map).apply {
                        this.position = position
                        title = trackUi.displayTitle
                        setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
                        icon = createTrackMarkerIcon(trackUi.id, trackUi.isStale, isLocked)
                        id = trackUi.id
                    }
                    map.overlays.add(newMarker)
                    trackMarkers[trackUi.id] = newMarker
                }
            }
            
            map.invalidate() // Must call to refresh display
        }
    }
    
    /**
     * Create marker icon for track
     */
    private fun createTrackMarkerIcon(trackId: String, isStale: Boolean, isLocked: Boolean): BitmapDrawable {
        // Use existing TrackMarkerHelper which creates bitmaps
        return TrackMarkerHelper.createSquareMarker(
            context = this,
            trackId = trackId,
            isStale = isStale,
            isLocked = isLocked
        ) as BitmapDrawable
    }
    
    /**
     * Update bottom sheet content
     */
    private fun updateBottomSheet(state: MainUiState) {
        val selectedTrack = state.selectedTrack
        
        if (selectedTrack != null) {
            val track = selectedTrack.track
            val isLocked = state.lockedTrackId == selectedTrack.id
            
            // Update title
            binding.trackInfoTitle.text = "Track ${selectedTrack.id}"
            
            // Calculate distance from drone if available
            val droneLocation = state.droneLocation
            val distanceText = if (droneLocation != null) {
                val distance = selectedTrack.distanceFrom(droneLocation.latitude, droneLocation.longitude)
                val distanceFormatted = when {
                    distance < 1000 -> "${distance.toInt()}m"
                    else -> String.format("%.1fkm", distance / 1000)
                }
                "  📏 $distanceFormatted from drone"
            } else {
                ""
            }
            
            // Update quick info with distance
            binding.trackInfoQuick.text = selectedTrack.quickInfo + distanceText
            
            // Update lock button icon
            binding.btnLockTrack.setIconResource(
                if (isLocked) R.drawable.ic_lock else R.drawable.ic_lock_open
            )
            
            // Update expanded content
            binding.trackInfoPosition.text = "${String.format("%.6f", track.geolocation.latitude)}°, ${String.format("%.6f", track.geolocation.longitude)}°"
            binding.trackInfoMovement.text = "Alt: ${String.format("%.0f", track.geolocation.altitude)}m  Speed: ${String.format("%.1f", track.geolocation.speed)}m/s  Heading: ${String.format("%.0f", track.geolocation.heading)}°"
            binding.trackInfoRadar.text = "Range: ${String.format("%.2f", track.observation.range)}m  Azimuth: ${String.format("%.1f", track.observation.azimuthAngle)}°"
            
            val topClassification = track.stats.classifications.maxByOrNull { it.confidence }
            binding.trackInfoClassification.text = "${topClassification?.type ?: "Unknown"} (${String.format("%.0f", (topClassification?.confidence ?: 0.0) * 100)}%)"
        }
    }
    
    /**
     * Show track bottom sheet
     */
    private fun showTrackBottomSheet() {
        bottomSheetBehavior.state = BottomSheetBehavior.STATE_COLLAPSED
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
        mapView?.onResume() // OSMDroid lifecycle
        viewModel.startRadarPolling()
    }
    
    override fun onPause() {
        super.onPause()
        mapView?.onPause() // OSMDroid lifecycle
        viewModel.stopRadarPolling()
    }
    
    override fun onDestroy() {
        super.onDestroy()
        mapView?.onDetach() // OSMDroid lifecycle
        cleanupCameraView()
        TrackMarkerHelper.clearCache()
    }
}
