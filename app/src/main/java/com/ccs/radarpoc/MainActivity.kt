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
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.Toast
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.GestureDetectorCompat
import androidx.core.view.ViewCompat
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
import com.ccs.radarpoc.util.MapFileManager
import com.ccs.radarpoc.util.MapTileProviderFactory
import com.google.android.material.bottomsheet.BottomSheetBehavior
import org.osmdroid.config.Configuration
import org.osmdroid.events.MapListener
import org.osmdroid.events.ScrollEvent
import org.osmdroid.events.ZoomEvent
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.CustomZoomButtonsController
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker
import org.osmdroid.views.overlay.Overlay
import org.osmdroid.views.overlay.ScaleBarOverlay
import org.osmdroid.views.overlay.gestures.RotationGestureOverlay
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.pow
import kotlin.math.sqrt

/**
 * Main Activity - Material Design 3 UI with draggable PiP
 * Using OSMDroid for full offline map support with multiple MBTiles files
 * Runs in immersive fullscreen mode for maximum map visibility
 */
class MainActivity : AppCompatActivity() {
    
    companion object {
        private const val TAG = "MainActivity"
        private const val DEFAULT_ZOOM = 15.0
        private const val DEFAULT_LAT = 30.0444  // Cairo, Egypt
        private const val DEFAULT_LNG = 31.2357
        private const val PIP_MARGIN = 16
        private const val MARKER_CLICK_THRESHOLD = 50f // pixels
        private const val ZOOM_ANIMATION_DURATION = 250L
        
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
    
    // Map overlays
    private var scaleBarOverlay: ScaleBarOverlay? = null
    private var rotationGestureOverlay: RotationGestureOverlay? = null
    
    // Current compass rotation for animation
    private var currentCompassRotation = 0f
    
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
        
        // Enable edge-to-edge display
        enableEdgeToEdge()
        
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        
        // Apply system bars insets to avoid UI overlap
        applySystemBarsInsets()
        
        // Enable immersive fullscreen mode
        hideSystemUI()
        
        setupBottomSheet()
        setupPipGestures()
        setupClickListeners()
        setupMapControls()
        setupOSMDroid()
        setupDroneCamera()
        observeState()
        observeNavigation()
        observeCenterOnTrack()
    }
    
    /**
     * Enable edge-to-edge display (draw behind system bars)
     */
    private fun enableEdgeToEdge() {
        // Make the app draw behind system bars
        WindowCompat.setDecorFitsSystemWindows(window, false)
        
        // Make system bars transparent
        window.statusBarColor = Color.TRANSPARENT
        window.navigationBarColor = Color.TRANSPARENT
    }
    
    /**
     * Apply insets so UI elements don't overlap with system bars when they're visible
     */
    private fun applySystemBarsInsets() {
        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { view, windowInsets ->
            val insets = windowInsets.getInsets(WindowInsetsCompat.Type.systemBars())
            
            // Apply padding to top bar so it doesn't overlap with status bar
            binding.topBar.setPadding(
                binding.topBar.paddingLeft,
                insets.top + 8,
                binding.topBar.paddingRight,
                binding.topBar.paddingBottom
            )
            
            // Apply margin to map controls so they don't overlap with navigation bar
            val mapControlsParams = binding.mapControlsContainer.layoutParams as ViewGroup.MarginLayoutParams
            mapControlsParams.rightMargin = insets.right + 12
            binding.mapControlsContainer.layoutParams = mapControlsParams
            
            // Apply padding to bottom controls
            binding.bottomControlsContainer.setPadding(
                insets.left + 16,
                binding.bottomControlsContainer.paddingTop,
                insets.right + 16,
                insets.bottom + 16
            )
            
            windowInsets
        }
    }
    
    /**
     * Hide system UI (status bar and navigation bar) for immersive fullscreen
     */
    private fun hideSystemUI() {
        val windowInsetsController = WindowCompat.getInsetsController(window, window.decorView)
        
        windowInsetsController.apply {
            // Hide both the status bar and the navigation bar
            hide(WindowInsetsCompat.Type.systemBars())
            
            // Behavior: show bars with swipe gesture, auto-hide after delay
            systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }
        
        // Keep screen on while app is active
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
    }
    
