# RadarPOC - Radar to Drone Integration App

## Overview

RadarPOC is an Android application that integrates radar tracking data with **Autel EVO 2** drone control through the Autel SDK. The app provides a simple, operator-friendly interface for:

- **Live Radar Tracking**: Displays radar tracks on Google Maps in real-time
- **Drone Camera Feed**: Shows live camera feed from connected Autel drone
- **Track Locking**: Allows operators to lock onto specific tracks and send GPS coordinates to drone
- **Multiple View Modes**: Switch between Map, Camera, and Split (Map+Camera) views

## Core Features

### 1. Live Drone Camera Feed
- Integrated with Autel SDK for live video streaming
- Full-screen and Picture-in-Picture (PiP) modes
- Automatic connection detection and status display

### 2. Google Maps with Radar Tracks
- Displays radar tracks as markers on Google Maps
- Real-time track updates from radar API
- Color-coded markers:
  - **Blue**: Normal active tracks
  - **Orange**: Locked tracks
  - **Light Blue**: Stale tracks

### 3. Track Selection & Context Menu
- Tap any track marker to open context menu:
  - **Show Info**: Display detailed track information (position, speed, classification, etc.)
  - **Lock**: Lock onto track and start sending GPS to drone
  - **Unlock**: Stop sending GPS and remove lock

### 4. GPS Command Sending
- Continuously sends locked track GPS coordinates to drone
- Updates in real-time as track moves
- Only one track can be locked at a time

### 5. Connection Status Indicators
- Top status bar shows connection status for:
  - **Radar**: Connected/Disconnected/Stale
  - **Drone**: Connected/Disconnected
  - **Camera**: Streaming/Off

### 6. Settings Configuration
- Configurable settings:
  - **Radar Base URL**: API endpoint for radar data
  - **Poll Interval**: How often to fetch radar data (seconds)
  - **Stale Timeout**: When to mark tracks as stale (seconds)
- **Test Connection** button to verify radar API connectivity

## View Modes

### Map Mode (Default)
- Full-screen Google Maps with radar track markers
- Tap markers for context menu
- No camera feed visible

### Camera Mode
- Full-screen live drone camera feed
- Displays locked track ID if any
- Map is hidden

### Split Mode (Map + Camera)
- Primary view: Google Maps (60-70% of screen)
- Secondary view: Live camera as Picture-in-Picture (bottom-right corner)
- Both views visible simultaneously

## Technical Architecture

### Data Models
- `RadarTrack`: Radar track data structure matching JSON API
- `Geolocation`: GPS coordinates, altitude, speed, heading
- `Observation`: Range, azimuth, radial velocity
- `Stats`: Classifications, RCS, amplitude
- `AppSettings`: Persistent configuration storage

### Network Layer
- `RadarApiClient`: HTTP client for radar API polling
- Automatic connection management
- Error handling and retry logic
- Configurable polling intervals

### UI Components
- `MainActivity`: Main activity with three view modes
- `SettingsActivity`: Configuration screen
- Google Maps integration for track visualization
- Autel SDK integration for drone camera feed

## Setup Instructions

### Prerequisites
1. Android Studio Arctic Fox or later
2. Android SDK 24+ (Android 7.0+)
3. Google Maps API Key
4. Autel drone and remote controller
5. Radar system with REST API endpoint

### Configuration Steps

1. **Google Maps API Key**
   - Open `app/src/main/AndroidManifest.xml`
   - Replace `YOUR_GOOGLE_MAPS_API_KEY_HERE` with your actual API key
   ```xml
   <meta-data
       android:name="com.google.android.geo.API_KEY"
       android:value="YOUR_ACTUAL_API_KEY" />
   ```

2. **Build the Project**
   ```bash
   ./gradlew build
   ```

3. **Install on Device**
   ```bash
   ./gradlew installDebug
   ```

4. **Configure Radar Settings**
   - Launch the app
   - Tap the settings icon (gear) in top-right corner
   - Enter your radar base URL (e.g., `http://192.168.1.100:8080`)
   - Set poll interval (default: 1 second)
   - Set stale timeout (default: 5 seconds)
   - Tap "Test Connection" to verify
   - Tap "Save" to apply settings

### Permissions
The app requires the following permissions:
- **INTERNET**: Network access for radar API
- **ACCESS_FINE_LOCATION**: GPS for Google Maps
- **CAMERA**: Drone camera feed
- **BLUETOOTH**: Drone connectivity
- **USB_PERMISSION**: Drone USB connection

All permissions are requested at runtime.

## Radar API Format

The app expects the radar API to return JSON in this format:

