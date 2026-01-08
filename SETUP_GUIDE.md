# RadarPOC Setup Guide

## Quick Start Guide

This guide will help you set up and run the RadarPOC application.

## Prerequisites

Before you begin, ensure you have:

1. **Android Studio** (Latest version recommended)
2. **JDK 11** or later
3. **Android Device** with Android 7.0 (API 24) or higher
4. **Google Maps API Key** (See instructions below)
5. **Autel Drone** and remote controller
6. **Radar System** with REST API endpoint

## Step 1: Clone/Open the Project

1. Open Android Studio
2. Select "Open an Existing Project"
3. Navigate to `RadarPOC` folder
4. Click "OK"

## Step 2: Get Google Maps API Key

1. Go to [Google Cloud Console](https://console.cloud.google.com/)
2. Create a new project or select existing one
3. Enable **Maps SDK for Android**
4. Go to **Credentials** → **Create Credentials** → **API Key**
5. Copy the API key
6. (Optional) Restrict the key to your app's package name: `com.ccs.radarpoc`

## Step 3: Configure API Key

1. Open `app/src/main/AndroidManifest.xml`
2. Find this line:
   ```xml
   <meta-data
       android:name="com.google.android.geo.API_KEY"
       android:value="YOUR_GOOGLE_MAPS_API_KEY_HERE" />
   ```
3. Replace `YOUR_GOOGLE_MAPS_API_KEY_HERE` with your actual API key
4. Save the file

## Step 4: Sync and Build

1. In Android Studio, click **File** → **Sync Project with Gradle Files**
2. Wait for Gradle sync to complete
3. Click **Build** → **Make Project**
4. Fix any errors if they appear

## Step 5: Connect Android Device

1. Enable **Developer Options** on your Android device:
   - Go to Settings → About Phone
   - Tap "Build Number" 7 times
   - Go back to Settings → Developer Options
   - Enable "USB Debugging"

2. Connect device via USB cable
3. Allow USB debugging when prompted

## Step 6: Install the App

1. In Android Studio, select your device from the device dropdown
2. Click the **Run** button (green play icon) or press `Shift+F10`
3. Wait for the app to install and launch

## Step 7: Configure Radar Settings

1. On the device, tap the **Settings** icon (gear) in the top-right corner
2. Enter your radar configuration:
   - **Radar Base URL**: e.g., `http://192.168.1.100:8080`
   - **Poll Interval**: `1` (seconds)
   - **Stale Timeout**: `5` (seconds)
3. Tap **Test Connection** to verify radar connectivity
4. If successful, tap **Save**

## Step 8: Connect the Drone

### For USB Connection:
1. Connect Autel remote controller to Android device via USB
2. Turn on the drone
3. Wait for connection to establish
4. Check status bar - should show "🟢 Drone: Connected"

### For Wireless Connection:
1. Enable WiFi/Bluetooth on Android device
2. Turn on the drone
3. Connect to drone's WiFi network
4. Wait for connection to establish
5. Check status bar - should show "🟢 Drone: Connected"

## Step 9: Verify Functionality

### Test Radar Connection:
- Status bar should show "🟢 Radar: Connected"
- Radar tracks should appear as markers on the map

### Test Drone Connection:
- Status bar should show "🟢 Drone: Connected"
- Camera status should show "🟢 Camera: Streaming"

### Test View Modes:
1. Tap **🗺 Map** - should show full-screen map
2. Tap **🎥 Camera** - should show full-screen drone camera feed
3. Tap **🗺+🎥 Both** - should show map with camera PiP

### Test Track Interaction:
1. Tap any track marker on the map
2. Select **Show Info** - should display track details
3. Select **Lock** - marker should turn orange
4. Check logcat for GPS coordinates being sent

## Troubleshooting

### Issue: Gradle Sync Failed

**Solutions:**
- Check internet connection
- Update Android Studio to latest version
- Invalidate caches: **File** → **Invalidate Caches / Restart**
- Delete `.gradle` folder and sync again

### Issue: API Key Not Working

**Solutions:**
- Verify API key is correct
- Check if Maps SDK for Android is enabled
- Wait a few minutes after creating new key
- Try unrestricted key first, then add restrictions

### Issue: Autel SDK Not Found

**Solutions:**
- Verify `app/libs/autel-sdk-release.aar` exists
- Check that AndroidSample-master SDK was copied correctly
- Rebuild project: **Build** → **Rebuild Project**

### Issue: App Crashes on Launch

**Solutions:**
- Check logcat for error messages
- Verify all permissions are granted
- Ensure device meets minimum SDK requirements (API 24+)
- Clear app data: Settings → Apps → RadarPOC → Clear Data

### Issue: Map Not Showing

**Solutions:**
- Verify Google Maps API key is configured
- Grant location permissions
- Check internet connection
- Verify device has Google Play Services

### Issue: Radar Not Connecting

**Solutions:**
- Verify radar base URL is correct
- Check network connectivity between device and radar
- Ensure radar API is running
- Test URL in web browser first
- Check firewall settings

### Issue: Drone Not Connecting

**Solutions:**
- Verify USB cable is working (data cable, not just charging)
- Check USB debugging is enabled
- Try different USB port
- Restart drone and remote controller
- Check drone battery level

### Issue: No Camera Feed

**Solutions:**
- Verify drone is connected first
- Grant camera permissions
- Switch to Camera Mode
- Restart the app
- Check if drone camera is functional

## Build Variants

### Debug Build (for testing):
```bash
./gradlew assembleDebug
```

### Release Build (for production):
```bash
./gradlew assembleRelease
```

Note: Release builds require signing configuration.

## Permissions Setup

The app will request these permissions at runtime:
- Location (for Google Maps)
- Camera (if needed for specific features)
- Storage (if SDK requires it)

Grant all permissions when prompted for full functionality.

## Network Requirements

### For Radar Connection:
- Device must be on same network as radar system
- OR radar system must be accessible via public IP
- No proxy or firewall blocking HTTP requests
- Port access to radar API endpoint

### For Drone Connection:
- USB connection: No special network requirements
- Wireless connection: Device must connect to drone's WiFi network

## Performance Tips

1. **Battery Optimization**: Disable battery optimization for the app
   - Settings → Battery → Battery Optimization → RadarPOC → Don't optimize

2. **Background Restrictions**: Allow background data
   - Settings → Apps → RadarPOC → Data Usage → Allow background data

3. **WiFi Sleep**: Keep WiFi on during sleep
   - Settings → WiFi → Advanced → Keep WiFi on during sleep → Always

## Development Mode

For development and testing:

1. **Enable Verbose Logging**:
   - Check logcat with filter: `tag:RadarPOC`
   - Or use: `adb logcat -s RadarApiClient MainActivity`

2. **Monitor Network Traffic**:
   - Use Android Studio Network Profiler
   - Or use Charles Proxy / Wireshark

3. **Debug Radar API**:
   - Test endpoint manually: `curl http://YOUR_RADAR_IP:PORT/api/tracks.json`
   - Use Postman or similar tools

## Next Steps

After successful setup:

1. **Familiarize with UI**: Explore all three view modes
2. **Test Track Locking**: Practice locking and unlocking tracks
3. **Monitor Status Bar**: Watch connection indicators
4. **Review Logs**: Check logcat for any warnings or errors
5. **Adjust Settings**: Fine-tune poll interval and stale timeout

## Additional Resources

- **Autel SDK Documentation**: Refer to official Autel SDK docs
- **Google Maps Android API**: https://developers.google.com/maps/documentation/android-sdk
- **Android Developer Guide**: https://developer.android.com/

## Support

If you encounter issues not covered in this guide:

1. Check the main README.md troubleshooting section
2. Review Android Studio logcat for error messages
3. Verify all configuration steps were completed
4. Check that radar API is functioning correctly
5. Test with a different Android device if available

## Checklist

Before deploying to production:

- [ ] Google Maps API key configured
- [ ] Radar base URL configured and tested
- [ ] Drone connection tested
- [ ] All three view modes tested
- [ ] Track locking tested
- [ ] GPS sending verified (check logs)
- [ ] All permissions granted
- [ ] Network connectivity verified
- [ ] Battery optimization disabled
- [ ] Performance tested with multiple tracks

## Configuration File Reference

Key files to configure:

1. `app/src/main/AndroidManifest.xml` - API key, permissions
2. `app/build.gradle.kts` - Dependencies, SDK versions
3. Settings screen in app - Radar URL, polling settings

That's it! You should now have a fully functional RadarPOC application.