    /**
     * Show system UI temporarily
     */
    private fun showSystemUI() {
        val windowInsetsController = WindowCompat.getInsetsController(window, window.decorView)
        windowInsetsController.show(WindowInsetsCompat.Type.systemBars())
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
     * Setup custom map controls (zoom buttons, compass)
     */
    private fun setupMapControls() {
        // Zoom In button
        binding.btnZoomIn.setOnClickListener {
            mapView?.controller?.zoomIn(ZOOM_ANIMATION_DURATION)
        }
        
        // Zoom Out button
        binding.btnZoomOut.setOnClickListener {
            mapView?.controller?.zoomOut(ZOOM_ANIMATION_DURATION)
        }
        
        // Compass button - reset to north
        binding.btnCompass.setOnClickListener {
            resetMapToNorth()
        }
    }
    
    /**
     * Reset map orientation to north with animation
     */
    private fun resetMapToNorth() {
        mapView?.let { map ->
            // Animate map rotation to 0 (north)
            map.mapOrientation = 0f
            
            // Reset compass icon rotation (smooth transition)
            binding.btnCompass.animate()
                .rotation(0f)
                .setDuration(300)
                .withEndAction {
                    // Ensure it's set after animation
                    binding.btnCompass.rotation = 0f
                    currentCompassRotation = 0f
                }
                .start()
            
            Toast.makeText(this, "Map reset to North", Toast.LENGTH_SHORT).show()
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
     * Setup OSMDroid map with all optimizations:
     * - Multiple MBTiles support
     * - Smooth zoom/scroll
     * - Overzoom support
     * - Scale bar
     */
    private fun setupOSMDroid() {
        // Configure OSMDroid - MUST be done before using the map
        Configuration.getInstance().apply {
            load(applicationContext, getSharedPreferences("osmdroid", MODE_PRIVATE))
            
            // Set user agent to avoid being blocked
            userAgentValue = packageName
            
            // Enable debug logging (disable in production)
            isDebugMode = false
            isDebugMapView = false
            isDebugTileProviders = false
            
            // Cache settings for better performance
            tileFileSystemCacheMaxBytes = 500L * 1024 * 1024  // 500MB cache
            tileFileSystemCacheTrimBytes = 400L * 1024 * 1024 // Trim to 400MB
            
            // Tile download settings
            tileDownloadThreads = 4
            tileFileSystemThreads = 4
            
            // Expiration settings
            expirationOverrideDuration = 1000L * 60 * 60 * 24 * 30 // 30 days
        }
        
        // Get MapView reference from layout
        mapView = binding.mapView
        
        // Configure map with all optimizations
        mapView?.apply {
            // ========================================
            // TILE PROVIDER SETUP - Load ALL MBTiles
            // ========================================
            val mapFiles = MapFileManager.scanForMapFiles(this@MainActivity)
            
            if (mapFiles.isNotEmpty()) {
                android.util.Log.d(TAG, "Found ${mapFiles.size} offline map files")
                
                // Create multi-archive provider
                val result = MapTileProviderFactory.createMultiArchiveProvider(
                    this@MainActivity,
                    mapFiles
                )
                
                if (result.provider != null) {
                    // Use offline tiles from ALL MBTiles files
                    setTileProvider(result.provider)
                    setTileSource(result.tileSource)
                    setUseDataConnection(false) // Pure offline mode
                    
                    // ========================================
                    // AUTO-DETECT ZOOM LIMITS FROM LOADED MAPS
                    // ========================================
                    if (result.loadedFiles.isNotEmpty()) {
                        // Get min/max zoom from all loaded files
                        val minZoom = result.loadedFiles.minOf { it.minZoom }
                        val maxZoom = result.loadedFiles.maxOf { it.maxZoom }
                        
                        // Apply zoom limits to prevent blank tiles
                        minZoomLevel = minZoom.toDouble()
                        maxZoomLevel = maxZoom.toDouble()
                        
                        android.util.Log.d(TAG, "✓ Auto-detected zoom limits: $minZoom-$maxZoom")
                    }
                    
                    // Show summary toast
                    val summary = MapTileProviderFactory.getLoadedMapsSummary(result.loadedFiles)
                    Toast.makeText(
                        this@MainActivity,
                        "✓ Offline: $summary",
                        Toast.LENGTH_SHORT
                    ).show()
                    
                    android.util.Log.d(TAG, "Loaded offline maps: $summary")
                    
                    // Log any errors
                    result.errors.forEach { error ->
                        android.util.Log.w(TAG, "Map loading issue: $error")
                    }
                } else {
                    // Fallback to online if no valid archives
                    android.util.Log.w(TAG, "No valid offline maps, falling back to online")
                    setupOnlineTiles()
                }
            } else {
                // No offline maps found, use online
                android.util.Log.d(TAG, "No offline maps found, using online tiles")
                setupOnlineTiles()
            }
            
            // ========================================
            // SMOOTH ZOOM AND SCROLL SETTINGS
            // ========================================
            
            // Enable smooth zooming
            isTilesScaledToDpi = true
            
            // Enable fling (momentum) scrolling
            isFlingEnabled = true
            
            // Enable multi-touch controls (pinch to zoom)
            setMultiTouchControls(true)
            
            // Set default zoom levels (will be overridden by auto-detect if offline maps loaded)
            if (mapFiles.isEmpty()) {
                minZoomLevel = 3.0
                maxZoomLevel = 22.0
            }
            
            // DISABLE built-in zoom controls (we use custom ones)
            zoomController.setVisibility(CustomZoomButtonsController.Visibility.NEVER)
            
            // Default: keep north-up initially
            setMapOrientation(0f)
            
            // Enable hardware acceleration for smoother rendering
            setLayerType(View.LAYER_TYPE_HARDWARE, null)
            
            // ========================================
            // MAP OVERLAYS
            // ========================================
            
            // Add scale bar (bottom left)
            scaleBarOverlay = ScaleBarOverlay(this).apply {
                setCentred(false)
                setAlignBottom(true)
                setAlignRight(false)
                setScaleBarOffset(50, 80)  // Offset from edge
            }
            overlays.add(scaleBarOverlay)
            
            // ========================================
            // INITIAL POSITION
            // ========================================
            
            // Set initial position (Cairo, Egypt)
            controller.setZoom(DEFAULT_ZOOM)
            controller.setCenter(GeoPoint(DEFAULT_LAT, DEFAULT_LNG))
            
            // ========================================
            // INTERACTION HANDLERS
            // ========================================
            
            // Add marker click handler overlay
            setupMarkerClickListener()
            
            // Add map tap listener for closing bottom sheet
            setupMapTapListener()
            
            // Force initial render
            invalidate()
        }
        
        android.util.Log.d(TAG, "OSMDroid setup complete with all optimizations")
    }
    
    /**
     * Setup online tiles as fallback
     */
    private fun MapView.setupOnlineTiles() {
        setTileSource(TileSourceFactory.MAPNIK)
        setUseDataConnection(true)
        
        Toast.makeText(
            this@MainActivity,
            "Using online maps. Add offline maps in Settings.",
            Toast.LENGTH_LONG
        ).show()
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
                    // OSMDroid: animate to position smoothly
                    mapView?.controller?.animateTo(
                        GeoPoint(track.latitude, track.longitude),
                        18.0,  // Zoom level
                        1000L  // Animation duration in ms
                    )
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
        updateMapControlsVisibility(state)
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
     * Update map controls visibility based on current view
     */
    private fun updateMapControlsVisibility(state: MainUiState) {
        // Show map controls only when map is the main view
        binding.mapControlsContainer.visibility = 
            if (state.mainView == MainView.MAP) View.VISIBLE else View.GONE
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
        
        // Re-enable immersive mode when returning to activity
        hideSystemUI()
        
        // Apply map settings (rotation, compass, scale bar, zoom buttons)
        applyMapSettings()
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
    
    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) {
            // Re-enable immersive mode when window gains focus
            hideSystemUI()
        }
    }
    
    /**
     * Apply map display settings from preferences
     */
    private fun applyMapSettings() {
        val settings = AppSettings(this)
        
        mapView?.let { map ->
            // ========================================
            // COMPASS VISIBILITY
            // ========================================
            binding.btnCompass.visibility = 
                if (settings.showCompass) View.VISIBLE else View.GONE
            
            // ========================================
            // ZOOM BUTTONS VISIBILITY
            // ========================================
            val zoomButtonsVisible = if (settings.showZoomButtons) View.VISIBLE else View.GONE
            binding.btnZoomIn.visibility = zoomButtonsVisible
            binding.btnZoomOut.visibility = zoomButtonsVisible
            
            // ========================================
            // SCALE BAR VISIBILITY
            // ========================================
            scaleBarOverlay?.isEnabled = settings.showScaleBar
            
            // ========================================
            // MAP ROTATION
            // ========================================
            if (settings.enableMapRotation) {
                // Enable rotation with RotationGestureOverlay
                if (rotationGestureOverlay == null) {
                    rotationGestureOverlay = RotationGestureOverlay(map).apply {
                        isEnabled = true
                    }
                    map.overlays.add(rotationGestureOverlay)
                    android.util.Log.d(TAG, "✓ Map rotation ENABLED - use two-finger twist gesture")
                }
                rotationGestureOverlay?.isEnabled = true
                
                // Add listener to update compass icon rotation
                map.addMapListener(object : MapListener {
                    override fun onScroll(event: ScrollEvent?): Boolean {
                        // Update compass rotation when map scrolls
                        updateCompassRotation(map.mapOrientation)
                        return true
                    }
                    
                    override fun onZoom(event: ZoomEvent?): Boolean {
                        return true
                    }
                })
                
                // Make compass button interactive
                binding.btnCompass.alpha = 1.0f
                binding.btnCompass.isEnabled = true
            } else {
                // Disable rotation - remove overlay and reset to north
                rotationGestureOverlay?.let { overlay ->
                    overlay.isEnabled = false
                    map.overlays.remove(overlay)
                }
                rotationGestureOverlay = null
                
                // Reset map to north
                map.mapOrientation = 0f
                binding.btnCompass.rotation = 0f
                currentCompassRotation = 0f
                
                // Make compass static (grayed out)
                binding.btnCompass.alpha = 0.5f
                binding.btnCompass.isEnabled = false
                
                android.util.Log.d(TAG, "✗ Map rotation DISABLED - locked to North")
            }
            
            // Refresh map
            map.invalidate()
        }
    }
    
    /**
     * Update compass icon rotation to match map orientation
     */
    private fun updateCompassRotation(mapOrientation: Float) {
        if (mapOrientation != currentCompassRotation) {
            // Rotate compass icon opposite to map rotation
            // so it always points north
            binding.btnCompass.rotation = -mapOrientation
            currentCompassRotation = mapOrientation
        }
    }
}