```json
{
  "result": [
    {
      "id": "357",
      "timestamp": 1767878867211,
      "geolocation": {
        "latitude": 30.091742,
        "longitude": 31.307905,
        "altitude": 7.597548,
        "speed": 6.644564,
        "heading": 156.778851
      },
      "observation": {
        "range": 28.338737,
        "radialVelocity": 5.478984,
        "azimuthAngle": 340.798245
      },
      "stats": {
        "amplitude": 40.883835,
        "rcs": 0.238509,
        "classifications": [
          {
            "type": "aerialSmall",
            "confidence": 0.031351
          },
          {
            "type": "bird",
            "confidence": 0.10707
          }
        ]
      }
    }
  ]
}
```

**API Endpoint**: `{base_url}/api/tracks.json`

## Usage Guide

### Basic Workflow

1. **Start the App**
   - App opens in Map Mode by default
   - Status bar shows connection status

2. **Configure Settings** (First Time)
   - Tap settings icon
   - Enter radar base URL
   - Test connection
   - Save settings

3. **Connect Drone**
   - Connect Autel drone via USB or wireless
   - Drone status will show "Connected" when ready
   - Camera status will show "Streaming" when video is available

4. **View Radar Tracks**
   - Tracks appear as markers on map
   - Markers update in real-time
   - Tap any marker to interact

5. **Lock a Track**
   - Tap a track marker
   - Select "Lock" from context menu
   - Marker turns orange
   - GPS coordinates are sent to drone continuously

6. **Switch View Modes**
   - Use bottom toolbar buttons:
     - **🗺 Map**: Full-screen map with tracks
     - **🎥 Camera**: Full-screen drone camera
     - **🗺+🎥 Both**: Map with camera PiP

7. **View Track Details**
   - Tap a track marker
   - Select "Show Info"
   - View detailed information

8. **Unlock Track**
   - Tap locked track marker
   - Select "Unlock"
   - GPS sending stops

## Track Staleness

Tracks are marked as "stale" when:
- Time since last update exceeds stale timeout setting
- Stale tracks appear in light blue color
- Stale tracks are labeled with "(Stale)" suffix

## Troubleshooting

### Radar Not Connecting
- Verify radar base URL is correct
- Check network connectivity
- Ensure radar API is running and accessible
- Try "Test Connection" in settings

### Drone Not Connecting
- Ensure drone is powered on
- Check USB cable connection
- Verify Bluetooth/WiFi connection
- Check drone battery level

### Map Not Loading
- Verify Google Maps API key is configured
- Check internet connection
- Ensure location permissions are granted

### No Camera Feed
- Verify drone is connected
- Ensure camera permissions are granted
- Check if drone camera is functional
- Try switching to Camera Mode

## Development Notes

### GPS Command Sending
The current implementation includes a placeholder for GPS command sending:

```kotlin
private fun sendGPSToDrone(track: RadarTrack) {
    if (!isDroneConnected) return
    
    // TODO: Implement GPS command sending via Autel SDK
    // This depends on the specific API for sending GPS waypoints/targets
    // Example (pseudo-code):
    // autelProduct?.flightController?.setTargetLocation(
    //     track.geolocation.latitude,
    //     track.geolocation.longitude,
    //     track.geolocation.altitude
    // )
    
    Log.d(TAG, "Sending GPS to drone: ${track.geolocation.latitude}, ${track.geolocation.longitude}")
}
```

**Note**: You need to implement the actual GPS command based on your specific Autel SDK version and drone model's API.

### Dependencies
- Autel SDK (autel-sdk-release.aar)
- Google Maps Android API
- OkHttp for networking
- Gson for JSON parsing
- Kotlin Coroutines for async operations

## Project Structure

```
RadarPOC/
├── app/
│   ├── src/
│   │   ├── main/
│   │   │   ├── java/com/ccs/radarpoc/
│   │   │   │   ├── MainActivity.kt          # Main app activity
│   │   │   │   ├── SettingsActivity.kt      # Settings screen
│   │   │   │   ├── data/
│   │   │   │   │   ├── RadarTrack.kt        # Data models
│   │   │   │   │   └── AppSettings.kt       # Settings manager
│   │   │   │   └── network/
│   │   │   │       └── RadarApiClient.kt    # Radar API client
│   │   │   ├── res/
│   │   │   │   ├── layout/
│   │   │   │   │   ├── activity_main.xml    # Main layout
│   │   │   │   │   └── activity_settings.xml # Settings layout
│   │   │   │   └── values/
│   │   │   │       └── colors.xml           # Color definitions
│   │   │   └── AndroidManifest.xml          # App manifest
│   │   └── libs/
│   │       └── autel-sdk-release.aar        # Autel SDK library
│   └── build.gradle.kts                     # App build config
└── README.md                                # This file
```

## License

This is a Proof-of-Concept application. Check with your organization for licensing terms.

## Support

For issues or questions:
1. Check troubleshooting section above
2. Review Autel SDK documentation
3. Verify radar API is functioning correctly
4. Check Android Studio logcat for error messages

## Version History

- **v1.0** - Initial POC release
  - Basic radar tracking visualization
  - Drone camera integration
  - Three view modes
  - Track locking and GPS sending
  - Settings configuration
