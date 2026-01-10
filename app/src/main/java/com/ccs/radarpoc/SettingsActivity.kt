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
        
        setupMapFilesRecyclerView()
    }
    
    private fun loadSettings() {
        etRadarBaseUrl.setText(appSettings.radarBaseUrl)
        etPollInterval.setText(appSettings.pollInterval.toString())
        etStaleTimeout.setText(appSettings.staleTimeout.toString())
        etMissionUpdateInterval.setText(appSettings.missionUpdateInterval.toString())
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
                selectMapFile(mapFile)
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
        val activeMapPath = appSettings.activeMapFilePath
        val mapFiles = MapFileManager.scanForMapFiles(this).map { mapFile ->
            mapFile.copy(isActive = mapFile.path == activeMapPath)
        }
        
        if (mapFiles.isEmpty()) {
            rvMapFiles.visibility = View.GONE
            tvEmptyMaps.visibility = View.VISIBLE
        } else {
            rvMapFiles.visibility = View.VISIBLE
            tvEmptyMaps.visibility = View.GONE
            mapFileAdapter.submitList(mapFiles)
        }
        
        updateCurrentMapSourceDisplay()
    }
    
    /**
     * Update current map source display
     */
    private fun updateCurrentMapSourceDisplay() {
        val activeMapPath = appSettings.activeMapFilePath
        tvCurrentMapSource.text = if (activeMapPath != null) {
            val file = File(activeMapPath)
            file.nameWithoutExtension
        } else {
            "Default (Online Tiles)"
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
                    
                    android.util.Log.d("SettingsActivity", "File validation passed, importing...")
                    
                    // Import file
                    val importResult = MapFileManager.importMapFile(this@SettingsActivity, tempFile)
                    
                    // Clean up temp file
                    tempFile.delete()
                    
                    importResult
                }
                
                result.onSuccess { mapFile ->
                    Toast.makeText(
                        this@SettingsActivity, 
                        "✓ Map imported: ${mapFile.displayName}\n${mapFile.sizeFormatted}", 
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
     * Select a map file as active
     */
    private fun selectMapFile(mapFile: MapFileManager.MapFile) {
        if (mapFile.isActive) {
            // Deselect (use default online tiles)
            appSettings.activeMapFilePath = null
            Toast.makeText(this, "Switched to default online tiles", Toast.LENGTH_SHORT).show()
        } else {
            // Select this map
            appSettings.activeMapFilePath = mapFile.path
            Toast.makeText(this, "Selected: ${mapFile.displayName}", Toast.LENGTH_SHORT).show()
        }
        loadMapFiles()
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
        // If this is the active map, deactivate it first
        if (mapFile.isActive) {
            appSettings.activeMapFilePath = null
        }
        
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
        AlertDialog.Builder(this)
            .setTitle("Offline Maps Instructions")
            .setMessage(MapFileManager.getInstructions(this))
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
