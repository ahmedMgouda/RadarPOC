package com.ccs.radarpoc.data.repository

import android.util.Log
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
class DroneRepository {
    companion object {
        private const val TAG = "DroneRepository"
        private const val DEFAULT_DRONE_SPEED = 5.0f
        private const val DEFAULT_HOVER_TIME = 5
    }
    
    // Connection state
    private val _isConnected = MutableStateFlow(false)
    val isConnected: StateFlow<Boolean> = _isConnected.asStateFlow()
    
    // Camera state
    private val _isCameraStreaming = MutableStateFlow(false)
    val isCameraStreaming: StateFlow<Boolean> = _isCameraStreaming.asStateFlow()
    
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
     */
    fun sendGpsTarget(
        target: DroneGpsTarget,
        onSuccess: () -> Unit = {},
        onError: (String) -> Unit = {}
    ): DroneOperationResult {
        if (!_isConnected.value || autelProduct == null) {
            return DroneOperationResult.NotConnected
        }
        
        try {
            val missionManager = autelProduct?.getMissionManager()
            if (missionManager == null) {
                val error = "Mission manager not available"
                Log.e(TAG, error)
                onError(error)
                return DroneOperationResult.Error(error)
            }
            
            // Create mission
            val mission = createWaypointMission(target)
            
            // Prepare and start mission
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
                            onError(errorMsg)
                        }
                    })
                }
                
                override fun onFailure(error: AutelError?) {
                    val errorMsg = "Failed to prepare mission: ${error?.description}"
                    Log.e(TAG, errorMsg)
                    onError(errorMsg)
                }
            })
            
            return DroneOperationResult.Success
            
        } catch (e: Exception) {
            val errorMsg = "Error sending GPS to drone: ${e.message}"
            Log.e(TAG, errorMsg, e)
            onError(errorMsg)
            return DroneOperationResult.Error(errorMsg)
        }
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
