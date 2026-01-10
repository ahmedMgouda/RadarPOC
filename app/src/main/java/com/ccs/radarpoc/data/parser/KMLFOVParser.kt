package com.ccs.radarpoc.data.parser

import android.graphics.Color
import android.util.Log
import com.ccs.radarpoc.data.model.BoresightLine
import com.ccs.radarpoc.data.model.RadarFOV
import com.ccs.radarpoc.data.model.RadarFOVData
import com.ccs.radarpoc.data.model.RadarUnit
import org.osmdroid.util.GeoPoint
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserFactory
import java.io.StringReader

/**
 * Parser for KML files containing radar FOV data
 * 
 * Supports:
 * - Multiple radar units (nested Documents)
 * - Radar location (Point placemark)
 * - FOV polygon (Polygon placemark)
 * - Boresight line (LineString placemark)
 * - KML colors (AABBGGRR format)
 */
object KMLFOVParser {
    
    private const val TAG = "KMLFOVParser"
    
    // Default colors if not specified in KML
    private const val DEFAULT_LINE_COLOR = 0xFF005555.toInt()  // Teal
    private const val DEFAULT_FILL_COLOR = 0x33005555.toInt()  // Semi-transparent teal
    private const val DEFAULT_LINE_WIDTH = 2.5f
    
    /**
     * Parse KML string into RadarFOVData
     */
    fun parse(kmlContent: String): Result<RadarFOVData> {
        return try {
            val factory = XmlPullParserFactory.newInstance()
            factory.isNamespaceAware = false
            val parser = factory.newPullParser()
            parser.setInput(StringReader(kmlContent))
            
            var documentName = "Radar FOV"
            val radars = mutableListOf<RadarUnit>()
            
            var eventType = parser.eventType
            var currentDepth = 0
            var inNestedDocument = false
            var nestedDocumentName = ""
            
            // Temporary storage for current radar being parsed
            var currentRadarLocation: GeoPoint? = null
            var currentRadarAltitude = 0.0
            var currentRadarName = ""
            var currentFOV: RadarFOV? = null
            var currentBoresight: BoresightLine? = null
            var currentLineColor = DEFAULT_LINE_COLOR
            var currentLineWidth = DEFAULT_LINE_WIDTH
            
            // Placemark parsing state
            var inPlacemark = false
            var placemarkName = ""
            var currentStyle: StyleInfo? = null
            
            while (eventType != XmlPullParser.END_DOCUMENT) {
                when (eventType) {
                    XmlPullParser.START_TAG -> {
                        when (parser.name) {
                            "Document" -> {
                                currentDepth++
                                if (currentDepth == 2) {
                                    // Nested document = radar unit
                                    inNestedDocument = true
                                    // Reset radar data
                                    currentRadarLocation = null
                                    currentRadarAltitude = 0.0
                                    currentRadarName = ""
                                    currentFOV = null
                                    currentBoresight = null
                                    currentLineColor = DEFAULT_LINE_COLOR
                                    currentLineWidth = DEFAULT_LINE_WIDTH
                                }
                            }
                            "Placemark" -> {
                                inPlacemark = true
                                placemarkName = ""
                                currentStyle = null
                            }
                            "name" -> {
                                val name = parser.nextText()
                                when {
                                    inPlacemark -> placemarkName = name
                                    inNestedDocument && nestedDocumentName.isEmpty() -> {
                                        nestedDocumentName = name
                                        currentRadarName = name
                                    }
                                    currentDepth == 1 && documentName == "Radar FOV" -> {
                                        documentName = name
                                    }
                                }
                            }
                            "Style" -> {
                                currentStyle = parseStyle(parser)
                            }
                            "Point" -> {
                                if (inPlacemark) {
                                    val coords = parseCoordinates(parser)
                                    if (coords.isNotEmpty()) {
                                        currentRadarLocation = coords[0]
                                        currentRadarAltitude = parseAltitude(parser, coords[0])
                                        if (currentRadarName.isEmpty()) {
                                            currentRadarName = placemarkName
                                        }
                                    }
                                }
                            }
                            "Polygon" -> {
                                if (inPlacemark && placemarkName.contains("FOV", ignoreCase = true)) {
                                    val coords = parsePolygonCoordinates(parser)
                                    if (coords.isNotEmpty()) {
                                        val style = currentStyle
                                        currentFOV = RadarFOV(
                                            points = coords,
                                            fillColor = style?.fillColor ?: DEFAULT_FILL_COLOR,
                                            strokeColor = style?.lineColor ?: DEFAULT_LINE_COLOR,
                                            strokeWidth = style?.lineWidth ?: DEFAULT_LINE_WIDTH
                                        )
                                        currentLineColor = style?.lineColor ?: DEFAULT_LINE_COLOR
                                        currentLineWidth = style?.lineWidth ?: DEFAULT_LINE_WIDTH
                                    }
                                }
                            }
                            "LineString" -> {
                                if (inPlacemark && placemarkName.contains("Boresight", ignoreCase = true)) {
                                    val coords = parseLineStringCoordinates(parser)
                                    if (coords.size >= 2) {
                                        val style = currentStyle
                                        currentBoresight = BoresightLine(
                                            start = coords[0],
                                            end = coords[1],
                                            color = style?.lineColor ?: DEFAULT_LINE_COLOR,
                                            width = style?.lineWidth ?: DEFAULT_LINE_WIDTH
                                        )
                                    }
                                }
                            }
                        }
                    }
                    XmlPullParser.END_TAG -> {
                        when (parser.name) {
                            "Document" -> {
                                if (currentDepth == 2 && inNestedDocument) {
                                    // End of radar unit document - create RadarUnit
                                    currentRadarLocation?.let { location ->
                                        val radar = RadarUnit(
                                            id = generateRadarId(currentRadarName),
                                            name = currentRadarName,
                                            location = location,
                                            altitude = currentRadarAltitude,
                                            fov = currentFOV,
                                            boresight = currentBoresight,
                                            lineColor = currentLineColor,
                                            lineWidth = currentLineWidth
                                        )
                                        radars.add(radar)
                                        Log.d(TAG, "Parsed radar: ${radar.name} at ${radar.location}")
                                    }
                                    inNestedDocument = false
                                    nestedDocumentName = ""
                                }
                                currentDepth--
                            }
                            "Placemark" -> {
                                inPlacemark = false
                            }
                        }
                    }
                }
                eventType = parser.next()
            }
            
            Log.d(TAG, "Parsed ${radars.size} radar units from KML")
            
            Result.success(RadarFOVData(
                documentName = documentName,
                radars = radars
            ))
            
        } catch (e: Exception) {
            Log.e(TAG, "Failed to parse KML", e)
            Result.failure(e)
        }
    }
    
