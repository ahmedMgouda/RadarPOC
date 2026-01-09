package com.ccs.radarpoc.util

import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Utility object for geographic calculations
 */
object GeoUtils {
    
    private const val EARTH_RADIUS_METERS = 6371000.0
    
    /**
     * Calculate distance between two GPS coordinates using Haversine formula
     * @return Distance in meters
     */
    fun distanceMeters(
        lat1: Double, lon1: Double,
        lat2: Double, lon2: Double
    ): Double {
        val dLat = Math.toRadians(lat2 - lat1)
        val dLon = Math.toRadians(lon2 - lon1)
        
        val a = sin(dLat / 2).pow(2.0) +
                cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) *
                sin(dLon / 2).pow(2.0)
        
        val c = 2 * atan2(sqrt(a), sqrt(1 - a))
        return EARTH_RADIUS_METERS * c
    }
    
    /**
     * Calculate bearing (heading) from one point to another
     * @return Bearing in degrees (0-360)
     */
    fun bearingDegrees(
        fromLat: Double, fromLon: Double,
        toLat: Double, toLon: Double
    ): Double {
        val lat1 = Math.toRadians(fromLat)
        val lat2 = Math.toRadians(toLat)
        val dLon = Math.toRadians(toLon - fromLon)
        
        val y = sin(dLon) * cos(lat2)
        val x = cos(lat1) * sin(lat2) - sin(lat1) * cos(lat2) * cos(dLon)
        
        var bearing = Math.toDegrees(atan2(y, x))
        bearing = (bearing + 360) % 360  // Normalize to 0-360
        
        return bearing
    }
    
    /**
     * Predict future position based on current position, speed, and heading
     * @param lat Current latitude
     * @param lon Current longitude
     * @param speedMps Speed in meters per second
     * @param headingDegrees Heading in degrees (0-360)
     * @param seconds Time in seconds to predict
     * @return Pair of (latitude, longitude)
     */
    fun predictPosition(
        lat: Double, lon: Double,
        speedMps: Double, headingDegrees: Double,
        seconds: Double
    ): Pair<Double, Double> {
        val distanceMeters = speedMps * seconds
        return destinationPoint(lat, lon, distanceMeters, headingDegrees)
    }
    
    /**
     * Calculate destination point given start point, distance, and bearing
     * @param lat Starting latitude
     * @param lon Starting longitude
     * @param distanceMeters Distance to travel in meters
     * @param bearingDegrees Bearing in degrees
     * @return Pair of (latitude, longitude)
     */
    fun destinationPoint(
        lat: Double, lon: Double,
        distanceMeters: Double, bearingDegrees: Double
    ): Pair<Double, Double> {
        val angularDistance = distanceMeters / EARTH_RADIUS_METERS
        val bearing = Math.toRadians(bearingDegrees)
        val lat1 = Math.toRadians(lat)
        val lon1 = Math.toRadians(lon)
        
        val lat2 = kotlin.math.asin(
            sin(lat1) * cos(angularDistance) +
            cos(lat1) * sin(angularDistance) * cos(bearing)
        )
        
        val lon2 = lon1 + atan2(
            sin(bearing) * sin(angularDistance) * cos(lat1),
            cos(angularDistance) - sin(lat1) * sin(lat2)
        )
        
        return Pair(
            Math.toDegrees(lat2),
            Math.toDegrees(lon2)
        )
    }
    
    /**
     * Format distance for display
     */
    fun formatDistance(meters: Double): String {
        return when {
            meters < 1000 -> "${meters.toInt()}m"
            else -> String.format("%.1fkm", meters / 1000)
        }
    }
    
    /**
     * Format coordinates for display
     */
    fun formatCoordinates(lat: Double, lon: Double, precision: Int = 6): String {
        return "${String.format("%.${precision}f", lat)}°, ${String.format("%.${precision}f", lon)}°"
    }
}
