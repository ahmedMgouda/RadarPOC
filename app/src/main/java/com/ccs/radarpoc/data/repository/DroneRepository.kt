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
import com.ccs.radarpoc.domain.repository.IDroneRepository
import com.ccs.radarpoc.util.GeoUtils
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

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
    IDLE,
    TRACKING,
    STOPPING
}

/**
 * Battery state data
 */
data class DroneBatteryState(
    val percentage: Int,
    val voltage: Float,
    val temperature: Float,
    val isCharging: Boolean,
    val remainingFlightTime: Int
) {
    val isLow: Boolean get() = percentage <= 20
    val isCritical: Boolean get() = percentage <= 10
}

/**
 * Data class representing drone GPS location
 */
data class DroneLocation(
    val latitude: Double,
    val longitude: Double,
    val altitude: Double
)

/**
 * Configuration for drone repository
 */
data class DroneConfig(
    val missionUpdateIntervalMs: Long = 3000L,
    val minimumDistanceMeters: Double = 5.0
)

/**
 * Repository for drone operations.
 * Implements IDroneRepository interface for proper abstraction.
 */
@Singleton
class DroneRepository @Inject constructor(
    private val config: DroneConfig
) : IDroneRepository {
    
    companion object {
        private const val TAG = "DroneRepository"
        private const val DEFAULT_DRONE_SPEED = 5.0f
        private const val DEFAULT_HOVER_TIME = 0
    }
    
    private var currentMissionId: String? = null
    private var lastMissionUpdateTime = 0L
    private var lastSentTarget: DroneGpsTarget? = null
    
    private val _trackingState = MutableStateFlow(TrackingState.IDLE)
    override val trackingState: StateFlow<TrackingState> = _trackingState.asStateFlow()
    
    private val _isConnected = MutableStateFlow(false)
    override val isConnected: StateFlow<Boolean> = _isConnected.asStateFlow()
    
    private val _isCameraStreaming = MutableStateFlow(false)
    override val isCameraStreaming: StateFlow<Boolean> = _isCameraStreaming.asStateFlow()
    
    private val _droneLocation = MutableStateFlow<DroneLocation?>(null)
    override val droneLocation: StateFlow<DroneLocation?> = _droneLocation.asStateFlow()
    
    private val _batteryState = MutableStateFlow<DroneBatteryState?>(null)
    override val batteryState: StateFlow<DroneBatteryState?> = _batteryState.asStateFlow()
    
    private var autelProduct: BaseProduct? = null
    private var autelCodec: AutelCodec? = null
    
    private var onCameraReady: ((AutelCodec) -> Unit)? = null
    private var onCameraDisconnected: (() -> Unit)? = null
    
    override fun initialize() {
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
                
                startDroneGpsTracking(product)
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
    
    override fun setCameraCallbacks(
        onReady: (AutelCodec) -> Unit,
        onDisconnected: () -> Unit
    ) {
        onCameraReady = onReady
        onCameraDisconnected = onDisconnected
        
        autelCodec?.let { codec ->
            if (_isConnected.value) {
                onReady(codec)
            }
        }
    }
    
    override fun getCodec(): AutelCodec? = autelCodec
    
    override fun sendGpsTarget(
        target: DroneGpsTarget,
        onSuccess: () -> Unit,
        onError: (String) -> Unit
    ): DroneOperationResult {
        if (!_isConnected.value || autelProduct == null) {
            return DroneOperationResult.NotConnected
        }
        
        val currentTime = System.currentTimeMillis()
        val timeSinceLastUpdate = currentTime - lastMissionUpdateTime
        
        if (timeSinceLastUpdate < config.missionUpdateIntervalMs && lastSentTarget != null) {
            Log.d(TAG, "Rate limit: Skipping update")
            return DroneOperationResult.Success
        }
        
        lastSentTarget?.let { last ->
            val distance = GeoUtils.distanceMeters(
                last.latitude, last.longitude,
                target.latitude, target.longitude
            )
            if (distance < config.minimumDistanceMeters) {
                Log.d(TAG, "Target barely moved, skipping")
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
            
            _trackingState.value = TrackingState.TRACKING
            
            if (currentMissionId != null) {
                Log.d(TAG, "Canceling previous mission: $currentMissionId")
                missionManager.cancelMission(object : CallbackWithNoParam {
                    override fun onSuccess() {
                        Log.d(TAG, "Previous mission canceled")
                        startNewMission(target, missionManager, onSuccess, onError)
                    }
                    
                    override fun onFailure(error: AutelError?) {
                        Log.w(TAG, "Failed to cancel previous mission: ${error?.description}")
                        startNewMission(target, missionManager, onSuccess, onError)
                    }
                })
            } else {
                startNewMission(target, missionManager, onSuccess, onError)
            }
            
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
    
    override fun stopTrackingAndHover(
        onSuccess: () -> Unit,
        onError: (String) -> Unit
    ): DroneOperationResult {
        if (!_isConnected.value || autelProduct == null) {
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
            resetTrackingState()
            onSuccess()
        }
        
        return DroneOperationResult.Success
    }
    
    private fun resetTrackingState() {
        currentMissionId = null
        lastSentTarget = null
        lastMissionUpdateTime = 0L
        _trackingState.value = TrackingState.IDLE
    }
    
    private fun startNewMission(
        target: DroneGpsTarget,
        missionManager: com.autel.sdk.mission.MissionManager,
        onSuccess: () -> Unit,
        onError: (String) -> Unit
    ) {
        val mission = createWaypointMission(target)
        currentMissionId = mission.GUID
        
        Log.d(TAG, "Starting mission: $currentMissionId for track ${target.trackId}")
        
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
    
    private fun createWaypointMission(target: DroneGpsTarget): Evo2WaypointMission {
        val mission = Evo2WaypointMission().apply {
            missionId = 1
            missionType = MissionType.Waypoint
            altitudeType = 1
            MissionName = "Track_${target.trackId}"
            GUID = UUID.randomUUID().toString().replace("-", "")
            missionAction = 0
        }
        
        val waypoint = Evo2Waypoint(
            AutelCoordinate3D(target.latitude, target.longitude, target.altitude)
        ).apply {
            wSpeed = DEFAULT_DRONE_SPEED
            poiIndex = -1
            hoverTime = DEFAULT_HOVER_TIME
            headingMode = WaypointHeadingMode.CUSTOM_DIRECTION
            waypointType = WaypointType.STANDARD
            actions = emptyList()
        }
        
        mission.wpList = listOf(waypoint)
        mission.finishedAction = Evo2WaypointFinishedAction.KEEP_ON_LAST_POINT
        
        return mission
    }
    
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
            } catch (e: Exception) {
                Log.e(TAG, "Error parsing battery state: ${e.message}")
            }
        }
    }
    
    override fun getBatteryPercentage(): Int? = _batteryState.value?.percentage
    override fun isBatteryLow(): Boolean = _batteryState.value?.isLow == true
    override fun isBatteryCritical(): Boolean = _batteryState.value?.isCritical == true
    override fun isTracking(): Boolean = _trackingState.value == TrackingState.TRACKING
    
    override fun cancelCodec() {
        autelCodec?.cancel()
    }
    
    override fun cleanup() {
        if (isTracking()) {
            stopTrackingAndHover()
        }
        cancelCodec()
        onCameraReady = null
        onCameraDisconnected = null
    }
}
