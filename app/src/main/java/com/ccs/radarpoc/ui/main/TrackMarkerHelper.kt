package com.ccs.radarpoc.ui.main

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.drawable.BitmapDrawable

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
    private val markerCache = mutableMapOf<String, BitmapDrawable>()
    
    /**
     * Get a color for a track based on its ID
     * Same ID always gets same color
     */
    fun getColorForTrack(trackId: String): Int {
        val hash = trackId.hashCode().let { if (it < 0) -it else it }
        return TRACK_COLORS[hash % TRACK_COLORS.size]
    }
    
    /**
     * Create a square marker bitmap drawable for OSMDroid
     * @param trackId The track ID (used to determine color)
     * @param isStale Whether the track is stale (will be black)
     * @param isLocked Whether the track is locked (will have white border)
     * @param sizeMultiplier Size multiplier (default 1.0 = 32px, 2.0 = 64px, etc.)
     * @param isSelected Whether the marker is currently selected (will have glowing border)
     */
    fun createSquareMarker(
        context: Context,
        trackId: String,
        isStale: Boolean = false,
        isLocked: Boolean = false,
        sizeMultiplier: Float = 1.0f,
        isSelected: Boolean = false
    ): BitmapDrawable {
        // Create cache key including size multiplier and selection state
        val cacheKey = "${trackId}_${isStale}_${isLocked}_${sizeMultiplier}_${isSelected}"
        
        // Return cached version if available
        markerCache[cacheKey]?.let { return it }
        
        // Determine fill color
        val fillColor = if (isStale) STALE_COLOR else getColorForTrack(trackId)
        
        // Calculate actual marker size based on multiplier
        val actualSize = (MARKER_SIZE * sizeMultiplier).toInt()
        val actualBorderWidth = BORDER_WIDTH * sizeMultiplier
        
        // Calculate text size and total height (marker + text)
        val textSize = (12f * sizeMultiplier)
        val textPaint = Paint().apply {
            color = Color.WHITE
            this.textSize = textSize
            isAntiAlias = true
            textAlign = Paint.Align.CENTER
            style = Paint.Style.FILL
            setShadowLayer(2f * sizeMultiplier, 0f, 0f, Color.BLACK) // Text shadow for readability
        }
        
        val textBounds = android.graphics.Rect()
        val trackLabel = "T-$trackId"
        textPaint.getTextBounds(trackLabel, 0, trackLabel.length, textBounds)
        val textHeight = textBounds.height()
        val textPadding = (4 * sizeMultiplier).toInt()
        
        // Create bitmap with extra space for text below marker
        val totalHeight = actualSize + textPadding + textHeight + textPadding
        val bitmap = Bitmap.createBitmap(actualSize, totalHeight, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        
        // Draw fill
        val fillPaint = Paint().apply {
            color = fillColor
            style = Paint.Style.FILL
            isAntiAlias = true
        }
        canvas.drawRect(0f, 0f, actualSize.toFloat(), actualSize.toFloat(), fillPaint)
        
        // Draw border (locked or normal)
        val borderPaint = Paint().apply {
            color = if (isLocked) LOCKED_BORDER_COLOR else Color.parseColor("#80FFFFFF")
            style = Paint.Style.STROKE
            strokeWidth = if (isLocked) actualBorderWidth * 2 else actualBorderWidth
            isAntiAlias = true
        }
        val halfBorder = borderPaint.strokeWidth / 2
        canvas.drawRect(
            halfBorder, 
            halfBorder, 
            actualSize - halfBorder, 
            actualSize - halfBorder, 
            borderPaint
        )
        
        // Draw selection highlight (glowing cyan border)
        if (isSelected) {
            val selectionPaint = Paint().apply {
                color = Color.parseColor("#00E5FF") // Bright cyan
                style = Paint.Style.STROKE
                strokeWidth = actualBorderWidth * 3 // Thicker border
                isAntiAlias = true
            }
            val selectionHalfBorder = selectionPaint.strokeWidth / 2
            canvas.drawRect(
                selectionHalfBorder,
                selectionHalfBorder,
                actualSize - selectionHalfBorder,
                actualSize - selectionHalfBorder,
                selectionPaint
            )
        }
        
        // Draw track ID text below marker
        val textY = actualSize + textPadding + textHeight
        canvas.drawText(trackLabel, actualSize / 2f, textY.toFloat(), textPaint)
        
        // Create drawable and cache it (OSMDroid compatible)
        val drawable = BitmapDrawable(context.resources, bitmap)
        markerCache[cacheKey] = drawable
        
        return drawable
    }
    
    /**
     * Clear the marker cache (call when done with map)
     */
    fun clearCache() {
        markerCache.clear()
    }
}
