# OSMDroid Migration Guide

## Summary of Changes

### 1. Dependencies ✅ DONE
- Removed Google Maps SDK
- Added OSMDroid 6.1.18

### 2. Layout ✅ DONE
- Replaced `SupportMapFragment` with `org.osmdroid.views.MapView`

### 3. MainActivity Changes NEEDED

#### Import Changes
```kotlin
// REMOVE Google Maps imports:
import com.google.android.gms.maps.*
import com.google.android.gms.maps.model.*

// ADD OSMDroid imports:
import org.osmdroid.config.Configuration
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker
import org.osmdroid.views.overlay.mylocation.GpsMyLocationProvider
import org.osmdroid.views.overlay.mylocation.MyLocationNewOverlay
```

#### Class Declaration
```kotlin
// BEFORE:
class MainActivity : AppCompatActivity(), OnMapReadyCallback

// AFTER:
class MainActivity : AppCompatActivity()
```

#### Map Variables
```kotlin
// BEFORE:
private var googleMap: GoogleMap? = null
private val trackMarkers = mutableMapOf<String, Marker>()
private var droneMarker: Marker? = null

// AFTER:
private var mapView: MapView? = null
private val trackMarkers = mutableMapOf<String, Marker>()
private var droneMarker: Marker? = null
```

#### Map Initialization
```kotlin
// BEFORE setupGoogleMaps():
private fun setupGoogleMaps() {
    val mapFragment = supportFragmentManager
        .findFragmentById(R.id.mapFragment) as SupportMapFragment
    mapFragment.getMapAsync(this)
}

// AFTER setupOSMDroid():
private fun setupOSMDroid() {
    // Configure OSMDroid
    Configuration.getInstance().load(
        applicationContext,
        getSharedPreferences("osmdroid", MODE_PRIVATE)
    )
    
    // Get MapView reference
    mapView = binding.mapView
    
    // Basic configuration
    mapView?.apply {
        setTileSource(TileSourceFactory.MAPNIK)
        setMultiTouchControls(true)
        controller.setZoom(DEFAULT_ZOOM.toDouble())
        controller.setCenter(GeoPoint(DEFAULT_LAT, DEFAULT_LNG))
        
        // Enable offline mode
        setUseDataConnection(false) // Set to true if online needed
        
        // Set tap listener
        setOnTouchListener { _, event ->
            if (event.action == MotionEvent.ACTION_UP) {
                if (bottomSheetBehavior.state != BottomSheetBehavior.STATE_HIDDEN) {
                    bottomSheetBehavior.state = BottomSheetBehavior.STATE_HIDDEN
                }
            }
            false
        }
    }
    
    // Add marker click handling overlay
    setupMarkerClickListener()
}
```

#### Coordinate Conversion
```kotlin
// BEFORE:
val position = LatLng(latitude, longitude)

// AFTER:
val position = GeoPoint(latitude, longitude)
```

#### Camera/View Movement
```kotlin
// BEFORE:
googleMap?.moveCamera(CameraUpdateFactory.newLatLngZoom(position, zoom))
googleMap?.animateCamera(CameraUpdateFactory.newLatLng(position))

// AFTER:
mapView?.controller?.setZoom(zoom.toDouble())
mapView?.controller?.setCenter(position)
mapView?.controller?.animateTo(position)
```

#### Marker Creation
```kotlin
// BEFORE (Google Maps):
val marker = map.addMarker(
    MarkerOptions()
        .position(position)
        .title("Track 357")
        .icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_RED))
)
marker?.tag = trackId

// AFTER (OSMDroid):
val marker = Marker(mapView).apply {
    position = GeoPoint(latitude, longitude)
    title = "Track 357"
    setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
    icon = ContextCompat.getDrawable(this@MainActivity, R.drawable.ic_marker_red)
    // Store track ID
    id = trackId
}
mapView?.overlays?.add(marker)
mapView?.invalidate()
```

#### Marker Updates
```kotlin
// BEFORE:
marker.position = LatLng(newLat, newLng)

// AFTER:
marker.position = GeoPoint(newLat, newLng)
mapView?.invalidate() // Must call to refresh
```

#### Marker Removal
```kotlin
// BEFORE:
marker.remove()

// AFTER:
mapView?.overlays?.remove(marker)
mapView?.invalidate()
```

### 4. Lifecycle Methods

```kotlin
override fun onResume() {
    super.onResume()
    mapView?.onResume()
    viewModel.startRadarPolling()
}

override fun onPause() {
    super.onPause()
    mapView?.onPause()
    viewModel.stopRadarPolling()
}

override fun onDestroy() {
    super.onDestroy()
    mapView?.onDetach()
    cleanupCameraView()
    TrackMarkerHelper.clearCache()
}
```

