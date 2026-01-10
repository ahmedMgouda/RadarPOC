package com.ccs.radarpoc.data.model

import org.osmdroid.util.GeoPoint

/**
 * Represents a radar unit with its location, field of view, and boresight
 */
data class RadarUnit(
    val id: String,
    val name: String,
    val location: GeoPoint,
    val altitude: Double,
    val fov: RadarFOV?,
    val boresight: BoresightLine?,
    val lineColor: Int,      // ARGB color from KML
    val lineWidth: Float
)

/**
 * Radar Field of View - the detection coverage area
 */
data class RadarFOV(
    val points: List<GeoPoint>,  // Polygon points
    val fillColor: Int,          // ARGB color (with transparency)
    val strokeColor: Int,        // ARGB border color
    val strokeWidth: Float
)

/**
 * Boresight line - the radar's forward direction (0° azimuth reference)
 */
data class BoresightLine(
    val start: GeoPoint,  // Radar position
    val end: GeoPoint,    // Direction end point
    val color: Int,       // ARGB color
    val width: Float
)

/**
 * Container for all radar FOV data from KML
 */
data class RadarFOVData(
    val documentName: String,
    val radars: List<RadarUnit>,
    val fetchedAt: Long = System.currentTimeMillis()
) {
    val radarCount: Int get() = radars.size
    
    fun getRadarById(id: String): RadarUnit? = radars.find { it.id == id }
    
    fun getAllFOVPolygons(): List<Pair<RadarUnit, RadarFOV>> {
        return radars.mapNotNull { radar ->
            radar.fov?.let { fov -> Pair(radar, fov) }
        }
    }
    
    fun getAllBoresightLines(): List<Pair<RadarUnit, BoresightLine>> {
        return radars.mapNotNull { radar ->
            radar.boresight?.let { boresight -> Pair(radar, boresight) }
        }
    }
}
