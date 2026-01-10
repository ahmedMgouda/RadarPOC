package com.ccs.radarpoc

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.OpenableColumns
import android.view.MenuItem
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.ccs.radarpoc.data.AppSettings
import com.ccs.radarpoc.network.RadarApiClient
import com.ccs.radarpoc.ui.MapFileAdapter
import com.ccs.radarpoc.util.MapFileManager
import com.ccs.radarpoc.util.MapTileProviderFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

class SettingsActivity : AppCompatActivity() {
    
    private lateinit var etRadarBaseUrl: EditText
    private lateinit var etPollInterval: EditText
    private lateinit var etStaleTimeout: EditText
    private lateinit var etMissionUpdateInterval: EditText
    private lateinit var btnTestConnection: Button
    private lateinit var btnSave: Button
    private lateinit var tvTestResult: TextView
    
    // Map management views
    private lateinit var rvMapFiles: RecyclerView
    private lateinit var tvEmptyMaps: TextView
    private lateinit var btnAddMapFile: Button
    private lateinit var btnMapInstructions: Button
    private lateinit var tvCurrentMapSource: TextView
    
    // Map Display Switches
    private lateinit var switchShowCompass: com.google.android.material.switchmaterial.SwitchMaterial
    private lateinit var switchShowZoomButtons: com.google.android.material.switchmaterial.SwitchMaterial
    private lateinit var switchShowScaleBar: com.google.android.material.switchmaterial.SwitchMaterial
    private lateinit var switchEnableMapRotation: com.google.android.material.switchmaterial.SwitchMaterial
    
    private lateinit var appSettings: AppSettings
    private lateinit var mapFileAdapter: MapFileAdapter
    
