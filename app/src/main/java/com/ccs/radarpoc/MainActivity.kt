package com.ccs.radarpoc

import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.*
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.autel.common.CallbackWithNoParam
import com.autel.common.CallbackWithOneParamProgress
import com.autel.common.error.AutelError
import com.autel.common.mission.AutelCoordinate3D
import com.autel.common.mission.MissionType
import com.autel.common.mission.evo.WaypointHeadingMode
import com.autel.common.mission.evo.WaypointType
import com.autel.common.mission.evo2.Evo2Waypoint
import com.autel.common.mission.evo2.Evo2WaypointFinishedAction
import com.autel.common.mission.evo2.Evo2WaypointMission
import com.autel.sdk.Autel
import com.autel.sdk.ProductConnectListener
import com.autel.sdk.product.BaseProduct
import com.autel.sdk.video.AutelCodec
import com.autel.sdk.widget.AutelCodecView
import com.ccs.radarpoc.data.AppSettings
import com.ccs.radarpoc.data.RadarTrack
import com.ccs.radarpoc.data.RadarTracksResponse
import com.ccs.radarpoc.network.RadarApiClient
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.OnMapReadyCallback
import com.google.android.gms.maps.SupportMapFragment
import com.google.android.gms.maps.model.*
import kotlinx.coroutines.launch
import java.util.UUID

class MainActivity : AppCompatActivity(), OnMapReadyCallback {
    
    private val TAG = "MainActivity"
    
    // View mode enum
    enum class ViewMode {
        MAP, CAMERA, SPLIT
    }
    
    // UI Components
    private lateinit var statusBar: LinearLayout
    private lateinit var radarStatus: TextView
    private lateinit var droneStatus: TextView
    private lateinit var cameraStatus: TextView
    private lateinit var settingsButton: ImageButton
    
    private lateinit var contentContainer: FrameLayout
    private lateinit var cameraContainer: FrameLayout
    private lateinit var pipCameraContainer: FrameLayout
    private lateinit var lockedTrackIndicator: TextView
    
    private lateinit var btnMapMode: Button
    private lateinit var btnCameraMode: Button
    private lateinit var btnSplitMode: Button
    
    // Google Maps
    private var googleMap: GoogleMap? = null
    private val trackMarkers = mutableMapOf<String, Marker>()
    
    // Drone SDK
    private var autelProduct: BaseProduct? = null
    private var autelCodec: AutelCodec? = null
    private var mainCameraView: AutelCodecView? = null
    private var pipCameraView: AutelCodecView? = null
    
    // Radar API
    private lateinit var appSettings: AppSettings
    private var radarApiClient: RadarApiClient? = null
    private val currentTracks = mutableMapOf<String, RadarTrack>()
    private var lockedTrack: RadarTrack? = null
    
    // State
    private var currentViewMode = ViewMode.MAP
    private var isRadarConnected = false
    private var isDroneConnected = false
    private var isCameraStreaming = false
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        
        appSettings = AppSettings(this)
        
        initViews()
        setupListeners()
        setupGoogleMaps()
        setupDroneSDK()
        startRadarPolling()
        
