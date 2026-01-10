# Offline Map Management Guide

## Overview
The RadarPOC app now supports offline map functionality using osmdroid with SQLite map files. Users can import, manage, and switch between custom offline map tiles through the Settings page.

## Features
- ✅ Import custom `.sqlite` map files
- ✅ Switch between multiple offline maps
- ✅ Delete unused map files
- ✅ View map file details (name, size, date)
- ✅ Fallback to default online tiles
- ✅ File validation for SQLite format

## How to Use

### 1. Creating Offline Map Files

You can create offline map files using various tools:

#### **Mobile Atlas Creator (MOBAC)** - Recommended
1. Download MOBAC from: https://mobac.sourceforge.io/
2. Select your desired area on the map
3. Choose zoom levels (recommended: 10-18)
4. Select "**OSMDroid SQLite**" as the atlas format
5. Click "Create Atlas"
6. Transfer the generated `.sqlite` file to your device

#### **OsmAnd Map Creator**
1. Download OsmAnd Map Creator
2. Select area and zoom levels
3. Export as SQLite format
4. Transfer to device

#### **QGIS with QTiles Plugin**
1. Install QGIS and QTiles plugin
2. Load your desired map layers
3. Export as SQLite/MBTiles format
4. Transfer to device

### 2. Importing Map Files to the App

1. Open the RadarPOC app
2. Tap the **Settings** button (⚙️ icon)
3. Scroll to the **"Offline Maps"** section
4. Tap **"Add Map File"** button
5. Navigate to your `.sqlite` map file location
6. Select the file - it will be validated and imported
7. The map will appear in the list

### 3. Activating a Map

1. In the Settings page, find your imported map in the list
2. Tap the **radio button** or anywhere on the map card
3. The selected map becomes active (radio button checked)
4. Return to the main screen to see your custom map

### 4. Switching Maps

- To use a different map: Tap another map from the list
- To use default online tiles: Tap the currently active map to deselect it
- The **"Current Map Source"** card shows which map is active

### 5. Deleting Maps

1. Tap the **red delete button** (🗑️) on any map card
2. Confirm the deletion
3. If the deleted map was active, the app switches to default tiles

## Technical Details

### File Storage Location
- Maps are stored in: `/Android/data/com.ccs.radarpoc/files/osmdroid/maps/`
- Files are managed by the app and isolated from other apps

### File Format Requirements
- **Format**: SQLite database (`.sqlite` extension)
- **Structure**: Must contain valid SQLite header ("SQLite format 3")
- **Tile Schema**: Compatible with osmdroid tile structure
- **Recommended**: Use MOBAC "OSMDroid SQLite" format

### Map File Size Considerations
- Small area (city): ~50-200 MB
- Large area (country): ~500 MB - 2 GB
- Multiple zoom levels increase file size exponentially
- Balance coverage area vs. file size for your use case

### Zoom Level Guidelines
- **Level 10-13**: Region/country overview
- **Level 14-16**: City/town details
- **Level 17-19**: Street-level detail
- **Level 20+**: Very high detail (rarely needed, very large files)

## Troubleshooting

### Map Not Loading
1. Check file format - must be `.sqlite`
2. Verify file was created with osmdroid-compatible tool
3. Try re-importing the file
4. Check the "Current Map Source" in Settings

### Import Failed
- **"Invalid file format"**: File is not a valid SQLite database
- **"File already exists"**: A map with the same name is already imported
- **"Source file does not exist"**: The selected file cannot be accessed

### Map Appears Blank
1. Ensure the map covers your current GPS location
2. Check zoom level matches available tiles in the map
3. Verify the map was created correctly in MOBAC
4. Try switching back to default tiles to verify app functionality

### Performance Issues
- Large map files may cause slower loading
- Consider splitting large areas into multiple smaller maps
- Close and restart the app if performance degrades

## Best Practices

1. **Plan Your Coverage**
   - Identify operational areas before creating maps
   - Include buffer zones around key areas
   - Consider flight range of your drones

2. **Optimize Zoom Levels**
   - Use only necessary zoom levels (10-18 typically sufficient)
   - Higher zoom = larger file size
   - Test with a small area first

3. **Organize Multiple Maps**
   - Use descriptive names (e.g., "Cairo_Downtown", "Alex_Port")
   - Keep file sizes manageable (<500 MB each)
   - Delete unused maps to free space

4. **Regular Updates**
   - Refresh maps periodically as areas change
   - OSM data is updated frequently
   - Replace old map files with updated versions

5. **Backup**
   - Keep original `.sqlite` files on your computer
   - Easy to re-import if device is reset

## Integration with osmdroid

The app uses osmdroid library for map rendering:
- **Library Version**: 6.1.18
- **Tile Source**: Custom XYTileSource for SQLite files
- **Offline Mode**: Enabled by default
- **Cache**: Managed automatically by osmdroid

## Support

For issues or questions:
1. Check this guide first
2. Verify your map file was created correctly
3. Test with a small sample map
4. Report issues with map file details and error messages

## Example: Creating a Sample Map with MOBAC

```
1. Launch Mobile Atlas Creator
2. Settings → Atlas Format → "OSMDroid SQLite"
3. Zoom to your target area (e.g., Cairo, Egypt)
4. Zoom levels: Select 10, 12, 14, 16, 18
5. Click "Selection" → "Add Selection" → Draw rectangle
6. Name: "Cairo_Test"
7. Click "Create Atlas"
8. Wait for completion
9. Find Cairo_Test.sqlite in output folder
10. Transfer to device and import via app
```

---

**Note**: This feature requires device storage access. The app uses Android's Storage Access Framework (SAF) for secure file access, so no special permissions are needed beyond what's already granted.
