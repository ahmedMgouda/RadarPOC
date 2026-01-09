package com.ccs.radarpoc.ui.main

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import com.google.android.gms.maps.model.BitmapDescriptor
import com.google.android.gms.maps.model.BitmapDescriptorFactory

/**
 * Helper class to create custom square markers for tracks
 * Each track gets a unique color based on its ID
 * Stale tracks are shown in black
 */
object TrackMarkerHelper {
    
    // Marker size in pixels
    private const val MARKER_SIZE = 32
    private const val BORDER_WIDTH = 2f
    
    // Predefined colors for active tracks (vibrant, easy to distinguish)
    private val TRACK_COLORS = listOf(
        Color.parseColor("#FF5722"),  // Deep Orange
        Color.parseColor("#2196F3"),  // Blue
        Color.parseColor("#4CAF50"),  // Green
        Color.parseColor("#9C27B0"),  // Purple
        Color.parseColor("#FF9800"),  // Orange
        Color.parseColor("#00BCD4"),  // Cyan
        Color.parseColor("#E91E63"),  // Pink
        Color.parseColor("#FFEB3B"),  // Yellow
        Color.parseColor("#3F51B5"),  // Indigo
        Color.parseColor("#009688"),  // Teal
        Color.parseColor("#F44336"),  // Red
        Color.parseColor("#8BC34A"),  // Light Green
        Color.parseColor("#673AB7"),  // Deep Purple
        Color.parseColor("#03A9F4"),  // Light Blue
        Color.parseColor("#CDDC39"),  // Lime
        Color.parseColor("#FFC107"),  // Amber
    )
    
    // Stale track color
    private val STALE_COLOR = Color.parseColor("#1A1A1A")  // Near black
    
    // Locked track border color
    private val LOCKED_BORDER_COLOR = Color.WHITE
    
    // Cache for marker bitmaps to avoid recreating
    private val markerCache = mutableMapOf<String, BitmapDescriptor>()
    
    /**
     * Get a color for a track based on its ID
     * Same ID always gets same color
     */
    fun getColorForTrack(trackId: String): Int {
        val hash = trackId.hashCode().let { if (it < 0) -it else it }
        return TRACK_COLORS[hash % TRACK_COLORS.size]
    }
    
    /**
     * Create a square marker bitmap descriptor
     * @param trackId The track ID (used to determine color)
     * @param isStale Whether the track is stale (will be black)
     * @param isLocked Whether the track is locked (will have white border)
     */
    fun createSquareMarker(
        context: Context,
        trackId: String,
        isStale: Boolean = false,
        isLocked: Boolean = false
    ): BitmapDescriptor {
        // Create cache key
        val cacheKey = "${trackId}_${isStale}_${isLocked}"
        
        // Return cached version if available
        markerCache[cacheKey]?.let { return it }
        
        // Determine fill color
        val fillColor = if (isStale) STALE_COLOR else getColorForTrack(trackId)
        
        // Create bitmap
        val bitmap = Bitmap.createBitmap(MARKER_SIZE, MARKER_SIZE, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        
        // Draw fill
        val fillPaint = Paint().apply {
            color = fillColor
            style = Paint.Style.FILL
            isAntiAlias = true
        }
        canvas.drawRect(0f, 0f, MARKER_SIZE.toFloat(), MARKER_SIZE.toFloat(), fillPaint)
        
        // Draw border
        val borderPaint = Paint().apply {
            color = if (isLocked) LOCKED_BORDER_COLOR else Color.parseColor("#80FFFFFF")
            style = Paint.Style.STROKE
            strokeWidth = if (isLocked) BORDER_WIDTH * 2 else BORDER_WIDTH
            isAntiAlias = true
        }
        val halfBorder = borderPaint.strokeWidth / 2
        canvas.drawRect(
            halfBorder, 
            halfBorder, 
            MARKER_SIZE - halfBorder, 
            MARKER_SIZE - halfBorder, 
            borderPaint
        )
        
        // Create descriptor and cache it
        val descriptor = BitmapDescriptorFactory.fromBitmap(bitmap)
        markerCache[cacheKey] = descriptor
        
        return descriptor
    }
    
    /**
     * Clear the marker cache (call when done with map)
     */
    fun clearCache() {
        markerCache.clear()
    }
}
