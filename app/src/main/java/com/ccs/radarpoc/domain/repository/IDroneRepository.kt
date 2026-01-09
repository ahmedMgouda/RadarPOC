package com.ccs.radarpoc.domain.repository

import com.ccs.radarpoc.data.repository.DroneBatteryState
import com.ccs.radarpoc.data.repository.DroneGpsTarget
import com.ccs.radarpoc.data.repository.DroneLocation
import com.ccs.radarpoc.data.repository.DroneOperationResult
import com.ccs.radarpoc.data.repository.TrackingState
import com.autel.sdk.video.AutelCodec
import kotlinx.coroutines.flow.StateFlow

/**
 * Interface for drone operations.
 * Provides a clean contract for drone connection, camera, GPS commands, and battery monitoring.
 * This abstraction enables:
 * - Unit testing with mock implementations
 * - Dependency injection
 * - Future drone SDK swapping
 */
interface IDroneRepository {
    
    /**
     * Observable tracking state
     */
    val trackingState: StateFlow<TrackingState>
    
    /**
     * Observable connection state
     */
    val isConnected: StateFlow<Boolean>
    
    /**
     * Observable camera streaming state
     */
    val isCameraStreaming: StateFlow<Boolean>
    
    /**
     * Observable drone location
     */
    val droneLocation: StateFlow<DroneLocation?>
    
    /**
     * Observable battery state
     */
    val batteryState: StateFlow<DroneBatteryState?>
    
    /**
     * Initialize drone SDK and register connection listener
     */
    fun initialize()
    
    /**
     * Set camera callbacks for UI layer
     * @param onReady Callback when camera codec is ready
     * @param onDisconnected Callback when camera disconnects
     */
    fun setCameraCallbacks(
        onReady: (AutelCodec) -> Unit,
        onDisconnected: () -> Unit
    )
    
    /**
     * Get the current codec for camera feed
     * @return AutelCodec if available, null otherwise
     */
    fun getCodec(): AutelCodec?
    
    /**
     * Send GPS coordinates to drone for tracking
     * @param target GPS target with coordinates and track ID
     * @param onSuccess Callback on successful command send
     * @param onError Callback on error with message
     * @return DroneOperationResult indicating success, error, or not connected
     */
    fun sendGpsTarget(
        target: DroneGpsTarget,
        onSuccess: () -> Unit = {},
        onError: (String) -> Unit = {}
    ): DroneOperationResult
    
    /**
     * Stop tracking and hover in place
     * @param onSuccess Callback on successful stop
     * @param onError Callback on error with message
     * @return DroneOperationResult indicating success, error, or not connected
     */
    fun stopTrackingAndHover(
        onSuccess: () -> Unit = {},
        onError: (String) -> Unit = {}
    ): DroneOperationResult
    
    /**
     * Get current battery percentage
     * @return Battery percentage (0-100) or null if not available
     */
    fun getBatteryPercentage(): Int?
    
    /**
     * Check if battery is low (≤20%)
     */
    fun isBatteryLow(): Boolean
    
    /**
     * Check if battery is critical (≤10%)
     */
    fun isBatteryCritical(): Boolean
    
    /**
     * Check if currently tracking a target
     */
    fun isTracking(): Boolean
    
    /**
     * Cancel current codec operations
     */
    fun cancelCodec()
    
    /**
     * Clean up resources
     */
    fun cleanup()
}
