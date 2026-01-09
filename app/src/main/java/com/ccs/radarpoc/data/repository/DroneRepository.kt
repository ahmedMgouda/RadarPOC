package com.ccs.radarpoc.data.repository

import android.util.Log
import com.autel.common.CallbackWithNoParam
import com.autel.common.CallbackWithOneParam
import com.autel.common.CallbackWithOneParamProgress
import com.autel.common.error.AutelError
import com.autel.common.flycontroller.evo.EvoFlyControllerInfo
import com.autel.common.mission.AutelCoordinate3D
import com.autel.common.mission.MissionType
import com.autel.common.mission.evo.WaypointHeadingMode
import com.autel.common.mission.evo.WaypointType
import com.autel.common.mission.evo2.Evo2Waypoint
import com.autel.common.mission.evo2.Evo2WaypointFinishedAction
import com.autel.common.mission.evo2.Evo2WaypointMission
import com.autel.sdk.Autel
import com.autel.sdk.ProductConnectListener
import com.autel.sdk.flycontroller.Evo2FlyController
import com.autel.sdk.product.BaseProduct
import com.autel.sdk.product.Evo2Aircraft
import com.autel.sdk.video.AutelCodec
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.UUID

/**
 * Data class representing GPS coordinates for drone navigation
 */
data class DroneGpsTarget(
    val latitude: Double,
    val longitude: Double,
    val altitude: Double,
    val trackId: String
)

/**
 * Result of a drone operation
 */
sealed class DroneOperationResult {
    object Success : DroneOperationResult()
    data class Error(val message: String) : DroneOperationResult()
    object NotConnected : DroneOperationResult()
}

/**
 * Repository for drone operations
 * Handles connection, camera feed, and GPS commands
 */
