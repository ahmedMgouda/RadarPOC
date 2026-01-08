package com.ccs.radarpoc.data

import com.google.gson.annotations.SerializedName

/**
 * Data models for radar track JSON structure
 */
data class RadarTracksResponse(
    @SerializedName("result")
    val result: List<RadarTrack>
)

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
    
    // Runtime properties
    var isLocked: Boolean = false,
    var isStale: Boolean = false
)

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
)

data class Observation(
    @SerializedName("range")
    val range: Double,
    
    @SerializedName("radialVelocity")
    val radialVelocity: Double,
    
    @SerializedName("azimuthAngle")
    val azimuthAngle: Double
)

data class Stats(
    @SerializedName("amplitude")
    val amplitude: Double,
    
    @SerializedName("rcs")
    val rcs: Double,
    
    @SerializedName("classifications")
    val classifications: List<Classification>
)

data class Classification(
    @SerializedName("type")
    val type: String,
    
    @SerializedName("confidence")
    val confidence: Double
)