    // File picker launcher
    private val filePickerLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            result.data?.data?.let { uri ->
                handleSelectedFile(uri)
            }
        }
    }
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)
        
        // Enable back button
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = "Settings"
        
        appSettings = AppSettings(this)
        
        initViews()
        loadSettings()
        setupListeners()
    }
    
    private fun initViews() {
        etRadarBaseUrl = findViewById(R.id.etRadarBaseUrl)
        etPollInterval = findViewById(R.id.etPollInterval)
        etStaleTimeout = findViewById(R.id.etStaleTimeout)
        etMissionUpdateInterval = findViewById(R.id.etMissionUpdateInterval)
        btnTestConnection = findViewById(R.id.btnTestConnection)
        btnSave = findViewById(R.id.btnSave)
        tvTestResult = findViewById(R.id.tvTestResult)
        
        // Map management views
        rvMapFiles = findViewById(R.id.rvMapFiles)
        tvEmptyMaps = findViewById(R.id.tvEmptyMaps)
        btnAddMapFile = findViewById(R.id.btnAddMapFile)
        btnMapInstructions = findViewById(R.id.btnMapInstructions)
        tvCurrentMapSource = findViewById(R.id.tvCurrentMapSource)
        
        // Map Display Switches
        switchShowCompass = findViewById(R.id.switchShowCompass)
        switchShowZoomButtons = findViewById(R.id.switchShowZoomButtons)
        switchShowScaleBar = findViewById(R.id.switchShowScaleBar)
        switchEnableMapRotation = findViewById(R.id.switchEnableMapRotation)
        
        setupMapFilesRecyclerView()
    }
    
    private fun loadSettings() {
        // Radar settings
        etRadarBaseUrl.setText(appSettings.radarBaseUrl)
        etPollInterval.setText(appSettings.pollInterval.toString())
        etStaleTimeout.setText(appSettings.staleTimeout.toString())
        
        // Drone settings
        etMissionUpdateInterval.setText(appSettings.missionUpdateInterval.toString())
        
        // Map Display settings
        switchShowCompass.isChecked = appSettings.showCompass
        switchShowZoomButtons.isChecked = appSettings.showZoomButtons
        switchShowScaleBar.isChecked = appSettings.showScaleBar
        switchEnableMapRotation.isChecked = appSettings.enableMapRotation
    }
    
    private fun setupListeners() {
        btnSave.setOnClickListener {
            saveSettings()
        }
        
        btnTestConnection.setOnClickListener {
            testConnection()
        }
        
        btnAddMapFile.setOnClickListener {
            openFilePicker()
        }
        
        btnMapInstructions.setOnClickListener {
            showInstructions()
        }
    }
    
    /**
     * Setup RecyclerView for map files
     */
    private fun setupMapFilesRecyclerView() {
        mapFileAdapter = MapFileAdapter(
            onMapSelected = { mapFile ->
                showMapFileInfo(mapFile)
            },
            onMapDeleted = { mapFile ->
                confirmDeleteMapFile(mapFile)
            }
        )
        
        rvMapFiles.apply {
            layoutManager = LinearLayoutManager(this@SettingsActivity)
            adapter = mapFileAdapter
        }
        
        loadMapFiles()
    }
    
    /**
     * Load and display available map files
     */
    private fun loadMapFiles() {
        lifecycleScope.launch {
            val mapFiles = MapFileManager.scanForMapFiles(this@SettingsActivity)
            
            // Read metadata for each file
            val mapFilesWithInfo = mapFiles.map { mapFile ->
                val info = withContext(Dispatchers.IO) {
                    MapTileProviderFactory.readMBTilesInfo(File(mapFile.path))
                }
                Pair(mapFile, info)
            }
            
            if (mapFiles.isEmpty()) {
                rvMapFiles.visibility = View.GONE
                tvEmptyMaps.visibility = View.VISIBLE
            } else {
                rvMapFiles.visibility = View.VISIBLE
                tvEmptyMaps.visibility = View.GONE
                mapFileAdapter.submitList(mapFiles)
            }
            
            updateCurrentMapSourceDisplay(mapFilesWithInfo)
        }
    }
    
    /**
     * Update current map source display - shows summary of ALL loaded maps
     */
    private fun updateCurrentMapSourceDisplay(mapFilesWithInfo: List<Pair<MapFileManager.MapFile, MapTileProviderFactory.MBTilesInfo?>>) {
        if (mapFilesWithInfo.isEmpty()) {
            tvCurrentMapSource.text = "No offline maps (using online tiles)"
            return
        }
        
        val loadedInfos = mapFilesWithInfo.mapNotNull { it.second }
        
        if (loadedInfos.isEmpty()) {
            tvCurrentMapSource.text = "${mapFilesWithInfo.size} file(s) - metadata unavailable"
            return
        }
        
        // Calculate combined stats
        val totalSize = mapFilesWithInfo.sumOf { it.first.sizeBytes }
        val totalSizeMB = totalSize / (1024.0 * 1024.0)
        val minZoom = loadedInfos.minOfOrNull { it.minZoom } ?: 0
        val maxZoom = loadedInfos.maxOfOrNull { it.maxZoom } ?: 0
        
        tvCurrentMapSource.text = buildString {
            append("${mapFilesWithInfo.size} file(s)")
            append(" • ${String.format("%.1f", totalSizeMB)} MB")
            append(" • Zoom $minZoom-$maxZoom")
        }
    }
    
    /**
     * Open file picker to select map file
     */
    private fun openFilePicker() {
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "*/*"
            putExtra(Intent.EXTRA_MIME_TYPES, arrayOf("application/x-sqlite3", "application/octet-stream", "*/*"))
        }
        filePickerLauncher.launch(intent)
    }
    
    /**
     * Handle selected file from picker with detailed logging
     */
    private fun handleSelectedFile(uri: Uri) {
        lifecycleScope.launch {
            try {
                // Show progress
                Toast.makeText(this@SettingsActivity, "Importing map file...", Toast.LENGTH_SHORT).show()
                
                val result = withContext(Dispatchers.IO) {
                    // Get file name from URI
                    val fileName = getFileName(uri) ?: "map.mbtiles"
                    android.util.Log.d("SettingsActivity", "Selected file: $fileName from URI: $uri")
                    
                    // Create temporary file
                    val tempFile = File(cacheDir, fileName)
                    android.util.Log.d("SettingsActivity", "Temp file path: ${tempFile.absolutePath}")
                    
                    // Copy from URI to temp file
                    var bytesCopied = 0L
                    contentResolver.openInputStream(uri)?.use { input ->
                        FileOutputStream(tempFile).use { output ->
                            bytesCopied = input.copyTo(output)
                        }
                    }
                    
                    android.util.Log.d("SettingsActivity", "Copied $bytesCopied bytes to temp file")
                    android.util.Log.d("SettingsActivity", "Temp file size: ${tempFile.length()} bytes, exists: ${tempFile.exists()}")
                    
                    // Validate file
                    if (!MapFileManager.isValidMapFile(tempFile)) {
                        val errorMsg = "Invalid map file format.\n" +
                                      "File: $fileName\n" +
                                      "Size: ${tempFile.length()} bytes\n" +
                                      "Extension: ${tempFile.extension}\n" +
                                      "Supported: .mbtiles, .sqlite, .db"
                        android.util.Log.e("SettingsActivity", errorMsg)
                        tempFile.delete()
                        return@withContext Result.failure<MapFileManager.MapFile>(
                            Exception(errorMsg)
                        )
                    }
                    
                    // Read and validate MBTiles metadata
                    val mbtilesInfo = MapTileProviderFactory.readMBTilesInfo(tempFile)
                    if (mbtilesInfo == null) {
                        android.util.Log.w("SettingsActivity", "Could not read MBTiles metadata, but file may still be valid")
                    } else {
                        android.util.Log.d("SettingsActivity", "MBTiles info: zoom ${mbtilesInfo.minZoom}-${mbtilesInfo.maxZoom}, format: ${mbtilesInfo.format}")
                    }
                    
                    android.util.Log.d("SettingsActivity", "File validation passed, importing...")
                    
                    // Import file
                    val importResult = MapFileManager.importMapFile(this@SettingsActivity, tempFile)
                    
                    // Clean up temp file
                    tempFile.delete()
                    
                    importResult
                }
                
                result.onSuccess { mapFile ->
                    // Read metadata for the imported file
                    val info = withContext(Dispatchers.IO) {
                        MapTileProviderFactory.readMBTilesInfo(File(mapFile.path))
                    }
                    
                    val infoText = if (info != null) {
                        "Zoom: ${info.minZoom}-${info.maxZoom}"
                    } else {
                        mapFile.sizeFormatted
                    }
                    
                    Toast.makeText(
                        this@SettingsActivity, 
                        "✓ Map imported: ${mapFile.displayName}\n$infoText", 
                        Toast.LENGTH_LONG
                    ).show()
                    loadMapFiles()
                }.onFailure { error ->
                    Toast.makeText(
                        this@SettingsActivity, 
                        "Import failed:\n${error.message}", 
                        Toast.LENGTH_LONG
                    ).show()
                }
                
            } catch (e: Exception) {
                android.util.Log.e("SettingsActivity", "Exception during import", e)
                Toast.makeText(
                    this@SettingsActivity, 
                    "Error: ${e.javaClass.simpleName}\n${e.message}", 
                    Toast.LENGTH_LONG
                ).show()
            }
        }
    }
    
    /**
     * Get file name from URI
     */
    private fun getFileName(uri: Uri): String? {
        var fileName: String? = null
        contentResolver.query(uri, null, null, null, null)?.use { cursor ->
            val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            if (cursor.moveToFirst() && nameIndex >= 0) {
                fileName = cursor.getString(nameIndex)
            }
        }
        return fileName
    }
    
    /**
     * Show detailed info about a map file
     */
    private fun showMapFileInfo(mapFile: MapFileManager.MapFile) {
        lifecycleScope.launch {
            val info = withContext(Dispatchers.IO) {
                MapTileProviderFactory.readMBTilesInfo(File(mapFile.path))
            }
            
            val message = buildString {
                append("File: ${mapFile.name}\n")
                append("Size: ${mapFile.formattedSize}\n")
                append("\n")
                
                if (info != null) {
                    append("─────────────────\n")
                    append("MBTiles Metadata:\n")
                    append("─────────────────\n")
                    append("Name: ${info.name}\n")
                    append("Format: ${info.format.uppercase()}\n")
                    append("Zoom Levels: ${info.minZoom} - ${info.maxZoom}\n")
                    append("Tile Scheme: ${info.scheme.uppercase()}\n")
                    if (info.bounds != null) {
                        append("Bounds: ${info.bounds}\n")
                    }
                } else {
                    append("(Metadata not available)\n")
                }
                
                append("\n")
                append("─────────────────\n")
                append("Note: All map files are automatically\n")
                append("combined. Different zoom levels from\n")
                append("different files work together seamlessly.")
            }
            
            AlertDialog.Builder(this@SettingsActivity)
                .setTitle("Map File Info")
                .setMessage(message)
                .setPositiveButton("OK", null)
                .setNegativeButton("Delete") { _, _ ->
                    confirmDeleteMapFile(mapFile)
                }
                .show()
        }
    }
    
    /**
     * Confirm before deleting map file
     */
    private fun confirmDeleteMapFile(mapFile: MapFileManager.MapFile) {
        AlertDialog.Builder(this)
            .setTitle("Delete Map File")
            .setMessage("Are you sure you want to delete '${mapFile.displayName}'?\n\nThis action cannot be undone.")
            .setPositiveButton("Delete") { _, _ ->
                deleteMapFile(mapFile)
            }
            .setNegativeButton("Cancel", null)
            .show()
    }
    
    /**
     * Delete map file
     */
    private fun deleteMapFile(mapFile: MapFileManager.MapFile) {
        val success = MapFileManager.deleteMapFile(mapFile)
        if (success) {
            Toast.makeText(this, "Map file deleted", Toast.LENGTH_SHORT).show()
            loadMapFiles()
        } else {
            Toast.makeText(this, "Error deleting map file", Toast.LENGTH_SHORT).show()
        }
    }
    
    /**
     * Show instructions dialog
     */
    private fun showInstructions() {
        val instructions = """
            📍 OFFLINE MAPS SETUP
            
            All imported map files are automatically combined. You can add multiple files with different zoom levels for the same area.
            
            ─────────────────────
            HOW TO CREATE MAPS:
            ─────────────────────
            
            1. Download Mobile Atlas Creator (MOBAC)
            2. Select "Google Satellite" or other source
            3. Choose your area and zoom levels
            4. Select "MBTiles SQLite" as atlas format
            5. Create atlas and copy .mbtiles file to phone
            6. Import here using "Add Map File"
            
            ─────────────────────
            TIPS:
            ─────────────────────
            
            • Create separate files for different zoom ranges:
              - File 1: Zoom 8-12 (overview)
              - File 2: Zoom 13-16 (detail)
              - File 3: Zoom 17-19 (high detail)
            
            • All files are combined automatically
            
            • Tiles are searched in order - first match wins
            
            • No internet needed once maps are loaded
            
            ─────────────────────
            SUPPORTED FORMATS:
            ─────────────────────
            • MBTiles (.mbtiles) ✓ Recommended
            • SQLite (.sqlite, .db)
            • GEMF (.gemf)
        """.trimIndent()
        
        AlertDialog.Builder(this)
            .setTitle("Offline Maps Help")
            .setMessage(instructions)
            .setPositiveButton("OK", null)
            .show()
    }
    
    private fun saveSettings() {
        val baseUrl = etRadarBaseUrl.text.toString().trim()
        val pollInterval = etPollInterval.text.toString().toIntOrNull() ?: AppSettings.DEFAULT_POLL_INTERVAL
        val staleTimeout = etStaleTimeout.text.toString().toIntOrNull() ?: AppSettings.DEFAULT_STALE_TIMEOUT
        val missionUpdateInterval = etMissionUpdateInterval.text.toString().toIntOrNull() ?: AppSettings.DEFAULT_MISSION_UPDATE_INTERVAL
        
        if (baseUrl.isEmpty()) {
            Toast.makeText(this, "Please enter a valid radar base URL", Toast.LENGTH_SHORT).show()
            return
        }
        
        if (pollInterval < 1) {
            Toast.makeText(this, "Poll interval must be at least 1 second", Toast.LENGTH_SHORT).show()
            return
        }
        
        if (staleTimeout < 1) {
            Toast.makeText(this, "Stale timeout must be at least 1 second", Toast.LENGTH_SHORT).show()
            return
        }
        
        if (missionUpdateInterval < 1) {
            Toast.makeText(this, "Mission update interval must be at least 1 second", Toast.LENGTH_SHORT).show()
            return
        }
        
        appSettings.radarBaseUrl = baseUrl
        appSettings.pollInterval = pollInterval
        appSettings.staleTimeout = staleTimeout
        appSettings.missionUpdateInterval = missionUpdateInterval
        
        // Save map display settings
        appSettings.showCompass = switchShowCompass.isChecked
        appSettings.showZoomButtons = switchShowZoomButtons.isChecked
        appSettings.showScaleBar = switchShowScaleBar.isChecked
        appSettings.enableMapRotation = switchEnableMapRotation.isChecked
        
        Toast.makeText(this, "Settings saved successfully", Toast.LENGTH_SHORT).show()
        finish()
    }
    
    private fun testConnection() {
        val baseUrl = etRadarBaseUrl.text.toString().trim()
        
        if (baseUrl.isEmpty()) {
            Toast.makeText(this, "Please enter a valid radar base URL", Toast.LENGTH_SHORT).show()
            return
        }
        
        tvTestResult.visibility = View.VISIBLE
        tvTestResult.text = "Testing connection..."
        btnTestConnection.isEnabled = false
        
        val client = RadarApiClient(baseUrl, 1)
        
        lifecycleScope.launch {
            val (success, message) = client.testConnection()
            
            tvTestResult.text = message
            tvTestResult.setBackgroundColor(
                if (success) 0xFF4CAF50.toInt() else 0xFFF44336.toInt()
            )
            btnTestConnection.isEnabled = true
        }
    }
    
    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            android.R.id.home -> {
                finish()
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }
}