        // Set initial view mode
        setViewMode(ViewMode.MAP)
    }
    
    private fun initViews() {
        statusBar = findViewById(R.id.statusBar)
        radarStatus = findViewById(R.id.radarStatus)
        droneStatus = findViewById(R.id.droneStatus)
        cameraStatus = findViewById(R.id.cameraStatus)
        settingsButton = findViewById(R.id.settingsButton)
        
        contentContainer = findViewById(R.id.contentContainer)
        cameraContainer = findViewById(R.id.cameraContainer)
        pipCameraContainer = findViewById(R.id.pipCameraContainer)
        lockedTrackIndicator = findViewById(R.id.lockedTrackIndicator)
        
        btnMapMode = findViewById(R.id.btnMapMode)
        btnCameraMode = findViewById(R.id.btnCameraMode)
        btnSplitMode = findViewById(R.id.btnSplitMode)
    }
    
    private fun setupListeners() {
        settingsButton.setOnClickListener {
            startActivity(Intent(this, SettingsActivity::class.java))
        }
        
        btnMapMode.setOnClickListener {
            setViewMode(ViewMode.MAP)
        }
        
        btnCameraMode.setOnClickListener {
            setViewMode(ViewMode.CAMERA)
        }
        
        btnSplitMode.setOnClickListener {
            setViewMode(ViewMode.SPLIT)
        }
    }
    
    private fun setupGoogleMaps() {
        val mapFragment = supportFragmentManager
            .findFragmentById(R.id.mapFragment) as SupportMapFragment
        mapFragment.getMapAsync(this)
    }
    
    override fun onMapReady(map: GoogleMap) {
        googleMap = map
        
        // Set default location (Cairo, Egypt)
        val defaultLocation = LatLng(30.0444, 31.2357)
        googleMap?.moveCamera(CameraUpdateFactory.newLatLngZoom(defaultLocation, 15f))
        
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
    
    private fun setupDroneSDK() {
        // Register product connection listener
        Autel.setProductConnectListener(object : ProductConnectListener {
            override fun productConnected(product: BaseProduct?) {
                Log.d(TAG, "Drone connected")
                autelProduct = product
                isDroneConnected = true
                updateDroneStatus()
                
                // Get codec for camera feed
                autelCodec = product?.codec
                initializeCameraFeed()
            }
            
            override fun productDisconnected() {
                Log.d(TAG, "Drone disconnected")
                autelProduct = null
                autelCodec = null
                isDroneConnected = false
                isCameraStreaming = false
                updateDroneStatus()
                updateCameraStatus()
                cleanupCameraFeed()
            }
        })
    }
    
    private fun initializeCameraFeed() {
        runOnUiThread {
            if (autelCodec != null) {
                // Create main camera view
                mainCameraView = AutelCodecView(this).apply {
                    layoutParams = FrameLayout.LayoutParams(
                        FrameLayout.LayoutParams.MATCH_PARENT,
                        FrameLayout.LayoutParams.MATCH_PARENT
                    )
                }
                
                // Create PiP camera view
                pipCameraView = AutelCodecView(this).apply {
                    layoutParams = FrameLayout.LayoutParams(
                        FrameLayout.LayoutParams.MATCH_PARENT,
                        FrameLayout.LayoutParams.MATCH_PARENT
                    )
                }
                
                isCameraStreaming = true
                updateCameraStatus()
                
                // Update camera views based on current mode
                updateCameraViews()
            }
        }
    }
    
    private fun cleanupCameraFeed() {
        runOnUiThread {
            cameraContainer.removeAllViews()
            pipCameraContainer.removeAllViews()
            mainCameraView = null
            pipCameraView = null
        }
    }
    
    private fun updateCameraViews() {
        runOnUiThread {
            when (currentViewMode) {
                ViewMode.MAP -> {
                    cameraContainer.removeAllViews()
                    cameraContainer.visibility = View.GONE
                    pipCameraContainer.visibility = View.GONE
                }
                ViewMode.CAMERA -> {
                    pipCameraContainer.removeAllViews()
                    pipCameraContainer.visibility = View.GONE
                    cameraContainer.removeAllViews()
                    mainCameraView?.let { cameraContainer.addView(it) }
                    cameraContainer.visibility = View.VISIBLE
                }
                ViewMode.SPLIT -> {
                    cameraContainer.removeAllViews()
                    cameraContainer.visibility = View.GONE
                    pipCameraContainer.removeAllViews()
                    pipCameraView?.let { pipCameraContainer.addView(it) }
                    pipCameraContainer.visibility = View.VISIBLE
                }
            }
        }
    }
    
    private fun startRadarPolling() {
        radarApiClient?.stopPolling()
        
        radarApiClient = RadarApiClient(
            appSettings.radarBaseUrl,
            appSettings.pollInterval
        )
        
        radarApiClient?.setListener(object : RadarApiClient.RadarDataListener {
            override fun onTracksReceived(tracks: RadarTracksResponse) {
                processRadarTracks(tracks.result)
            }
            
            override fun onError(error: String) {
                Log.e(TAG, "Radar error: $error")
            }
            
            override fun onConnectionStatusChanged(isConnected: Boolean) {
                isRadarConnected = isConnected
                updateRadarStatus()
            }
        })
        
        radarApiClient?.startPolling(lifecycleScope)
    }
    
    private fun processRadarTracks(tracks: List<RadarTrack>) {
        val now = System.currentTimeMillis()
        val staleTimeoutMs = appSettings.staleTimeout * 1000L
        
        // Update stale status
        tracks.forEach { track ->
            track.isStale = (now - track.timestamp) > staleTimeoutMs
            currentTracks[track.id] = track
        }
        
        // Remove tracks not in the latest update
        val trackIds = tracks.map { it.id }.toSet()
        val toRemove = currentTracks.keys.filter { it !in trackIds }
        toRemove.forEach { currentTracks.remove(it) }
        
        // Update map markers
        updateMapMarkers()
        
        // Send locked track GPS to drone
        lockedTrack?.let { track ->
            if (track.id in currentTracks) {
                val updatedTrack = currentTracks[track.id]!!
                sendGPSToDrone(updatedTrack)
            }
        }
    }
    
    private fun updateMapMarkers() {
        runOnUiThread {
            googleMap?.let { map ->
                // Remove markers for tracks that no longer exist
                val currentTrackIds = currentTracks.keys
                val markersToRemove = trackMarkers.keys.filter { it !in currentTrackIds }
                markersToRemove.forEach { id ->
                    trackMarkers[id]?.remove()
                    trackMarkers.remove(id)
                }
                
                // Add or update markers
                currentTracks.values.forEach { track ->
                    val position = LatLng(track.geolocation.latitude, track.geolocation.longitude)
                    
                    val markerColor = when {
                        track.isLocked -> BitmapDescriptorFactory.HUE_ORANGE
                        track.isStale -> BitmapDescriptorFactory.HUE_AZURE
                        else -> BitmapDescriptorFactory.HUE_BLUE
                    }
                    
                    val marker = trackMarkers[track.id]
                    if (marker != null) {
                        // Update existing marker
                        marker.position = position
                        marker.setIcon(BitmapDescriptorFactory.defaultMarker(markerColor))
                        marker.title = "Track ${track.id}${if (track.isLocked) " [LOCKED]" else ""}${if (track.isStale) " (Stale)" else ""}"
                    } else {
                        // Create new marker
                        val newMarker = map.addMarker(
                            MarkerOptions()
                                .position(position)
                                .title("Track ${track.id}${if (track.isLocked) " [LOCKED]" else ""}${if (track.isStale) " (Stale)" else ""}")
                                .icon(BitmapDescriptorFactory.defaultMarker(markerColor))
                        )
                        newMarker?.tag = track.id
                        if (newMarker != null) {
                            trackMarkers[track.id] = newMarker
                        }
                    }
                }
            }
        }
    }
    
    private fun showTrackContextMenu(trackId: String) {
        val track = currentTracks[trackId] ?: return
        
        val options = if (track.isLocked) {
            arrayOf("Show Info", "Unlock")
        } else {
            arrayOf("Show Info", "Lock")
        }
        
        AlertDialog.Builder(this)
            .setTitle("Track ${track.id}")
            .setItems(options) { _, which ->
                when (options[which]) {
                    "Show Info" -> showTrackInfo(track)
                    "Lock" -> lockTrack(track)
                    "Unlock" -> unlockTrack()
                }
            }
            .show()
    }
    
    private fun showTrackInfo(track: RadarTrack) {
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
            
            Status: ${if (track.isStale) "STALE" else "Active"}
        """.trimIndent()
        
        AlertDialog.Builder(this)
            .setTitle("Track ${track.id} Info")
            .setMessage(info)
            .setPositiveButton("OK", null)
            .show()
    }
    
    private fun lockTrack(track: RadarTrack) {
        // Unlock previous track if any
        lockedTrack?.isLocked = false
        
        // Lock new track
        track.isLocked = true
        lockedTrack = track
        
        // Update UI
        updateMapMarkers()
        updateLockedTrackIndicator()
        
        Toast.makeText(this, "Locked Track ${track.id}", Toast.LENGTH_SHORT).show()
    }
    
    private fun unlockTrack() {
        lockedTrack?.isLocked = false
        lockedTrack = null
        
        // Update UI
        updateMapMarkers()
        updateLockedTrackIndicator()
        
        Toast.makeText(this, "Track unlocked", Toast.LENGTH_SHORT).show()
    }
    
    private fun updateLockedTrackIndicator() {
        runOnUiThread {
            if (lockedTrack != null) {
                lockedTrackIndicator.text = "Locked: Track ${lockedTrack?.id}"
                lockedTrackIndicator.visibility = if (currentViewMode == ViewMode.CAMERA) View.VISIBLE else View.GONE
            } else {
                lockedTrackIndicator.visibility = View.GONE
            }
        }
    }
    
    private fun sendGPSToDrone(track: RadarTrack) {
        if (!isDroneConnected || autelProduct == null) return
        
        try {
            // Get the mission manager
            val missionManager = autelProduct?.getMissionManager()
            if (missionManager == null) {
                Log.e(TAG, "Mission manager not available")
                return
            }
            
            // Create a simple mission with single waypoint to track the target
            val mission = Evo2WaypointMission()
            mission.missionId = 1
            mission.missionType = MissionType.Waypoint
            mission.altitudeType = 1 // 0=relative, 1=absolute altitude
            mission.MissionName = "RadarTrack_${track.id}"
            mission.GUID = UUID.randomUUID().toString().replace("-", "")
            mission.missionAction = 0 // Normal flight
            
            // Create waypoint at track location
            val waypoint = Evo2Waypoint(
                AutelCoordinate3D(
                    track.geolocation.latitude,
                    track.geolocation.longitude,
                    track.geolocation.altitude
                )
            )
            waypoint.wSpeed = 5.0f // Speed in m/s
            waypoint.poiIndex = -1 // No POI
            waypoint.hoverTime = 5 // Hover for 5 seconds at target
            waypoint.headingMode = WaypointHeadingMode.CUSTOM_DIRECTION
            waypoint.waypointType = WaypointType.HOVER
            waypoint.actions = emptyList()
            
            mission.wpList = listOf(waypoint)
            mission.finishedAction = Evo2WaypointFinishedAction.RETURN_HOME
            
            // Prepare and start mission
            missionManager.prepareMission(mission, object : CallbackWithOneParamProgress<Boolean> {
                override fun onProgress(progress: Float) {
                    Log.d(TAG, "Mission preparation progress: $progress")
                }
                
                override fun onSuccess(result: Boolean?) {
                    Log.d(TAG, "Mission prepared successfully, starting mission")
                    
                    // Start the mission
                    missionManager.startMission(object : CallbackWithNoParam {
                        override fun onSuccess() {
                            Log.d(TAG, "GPS waypoint mission started: ${track.geolocation.latitude}, ${track.geolocation.longitude}, alt: ${track.geolocation.altitude}")
                            runOnUiThread {
                                Toast.makeText(this@MainActivity, "Drone tracking Track ${track.id}", Toast.LENGTH_SHORT).show()
                            }
                        }
                        
                        override fun onFailure(error: AutelError?) {
                            Log.e(TAG, "Failed to start mission: ${error?.description}")
                        }
                    })
                }
                
                override fun onFailure(error: AutelError?) {
                    Log.e(TAG, "Failed to prepare mission: ${error?.description}")
                }
            })
            
        } catch (e: Exception) {
            Log.e(TAG, "Error sending GPS to drone: ${e.message}", e)
        }
    }
    
    private fun setViewMode(mode: ViewMode) {
        currentViewMode = mode
        
        // Update button states
        btnMapMode.setBackgroundColor(if (mode == ViewMode.MAP) Color.LTGRAY else Color.TRANSPARENT)
        btnCameraMode.setBackgroundColor(if (mode == ViewMode.CAMERA) Color.LTGRAY else Color.TRANSPARENT)
        btnSplitMode.setBackgroundColor(if (mode == ViewMode.SPLIT) Color.LTGRAY else Color.TRANSPARENT)
        
        // Update views
        updateCameraViews()
        updateLockedTrackIndicator()
    }
    
    private fun updateRadarStatus() {
        runOnUiThread {
            radarStatus.text = if (isRadarConnected) {
                "🟢 Radar: Connected"
            } else {
                "🔴 Radar: Disconnected"
            }
        }
    }
    
    private fun updateDroneStatus() {
        runOnUiThread {
            droneStatus.text = if (isDroneConnected) {
                "🟢 Drone: Connected"
            } else {
                "🔴 Drone: Disconnected"
            }
        }
    }
    
    private fun updateCameraStatus() {
        runOnUiThread {
            cameraStatus.text = when {
                isCameraStreaming -> "🟢 Camera: Streaming"
                else -> "🔴 Camera: Off"
            }
        }
    }
    
    override fun onResume() {
        super.onResume()
        // Restart polling with updated settings
        startRadarPolling()
    }
    
    override fun onPause() {
        super.onPause()
        radarApiClient?.stopPolling()
    }
    
    override fun onDestroy() {
        super.onDestroy()
        radarApiClient?.stopPolling()
        cleanupCameraFeed()
        autelCodec?.cancel()
    }
}