### 5. Offline Map Configuration

#### Download Offline Tiles (for field deployment):

```kotlin
// In settings or separate activity
private fun downloadOfflineTiles(
    north: Double, south: Double,
    east: Double, west: Double,
    minZoom: Int = 10,
    maxZoom: Int = 17
) {
    val cacheManager = CacheManager(mapView)
    val bb = BoundingBox(north, east, south, west)
    
    cacheManager.downloadAreaAsync(
        this, bb, minZoom, maxZoom,
        object : CacheManager.CacheManagerCallback {
            override fun onTaskComplete() {
                Toast.makeText(this@MainActivity, "Tiles downloaded!", Toast.LENGTH_SHORT).show()
            }
            
            override fun onTaskFailed(errors: Int) {
                Toast.makeText(this@MainActivity, "Download failed: $errors errors", Toast.LENGTH_SHORT).show()
            }
            
            override fun updateProgress(progress: Int, currentZoomLevel: Int, zoomMin: Int, zoomMax: Int) {
                // Update progress UI
            }
            
            override fun downloadStarted() {
                // Show progress dialog
            }
            
            override fun setPossibleTilesInArea(total: Int) {
                // Total tiles to download
            }
        }
    )
}
```

### 6. Key Differences Table

| Feature | Google Maps | OSMDroid |
|---------|-------------|----------|
| **Map Instance** | `GoogleMap` | `MapView` |
| **Coordinates** | `LatLng(lat, lng)` | `GeoPoint(lat, lng)` |
| **Marker** | Auto-managed | Manual overlay management |
| **Camera Move** | `moveCamera(CameraUpdateFactory...)` | `controller.setCenter(...)` |
| **Zoom** | `newLatLngZoom(pos, zoom)` | `controller.setZoom(zoom.toDouble())` |
| **Marker Click** | `setOnMarkerClickListener` | Custom overlay or touch handling |
| **Refresh** | Automatic | Call `mapView.invalidate()` |
| **Lifecycle** | Fragment handles | Call onResume/onPause/onDetach |

### 7. Marker Click Handling

OSMDroid requires custom click handling:

```kotlin
private fun setupMarkerClickListener() {
    mapView?.overlays?.add(object : org.osmdroid.views.overlay.Overlay() {
        override fun onSingleTapConfirmed(e: MotionEvent, mapView: MapView): Boolean {
            val projection = mapView.projection
            val geoPoint = projection.fromPixels(e.x.toInt(), e.y.toInt()) as GeoPoint
            
            // Find closest marker
            var closestMarker: Marker? = null
            var minDistance = Float.MAX_VALUE
            
            mapView.overlays.filterIsInstance<Marker>().forEach { marker ->
                val markerPixel = projection.toPixels(marker.position, null)
                val distance = kotlin.math.sqrt(
                    (markerPixel.x - e.x).pow(2) + (markerPixel.y - e.y).pow(2).toFloat()
                )
                if (distance < 50 && distance < minDistance) { // 50px threshold
                    closestMarker = marker
                    minDistance = distance
                }
            }
            
            closestMarker?.let { marker ->
                val trackId = marker.id
                viewModel.onEvent(MainUiEvent.TrackSelected(trackId))
                showTrackBottomSheet()
                return true
            }
            
            return false
        }
    })
}
```

### 8. Benefits of Migration

✅ **Fully offline** - No internet required  
✅ **No API keys** - No costs  
✅ **Custom tiles** - Can use tactical/custom map sources  
✅ **No restrictions** - Suitable for military/defense use  
✅ **Open source** - Complete control  

### 9. Testing Checklist

- [ ] Map loads and displays correctly
- [ ] Markers appear for radar tracks
- [ ] Drone marker shows (green)
- [ ] Tap marker shows bottom sheet
- [ ] Lock track updates marker appearance
- [ ] Stale tracks shown differently
- [ ] Camera movement smooth
- [ ] Offline mode works (disable network)
- [ ] App doesn't crash on rotate
- [ ] Memory usage acceptable

### 10. Offline Deployment Instructions

1. **Pre-download tiles** for deployment area
2. Copy tile cache to device: `/sdcard/osmdroid/tiles/`
3. Set `setUseDataConnection(false)` in app
4. Deploy with tile cache included

Would you like me to:
1. Apply these changes to MainActivity now?
2. Create a helper class for OSMDroid marker management?
3. Add offline tile downloader activity?