    /**
     * Parse Style element
     */
    private fun parseStyle(parser: XmlPullParser): StyleInfo {
        var lineColor = DEFAULT_LINE_COLOR
        var lineWidth = DEFAULT_LINE_WIDTH
        var fillColor = DEFAULT_FILL_COLOR
        var hasFill = true
        
        var depth = 1
        while (depth > 0) {
            when (parser.next()) {
                XmlPullParser.START_TAG -> {
                    depth++
                    when (parser.name) {
                        "color" -> {
                            val colorStr = parser.nextText()
                            val parsedColor = parseKMLColor(colorStr)
                            // Determine if this is for line or fill based on parent
                            lineColor = parsedColor
                            fillColor = (parsedColor and 0x00FFFFFF) or 0x33000000 // Add transparency for fill
                        }
                        "width" -> {
                            lineWidth = parser.nextText().toFloatOrNull() ?: DEFAULT_LINE_WIDTH
                        }
                        "fill" -> {
                            hasFill = parser.nextText() != "0"
                        }
                    }
                }
                XmlPullParser.END_TAG -> {
                    depth--
                }
            }
        }
        
        return StyleInfo(
            lineColor = lineColor,
            lineWidth = lineWidth,
            fillColor = if (hasFill) fillColor else Color.TRANSPARENT
        )
    }
    
