package com.ccs.radarpoc.data

import com.google.gson.annotations.SerializedName

/**
 * Response wrapper for radar tracks API
 */
data class RadarTracksResponse(
    @SerializedName("result")
    val result: List<RadarTrack>
)

/**
 * Radar track data model
 * Represents a single tracked object from the radar system
 */
data class RadarTrack(
    @SerializedName("id")
    val id: String,
    
    @SerializedName("timestamp")
    val timestamp: Long,
    
    @SerializedName("geolocation")
    val geolocation: Geolocation,
    
    @SerializedName("observation")
    val observation: Observation,
    
    @SerializedName("stats")
    val stats: Stats,
    
    // Runtime properties (not from API)
    @Transient
    var isStale: Boolean = false
) {
    /**
     * Get the top classification by confidence
     */
    val topClassification: Classification?
        get() = stats.classifications.maxByOrNull { it.confidence }
    
    /**
     * Get classification display string
     */
    val classificationDisplay: String
        get() {
            val top = topClassification
            return if (top != null) {
                "${top.type} (${String.format("%.1f", top.confidence * 100)}%)"
            } else {
                "Unknown"
            }
        }
}

/**
 * Geographic location data
 */
data class Geolocation(
    @SerializedName("latitude")
    val latitude: Double,
    
    @SerializedName("longitude")
    val longitude: Double,
    
    @SerializedName("altitude")
    val altitude: Double,
    
    @SerializedName("speed")
    val speed: Double,
    
    @SerializedName("heading")
    val heading: Double
) {
    /**
     * Format coordinates for display
     */
    val coordinatesDisplay: String
        get() = "${String.format("%.6f", latitude)}°, ${String.format("%.6f", longitude)}°"
    
    /**
     * Format altitude for display
     */
    val altitudeDisplay: String
        get() = "${String.format("%.1f", altitude)} m"
    
    /**
     * Format speed for display
     */
    val speedDisplay: String
        get() = "${String.format("%.1f", speed)} m/s"
    
    /**
     * Format heading for display
     */
    val headingDisplay: String
        get() = "${String.format("%.1f", heading)}°"
}

/**
 * Radar observation data
 */
data class Observation(
    @SerializedName("range")
    val range: Double,
    
    @SerializedName("radialVelocity")
    val radialVelocity: Double,
    
    @SerializedName("azimuthAngle")
    val azimuthAngle: Double
) {
    /**
     * Format range for display
     */
    val rangeDisplay: String
        get() = "${String.format("%.1f", range)} m"
    
    /**
     * Format azimuth for display
     */
    val azimuthDisplay: String
        get() = "${String.format("%.1f", azimuthAngle)}°"
}

/**
 * Track statistics
 */
data class Stats(
    @SerializedName("amplitude")
    val amplitude: Double,
    
    @SerializedName("rcs")
    val rcs: Double,
    
    @SerializedName("classifications")
    val classifications: List<Classification>
)

/**
 * Track classification
 */
data class Classification(
    @SerializedName("type")
    val type: String,
    
    @SerializedName("confidence")
    val confidence: Double
) {
    /**
     * Format confidence as percentage
     */
    val confidencePercent: String
        get() = "${String.format("%.1f", confidence * 100)}%"
}