class DroneRepository(
    private val missionUpdateIntervalSeconds: Int = 3
) {
    companion object {
        private const val TAG = "DroneRepository"
        private const val DEFAULT_DRONE_SPEED = 5.0f
        private const val DEFAULT_HOVER_TIME = 5
    }
    
    // Mission tracking for optimization
    private var currentMissionId: String? = null
    private var lastMissionUpdateTime = 0L
    private var lastSentTarget: DroneGpsTarget? = null
    
    // Connection state
    private val _isConnected = MutableStateFlow(false)
    val isConnected: StateFlow<Boolean> = _isConnected.asStateFlow()
    
    // Camera state
    private val _isCameraStreaming = MutableStateFlow(false)
    val isCameraStreaming: StateFlow<Boolean> = _isCameraStreaming.asStateFlow()
    
    // Drone GPS location
    data class DroneLocation(
        val latitude: Double,
        val longitude: Double,
        val altitude: Double
    )
    
    private val _droneLocation = MutableStateFlow<DroneLocation?>(null)
    val droneLocation: StateFlow<DroneLocation?> = _droneLocation.asStateFlow()
    
    // Product reference
    private var autelProduct: BaseProduct? = null
    private var autelCodec: AutelCodec? = null
    
    // Callbacks for UI layer
    private var onCameraReady: ((AutelCodec) -> Unit)? = null
    private var onCameraDisconnected: (() -> Unit)? = null
    
    /**
     * Initialize drone SDK and register connection listener
     */
    fun initialize() {
        Autel.setProductConnectListener(object : ProductConnectListener {
            override fun productConnected(product: BaseProduct?) {
                Log.d(TAG, "Drone connected")
                autelProduct = product
                autelCodec = product?.codec
                _isConnected.value = true
                
                autelCodec?.let { codec ->
                    _isCameraStreaming.value = true
                    onCameraReady?.invoke(codec)
                }
                
                // Start tracking drone GPS location
                startDroneGpsTracking(product)
            }
            
            override fun productDisconnected() {
                Log.d(TAG, "Drone disconnected")
                autelProduct = null
                autelCodec = null
                _isConnected.value = false
                _isCameraStreaming.value = false
                onCameraDisconnected?.invoke()
            }
        })
    }
    
    /**
     * Set camera callbacks for UI layer
     */
    fun setCameraCallbacks(
        onReady: (AutelCodec) -> Unit,
        onDisconnected: () -> Unit
    ) {
        onCameraReady = onReady
        onCameraDisconnected = onDisconnected
        
        // If already connected, invoke callback immediately
        autelCodec?.let { codec ->
            if (_isConnected.value) {
                onReady(codec)
            }
        }
    }
    
    /**
     * Get the current codec for camera feed
     */
    fun getCodec(): AutelCodec? = autelCodec
    
    /**
     * Send GPS coordinates to drone for tracking
     * Optimized with rate limiting and mission cancellation
     */
    fun sendGpsTarget(
        target: DroneGpsTarget,
        onSuccess: () -> Unit = {},
        onError: (String) -> Unit = {}
    ): DroneOperationResult {
        if (!_isConnected.value || autelProduct == null) {
            return DroneOperationResult.NotConnected
        }
        
        // Rate limiting: Check if enough time has passed since last update
        val currentTime = System.currentTimeMillis()
        val timeSinceLastUpdate = currentTime - lastMissionUpdateTime
        val minUpdateIntervalMs = missionUpdateIntervalSeconds * 1000L
        
        if (timeSinceLastUpdate < minUpdateIntervalMs && lastSentTarget != null) {
            Log.d(TAG, "Rate limit: Skipping mission update (${timeSinceLastUpdate}ms < ${minUpdateIntervalMs}ms)")
            return DroneOperationResult.Success // Silent skip
        }
        
        // Check if target location changed significantly (optional optimization)
        lastSentTarget?.let { last ->
            val distance = calculateDistance(
                last.latitude, last.longitude,
                target.latitude, target.longitude
            )
            if (distance < 5.0) { // Less than 5 meters movement
                Log.d(TAG, "Target barely moved (${distance}m), skipping update")
                return DroneOperationResult.Success
            }
        }
        
        try {
            val missionManager = autelProduct?.getMissionManager()
            if (missionManager == null) {
                val error = "Mission manager not available"
                Log.e(TAG, error)
                onError(error)
                return DroneOperationResult.Error(error)
            }
            
            // Cancel any existing mission before starting new one
            if (currentMissionId != null) {
                Log.d(TAG, "Canceling previous mission: $currentMissionId")
                missionManager.cancelMission(object : CallbackWithNoParam {
                    override fun onSuccess() {
                        Log.d(TAG, "Previous mission canceled successfully")
                        startNewMission(target, missionManager, onSuccess, onError)
                    }
                    
                    override fun onFailure(error: AutelError?) {
                        Log.w(TAG, "Failed to cancel previous mission: ${error?.description}, starting new mission anyway")
                        startNewMission(target, missionManager, onSuccess, onError)
                    }
                })
            } else {
                // No previous mission, start directly
                startNewMission(target, missionManager, onSuccess, onError)
            }
            
            // Update tracking variables
            lastMissionUpdateTime = currentTime
            lastSentTarget = target
            
            return DroneOperationResult.Success
            
        } catch (e: Exception) {
            val errorMsg = "Error sending GPS to drone: ${e.message}"
            Log.e(TAG, errorMsg, e)
            onError(errorMsg)
            return DroneOperationResult.Error(errorMsg)
        }
    }
    
    /**
     * Start a new waypoint mission
     */
    private fun startNewMission(
        target: DroneGpsTarget,
        missionManager: com.autel.sdk.mission.MissionManager,
        onSuccess: () -> Unit,
        onError: (String) -> Unit
    ) {
        val mission = createWaypointMission(target)
        currentMissionId = mission.GUID
        
        Log.d(TAG, "Preparing new mission: $currentMissionId for track ${target.trackId}")
        
        missionManager.prepareMission(mission, object : CallbackWithOneParamProgress<Boolean> {
            override fun onProgress(progress: Float) {
                Log.d(TAG, "Mission preparation progress: $progress")
            }
            
            override fun onSuccess(result: Boolean?) {
                Log.d(TAG, "Mission prepared successfully, starting mission")
                
                missionManager.startMission(object : CallbackWithNoParam {
                    override fun onSuccess() {
                        Log.d(TAG, "GPS waypoint mission started: ${target.latitude}, ${target.longitude}, alt: ${target.altitude}")
                        onSuccess()
                    }
                    
                    override fun onFailure(error: AutelError?) {
                        val errorMsg = "Failed to start mission: ${error?.description}"
                        Log.e(TAG, errorMsg)
                        currentMissionId = null // Clear failed mission
                        onError(errorMsg)
                    }
                })
            }
            
            override fun onFailure(error: AutelError?) {
                val errorMsg = "Failed to prepare mission: ${error?.description}"
                Log.e(TAG, errorMsg)
                currentMissionId = null // Clear failed mission
                onError(errorMsg)
            }
        })
    }
    
    /**
     * Calculate distance between two GPS coordinates (simple approximation)
     */
    private fun calculateDistance(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val R = 6371000.0 // Earth radius in meters
        val dLat = Math.toRadians(lat2 - lat1)
        val dLon = Math.toRadians(lon2 - lon1)
        
        val a = kotlin.math.sin(dLat / 2).pow(2.0) +
                kotlin.math.cos(Math.toRadians(lat1)) * kotlin.math.cos(Math.toRadians(lat2)) *
                kotlin.math.sin(dLon / 2).pow(2.0)
        
        val c = 2 * kotlin.math.atan2(kotlin.math.sqrt(a), kotlin.math.sqrt(1 - a))
        return R * c
    }
    
    /**
     * Create a waypoint mission for the target
     */
    private fun createWaypointMission(target: DroneGpsTarget): Evo2WaypointMission {
        val mission = Evo2WaypointMission().apply {
            missionId = 1
            missionType = MissionType.Waypoint
            altitudeType = 1 // 0=relative, 1=absolute altitude
            MissionName = "RadarTrack_${target.trackId}"
            GUID = UUID.randomUUID().toString().replace("-", "")
            missionAction = 0 // Normal flight
        }
        
        val waypoint = Evo2Waypoint(
            AutelCoordinate3D(
                target.latitude,
                target.longitude,
                target.altitude
            )
        ).apply {
            wSpeed = DEFAULT_DRONE_SPEED
            poiIndex = -1
            hoverTime = DEFAULT_HOVER_TIME
            headingMode = WaypointHeadingMode.CUSTOM_DIRECTION
            waypointType = WaypointType.HOVER
            actions = emptyList()
        }
        
        mission.wpList = listOf(waypoint)
        mission.finishedAction = Evo2WaypointFinishedAction.RETURN_HOME
        
        return mission
    }
    
    /**
     * Start tracking drone GPS location
     */
    private fun startDroneGpsTracking(product: BaseProduct?) {
        val flyController = (product as? Evo2Aircraft)?.getFlyController()
        
        flyController?.setFlyControllerInfoListener(object : CallbackWithOneParam<EvoFlyControllerInfo> {
            override fun onSuccess(flyControllerInfo: EvoFlyControllerInfo?) {
                flyControllerInfo?.getGpsInfo()?.let { gpsInfo ->
                    val location = DroneLocation(
                        latitude = gpsInfo.latitude,
                        longitude = gpsInfo.longitude,
                        altitude = gpsInfo.altitude
                    )
                    _droneLocation.value = location
                    Log.d(TAG, "Drone GPS updated: $location")
                }
            }
            
            override fun onFailure(error: AutelError?) {
                Log.e(TAG, "Failed to get drone GPS: ${error?.description}")
            }
        })
    }
    
    /**
     * Cancel current codec operations
     */
    fun cancelCodec() {
        autelCodec?.cancel()
    }
    
    /**
     * Clean up resources
     */
    fun cleanup() {
        cancelCodec()
        onCameraReady = null
        onCameraDisconnected = null
    }
}