    /**
     * Parse KML color format (AABBGGRR) to Android ARGB
     */
    private fun parseKMLColor(kmlColor: String): Int {
        return try {
            val color = kmlColor.trim().removePrefix("#")
            if (color.length == 8) {
                // KML format: AABBGGRR
                val aa = color.substring(0, 2).toInt(16)
                val bb = color.substring(2, 4).toInt(16)
                val gg = color.substring(4, 6).toInt(16)
                val rr = color.substring(6, 8).toInt(16)
                // Convert to ARGB
                Color.argb(aa, rr, gg, bb)
            } else if (color.length == 6) {
                // Standard RGB
                Color.parseColor("#$color")
            } else {
                DEFAULT_LINE_COLOR
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to parse color: $kmlColor", e)
            DEFAULT_LINE_COLOR
        }
    }
    
    /**
     * Parse coordinates from Point element
     */
    private fun parseCoordinates(parser: XmlPullParser): List<GeoPoint> {
        val points = mutableListOf<GeoPoint>()
        
        var depth = 1
        while (depth > 0) {
            when (parser.next()) {
                XmlPullParser.START_TAG -> {
                    depth++
                    if (parser.name == "coordinates") {
                        val coordsText = parser.nextText()
                        points.addAll(parseCoordinateString(coordsText))
                    }
                }
                XmlPullParser.END_TAG -> {
                    depth--
                }
            }
        }
        
        return points
    }
    
    /**
     * Parse coordinates from Polygon element
     */
    private fun parsePolygonCoordinates(parser: XmlPullParser): List<GeoPoint> {
        val points = mutableListOf<GeoPoint>()
        
        var depth = 1
        while (depth > 0) {
            when (parser.next()) {
                XmlPullParser.START_TAG -> {
                    depth++
                    if (parser.name == "coordinates") {
                        val coordsText = parser.nextText()
                        points.addAll(parseCoordinateString(coordsText))
                    }
                }
                XmlPullParser.END_TAG -> {
                    depth--
                }
            }
        }
        
        return points
    }
    
    /**
     * Parse coordinates from LineString element
     */
    private fun parseLineStringCoordinates(parser: XmlPullParser): List<GeoPoint> {
        val points = mutableListOf<GeoPoint>()
        
        var depth = 1
        while (depth > 0) {
            when (parser.next()) {
                XmlPullParser.START_TAG -> {
                    depth++
                    if (parser.name == "coordinates") {
                        val coordsText = parser.nextText()
                        points.addAll(parseCoordinateString(coordsText))
                    }
                }
                XmlPullParser.END_TAG -> {
                    depth--
                }
            }
        }
        
        return points
    }
    
    /**
     * Parse coordinate string "lon,lat,alt lon,lat,alt ..."
     */
    private fun parseCoordinateString(coordsText: String): List<GeoPoint> {
        val points = mutableListOf<GeoPoint>()
        
        // Split by whitespace and parse each coordinate tuple
        val tuples = coordsText.trim().split(Regex("\\s+"))
        
        for (tuple in tuples) {
            val parts = tuple.split(",")
            if (parts.size >= 2) {
                try {
                    val lon = parts[0].trim().toDouble()
                    val lat = parts[1].trim().toDouble()
                    val alt = if (parts.size >= 3) parts[2].trim().toDoubleOrNull() ?: 0.0 else 0.0
                    points.add(GeoPoint(lat, lon, alt))
                } catch (e: NumberFormatException) {
                    Log.w(TAG, "Failed to parse coordinate: $tuple")
                }
            }
        }
        
        return points
    }
    
    /**
     * Parse altitude from coordinate (already parsed in GeoPoint)
     */
    private fun parseAltitude(parser: XmlPullParser, point: GeoPoint): Double {
        return point.altitude
    }
    
    /**
     * Generate unique ID for radar from name
     */
    private fun generateRadarId(name: String): String {
        return name.replace(Regex("[^a-zA-Z0-9]"), "_").lowercase()
    }
    
    /**
     * Style information holder
     */
    private data class StyleInfo(
        val lineColor: Int,
        val lineWidth: Float,
        val fillColor: Int
    )
}
