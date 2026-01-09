package com.ccs.radarpoc.data.repository

import android.util.Log
import com.autel.common.CallbackWithNoParam
import com.autel.common.CallbackWithOneParam
import com.autel.common.CallbackWithOneParamProgress
import com.autel.common.battery.evo.EvoChargeState
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
import com.autel.sdk.product.BaseProduct
import com.autel.sdk.product.Evo2Aircraft
import com.autel.sdk.video.AutelCodec
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.UUID
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

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
 * Tracking state for UI feedback
 */
enum class TrackingState {
    IDLE,           // Not tracking any target
    TRACKING,       // Actively tracking a target
    STOPPING        // In process of stopping (hover transition)
}

/**
 * Battery state data
 */
data class DroneBatteryState(
    val percentage: Int,          // 0-100
    val voltage: Float,           // Volts
    val temperature: Float,       // Celsius
    val isCharging: Boolean,
    val remainingFlightTime: Int  // Seconds
) {
    val isLow: Boolean get() = percentage <= 20
    val isCritical: Boolean get() = percentage <= 10
}

/**
 * Repository for drone operations
 * Handles connection, camera feed, GPS commands, and battery monitoring
 */
class DroneRepository(
    private val missionUpdateIntervalMs: Long = 3000L,      // Minimum time between mission updates
    private val minimumDistanceMeters: Double = 5.0         // Minimum distance change to trigger update
) {
    companion object {
        private const val TAG = "DroneRepository"
        private const val DEFAULT_DRONE_SPEED = 5.0f
        private const val DEFAULT_HOVER_TIME = 0  // Don't hover at waypoint, we'll keep updating
        private const val EARTH_RADIUS_METERS = 6371000.0
    }
    
    // Mission tracking for optimization
    private var currentMissionId: String? = null
    private var lastMissionUpdateTime = 0L
    private var lastSentTarget: DroneGpsTarget? = null
    
    // Tracking state
    private val _trackingState = MutableStateFlow(TrackingState.IDLE)
    val trackingState: StateFlow<TrackingState> = _trackingState.asStateFlow()
    
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
    
    // Battery state
    private val _batteryState = MutableStateFlow<DroneBatteryState?>(null)
    val batteryState: StateFlow<DroneBatteryState?> = _batteryState.asStateFlow()
    
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
                
                // Start battery monitoring
                startBatteryMonitoring(product)
            }
            
            override fun productDisconnected() {
                Log.d(TAG, "Drone disconnected")
                autelProduct = null
                autelCodec = null
                _isConnected.value = false
                _isCameraStreaming.value = false
                _trackingState.value = TrackingState.IDLE
                _batteryState.value = null
                currentMissionId = null
                lastSentTarget = null
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
     * Optimized with rate limiting and distance threshold
     */
    fun sendGpsTarget(
        target: DroneGpsTarget,
        onSuccess: () -> Unit = {},
        onError: (String) -> Unit = {}
    ): DroneOperationResult {
        if (!_isConnected.value || autelProduct == null) {
            return DroneOperationResult.NotConnected
        }
        
        val currentTime = System.currentTimeMillis()
        val timeSinceLastUpdate = currentTime - lastMissionUpdateTime
        
        // Rate limiting: Check if enough time has passed since last update
        if (timeSinceLastUpdate < missionUpdateIntervalMs && lastSentTarget != null) {
            Log.d(TAG, "Rate limit: Skipping update (${timeSinceLastUpdate}ms < ${missionUpdateIntervalMs}ms)")
            return DroneOperationResult.Success // Silent skip
        }
        
        // Distance threshold: Check if target moved enough
        lastSentTarget?.let { last ->
            val distance = calculateDistance(
                last.latitude, last.longitude,
                target.latitude, target.longitude
            )
            if (distance < minimumDistanceMeters) {
                Log.d(TAG, "Target barely moved (${String.format("%.1f", distance)}m < ${minimumDistanceMeters}m), skipping")
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
            
            // Update state
            _trackingState.value = TrackingState.TRACKING
            
            // Cancel any existing mission before starting new one
            if (currentMissionId != null) {
                Log.d(TAG, "Canceling previous mission: $currentMissionId")
                missionManager.cancelMission(object : CallbackWithNoParam {
                    override fun onSuccess() {
                        Log.d(TAG, "Previous mission canceled")
                        startNewMission(target, missionManager, onSuccess, onError)
                    }
                    
                    override fun onFailure(error: AutelError?) {
                        Log.w(TAG, "Failed to cancel previous mission: ${error?.description}")
                        // Start new mission anyway
                        startNewMission(target, missionManager, onSuccess, onError)
                    }
                })
            } else {
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
     * Stop tracking and hover in place
     * Call this when user unlocks a track or target is lost
     */
    fun stopTrackingAndHover(
        onSuccess: () -> Unit = {},
        onError: (String) -> Unit = {}
    ): DroneOperationResult {
        if (!_isConnected.value || autelProduct == null) {
            // Not connected, just reset state
            resetTrackingState()
            return DroneOperationResult.NotConnected
        }
        
        Log.d(TAG, "Stopping tracking and hovering in place")
        _trackingState.value = TrackingState.STOPPING
        
        val missionManager = autelProduct?.getMissionManager()
        if (missionManager == null) {
            resetTrackingState()
            return DroneOperationResult.Error("Mission manager not available")
        }
        
        // Cancel current mission - drone should hover automatically when mission canceled
        if (currentMissionId != null) {
            missionManager.cancelMission(object : CallbackWithNoParam {
                override fun onSuccess() {
                    Log.d(TAG, "Mission canceled, drone now hovering")
                    resetTrackingState()
                    onSuccess()
                }
                
                override fun onFailure(error: AutelError?) {
                    val errorMsg = "Failed to cancel mission: ${error?.description}"
                    Log.e(TAG, errorMsg)
                    resetTrackingState()
                    onError(errorMsg)
                }
            })
        } else {
            // No active mission, just reset state
            resetTrackingState()
            onSuccess()
        }
        
        return DroneOperationResult.Success
    }
    
    /**
     * Reset tracking state variables
     */
    private fun resetTrackingState() {
        currentMissionId = null
        lastSentTarget = null
        lastMissionUpdateTime = 0L
        _trackingState.value = TrackingState.IDLE
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
        
        Log.d(TAG, "Starting mission: $currentMissionId for track ${target.trackId}")
        Log.d(TAG, "Target: ${target.latitude}, ${target.longitude}, alt: ${target.altitude}")
        
        missionManager.prepareMission(mission, object : CallbackWithOneParamProgress<Boolean> {
            override fun onProgress(progress: Float) {
                Log.d(TAG, "Mission preparation: ${(progress * 100).toInt()}%")
            }
            
            override fun onSuccess(result: Boolean?) {
                Log.d(TAG, "Mission prepared, starting...")
                
                missionManager.startMission(object : CallbackWithNoParam {
                    override fun onSuccess() {
                        Log.d(TAG, "Mission started successfully")
                        onSuccess()
                    }
                    
                    override fun onFailure(error: AutelError?) {
                        val errorMsg = "Failed to start mission: ${error?.description}"
                        Log.e(TAG, errorMsg)
                        currentMissionId = null
                        onError(errorMsg)
                    }
                })
            }
            
            override fun onFailure(error: AutelError?) {
                val errorMsg = "Failed to prepare mission: ${error?.description}"
                Log.e(TAG, errorMsg)
                currentMissionId = null
                onError(errorMsg)
            }
        })
    }
    
    /**
     * Calculate distance between two GPS coordinates using Haversine formula
     */
    private fun calculateDistance(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val dLat = Math.toRadians(lat2 - lat1)
        val dLon = Math.toRadians(lon2 - lon1)
        
        val a = sin(dLat / 2).pow(2.0) +
                cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) *
                sin(dLon / 2).pow(2.0)
        
        val c = 2 * atan2(sqrt(a), sqrt(1 - a))
        return EARTH_RADIUS_METERS * c
    }
    
    /**
     * Create a waypoint mission for the target
     */
    private fun createWaypointMission(target: DroneGpsTarget): Evo2WaypointMission {
        val mission = Evo2WaypointMission().apply {
            missionId = 1
            missionType = MissionType.Waypoint
            altitudeType = 1 // 0=relative, 1=absolute altitude
            MissionName = "Track_${target.trackId}"
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
            waypointType = WaypointType.STANDARD
            actions = emptyList()
        }
        
        mission.wpList = listOf(waypoint)
        // Hover at end instead of RTH - we'll keep updating the target
        mission.finishedAction = Evo2WaypointFinishedAction.KEEP_ON_LAST_POINT
        
        return mission
    }
    
    /**
     * Start tracking drone GPS location
     */
    private fun startDroneGpsTracking(product: BaseProduct?) {
        val flyController = (product as? Evo2Aircraft)?.flyController
        
        flyController?.setFlyControllerInfoListener(object : CallbackWithOneParam<EvoFlyControllerInfo> {
            override fun onSuccess(info: EvoFlyControllerInfo?) {
                info?.gpsInfo?.let { gps ->
                    _droneLocation.value = DroneLocation(
                        latitude = gps.latitude,
                        longitude = gps.longitude,
                        altitude = gps.altitude
                    )
                }
            }
            
            override fun onFailure(error: AutelError?) {
                Log.e(TAG, "GPS tracking error: ${error?.description}")
            }
        })
    }
    
    /**
     * Start battery monitoring
     */
    private fun startBatteryMonitoring(product: BaseProduct?) {
        val battery = (product as? Evo2Aircraft)?.battery
        
        battery?.setBatteryStateListener { batteryState ->
            try {
                val percentage = batteryState?.remainPowerPercent ?: 0
                val voltage = batteryState?.voltage ?: 0f
                val temperature = batteryState?.temperature ?: 0f
                val isCharging = batteryState?.chargeState == EvoChargeState.CHARGING
                val remainingTime = batteryState?.remainingFlightTime ?: 0
                
                _batteryState.value = DroneBatteryState(
                    percentage = percentage,
                    voltage = voltage,
                    temperature = temperature,
                    isCharging = isCharging,
                    remainingFlightTime = remainingTime
                )
                
                Log.d(TAG, "Battery: $percentage%, ${voltage}V, ${temperature}°C, remaining: ${remainingTime}s")
            } catch (e: Exception) {
                Log.e(TAG, "Error parsing battery state: ${e.message}")
            }
        }
    }
    
    /**
     * Get current battery percentage
     * @return Battery percentage (0-100) or null if not available
     */
    fun getBatteryPercentage(): Int? = _batteryState.value?.percentage
    
    /**
     * Check if battery is low (≤20%)
     */
    fun isBatteryLow(): Boolean = _batteryState.value?.isLow == true
    
    /**
     * Check if battery is critical (≤10%)
     */
    fun isBatteryCritical(): Boolean = _batteryState.value?.isCritical == true
    
    /**
     * Check if currently tracking a target
     */
    fun isTracking(): Boolean = _trackingState.value == TrackingState.TRACKING
    
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
        // Stop any active tracking
        if (isTracking()) {
            stopTrackingAndHover()
        }
        cancelCodec()
        onCameraReady = null
        onCameraDisconnected = null
    }
}
