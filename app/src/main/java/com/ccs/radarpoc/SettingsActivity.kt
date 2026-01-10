package com.ccs.radarpoc

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.OpenableColumns
import android.view.KeyEvent
import android.view.LayoutInflater
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.viewpager2.widget.ViewPager2
import com.ccs.radarpoc.data.AppSettings
import com.ccs.radarpoc.data.repository.RadarFOVRepository
import com.ccs.radarpoc.network.RadarApiClient
import com.ccs.radarpoc.ui.MapFileAdapter
import com.ccs.radarpoc.util.MapFileManager
import com.ccs.radarpoc.util.MapTileProviderFactory
import com.google.android.material.card.MaterialCardView
import com.google.android.material.tabs.TabLayout
import com.google.android.material.tabs.TabLayoutMediator
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

class SettingsActivity : AppCompatActivity() {
    
    companion object {
        private const val KEY_IS_AUTHENTICATED = "is_authenticated"
    }
    
    // Main views
    private lateinit var tabLayout: TabLayout
    private lateinit var viewPager: ViewPager2
    private lateinit var btnSave: Button
    
    // Radar settings views
    private lateinit var etRadarBaseUrl: TextInputEditText
    private lateinit var etPollInterval: TextInputEditText
    private lateinit var etStaleTimeout: TextInputEditText
    private lateinit var btnTestConnection: Button
    private lateinit var tvTestResult: TextView
    
    // FOV settings views
    private lateinit var etFovPollInterval: TextInputEditText
    private lateinit var switchShowFov: com.google.android.material.switchmaterial.SwitchMaterial
    private lateinit var switchShowBoresight: com.google.android.material.switchmaterial.SwitchMaterial
    private lateinit var switchShowRadarMarkers: com.google.android.material.switchmaterial.SwitchMaterial
    private lateinit var btnTestFovConnection: Button
    private lateinit var tvFovTestResult: TextView
    
    // Drone settings views
    private lateinit var etMissionUpdateInterval: TextInputEditText
    
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
    
    // Security Settings Views
    private lateinit var cardDefaultCredentialsWarning: MaterialCardView
    private lateinit var tvCurrentUsername: TextView
    private lateinit var etNewUsername: TextInputEditText
    private lateinit var etNewPassword: TextInputEditText
    private lateinit var etConfirmPassword: TextInputEditText
    private lateinit var tilNewUsername: TextInputLayout
    private lateinit var tilNewPassword: TextInputLayout
    private lateinit var tilConfirmPassword: TextInputLayout
    private lateinit var btnUpdateCredentials: Button
    
    private lateinit var appSettings: AppSettings
    private lateinit var mapFileAdapter: MapFileAdapter
    
    // Authentication state
    private var isAuthenticated = false
    
    // Login dialog reference
    private var loginDialog: AlertDialog? = null
    
    // Tab layouts
    private val tabLayouts = mutableListOf<View>()
    
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
        
        appSettings = AppSettings(this)
        
        // Restore authentication state
        isAuthenticated = savedInstanceState?.getBoolean(KEY_IS_AUTHENTICATED, false) ?: false
        
        if (isAuthenticated) {
            initializeSettingsScreen()
        } else {
            showLoginDialog()
        }
    }
    
    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putBoolean(KEY_IS_AUTHENTICATED, isAuthenticated)
    }
    
    override fun onDestroy() {
        super.onDestroy()
        loginDialog?.dismiss()
        loginDialog = null
    }
    
    private fun showLoginDialog() {
        val dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_login, null)
        
        val tilUsername = dialogView.findViewById<TextInputLayout>(R.id.tilUsername)
        val tilPassword = dialogView.findViewById<TextInputLayout>(R.id.tilPassword)
        val etUsername = dialogView.findViewById<TextInputEditText>(R.id.etUsername)
        val etPassword = dialogView.findViewById<TextInputEditText>(R.id.etPassword)
        val tvError = dialogView.findViewById<TextView>(R.id.tvError)
        
        val performLogin = {
            val username = etUsername.text?.toString()?.trim() ?: ""
            val password = etPassword.text?.toString() ?: ""
            
            tilUsername.error = null
            tilPassword.error = null
            tvError.visibility = View.GONE
            
            var hasError = false
            if (username.isEmpty()) {
                tilUsername.error = "Username required"
                hasError = true
            }
            if (password.isEmpty()) {
                tilPassword.error = "Password required"
                hasError = true
            }
            
            if (!hasError) {
                if (appSettings.validateCredentials(username, password)) {
                    isAuthenticated = true
                    loginDialog?.dismiss()
                    loginDialog = null
                    initializeSettingsScreen()
                } else {
                    tvError.text = "Invalid username or password"
                    tvError.visibility = View.VISIBLE
                    etPassword.text?.clear()
                }
            }
        }
        
        etUsername.setOnEditorActionListener { _, actionId, event ->
            if (actionId == EditorInfo.IME_ACTION_NEXT || 
                (event?.keyCode == KeyEvent.KEYCODE_ENTER && event.action == KeyEvent.ACTION_DOWN)) {
                etPassword.requestFocus()
                true
            } else {
                false
            }
        }
        
        etPassword.setOnEditorActionListener { _, actionId, event ->
            if (actionId == EditorInfo.IME_ACTION_DONE || 
                (event?.keyCode == KeyEvent.KEYCODE_ENTER && event.action == KeyEvent.ACTION_DOWN)) {
                performLogin()
                true
            } else {
                false
            }
        }
        
        loginDialog = AlertDialog.Builder(this)
            .setView(dialogView)
            .setCancelable(false)
            .setPositiveButton("Login", null)
            .setNegativeButton("Cancel") { _, _ ->
                finish()
            }
            .create()
        
        loginDialog?.setOnShowListener {
            val loginButton = loginDialog?.getButton(AlertDialog.BUTTON_POSITIVE)
            loginButton?.setOnClickListener {
                performLogin()
            }
            etUsername.requestFocus()
        }
        
        loginDialog?.show()
    }
    
    private fun initializeSettingsScreen() {
        setContentView(R.layout.activity_settings)
        
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = "Settings"
        
        initMainViews()
        setupViewPager()
        loadSettings()
    }
    
    private fun initMainViews() {
        tabLayout = findViewById(R.id.tabLayout)
        viewPager = findViewById(R.id.viewPager)
        btnSave = findViewById(R.id.btnSave)
        
        btnSave.setOnClickListener {
            saveSettings()
        }
    }
    
    private fun setupViewPager() {
        // Create tab layouts with proper layout parameters for ViewPager2
        val radarTab = layoutInflater.inflate(R.layout.tab_radar_settings, viewPager, false).apply {
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
        }
        val mapTab = layoutInflater.inflate(R.layout.tab_map_settings, viewPager, false).apply {
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
        }
        val droneTab = layoutInflater.inflate(R.layout.tab_drone_settings, viewPager, false).apply {
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
        }
        val securityTab = layoutInflater.inflate(R.layout.tab_security_settings, viewPager, false).apply {
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
        }
        
        tabLayouts.clear()
        tabLayouts.add(radarTab)
        tabLayouts.add(mapTab)
        tabLayouts.add(droneTab)
        tabLayouts.add(securityTab)
        
        // Initialize views in each tab
        initRadarTabViews(radarTab)
        initMapTabViews(mapTab)
        initDroneTabViews(droneTab)
        initSecurityTabViews(securityTab)
        
        // Setup ViewPager adapter
        viewPager.adapter = SettingsPagerAdapter(tabLayouts)
        
        // Setup TabLayout with ViewPager
        TabLayoutMediator(tabLayout, viewPager) { tab, position ->
            tab.text = when (position) {
                0 -> "📡 Radar"
                1 -> "🗺️ Map"
                2 -> "🚁 Drone"
                3 -> "🔐 Security"
                else -> ""
            }
        }.attach()
    }
    
    private fun initRadarTabViews(view: View) {
        etRadarBaseUrl = view.findViewById(R.id.etRadarBaseUrl)
        etPollInterval = view.findViewById(R.id.etPollInterval)
        etStaleTimeout = view.findViewById(R.id.etStaleTimeout)
        btnTestConnection = view.findViewById(R.id.btnTestConnection)
        tvTestResult = view.findViewById(R.id.tvTestResult)
        
        // FOV settings
        etFovPollInterval = view.findViewById(R.id.etFovPollInterval)
        switchShowFov = view.findViewById(R.id.switchShowFov)
        switchShowBoresight = view.findViewById(R.id.switchShowBoresight)
        switchShowRadarMarkers = view.findViewById(R.id.switchShowRadarMarkers)
        btnTestFovConnection = view.findViewById(R.id.btnTestFovConnection)
        tvFovTestResult = view.findViewById(R.id.tvFovTestResult)
        
        btnTestConnection.setOnClickListener {
            testConnection()
        }
        
        btnTestFovConnection.setOnClickListener {
            testFovConnection()
        }
    }
    
    private fun initMapTabViews(view: View) {
        rvMapFiles = view.findViewById(R.id.rvMapFiles)
        tvEmptyMaps = view.findViewById(R.id.tvEmptyMaps)
        btnAddMapFile = view.findViewById(R.id.btnAddMapFile)
        btnMapInstructions = view.findViewById(R.id.btnMapInstructions)
        tvCurrentMapSource = view.findViewById(R.id.tvCurrentMapSource)
        
        switchShowCompass = view.findViewById(R.id.switchShowCompass)
        switchShowZoomButtons = view.findViewById(R.id.switchShowZoomButtons)
        switchShowScaleBar = view.findViewById(R.id.switchShowScaleBar)
        switchEnableMapRotation = view.findViewById(R.id.switchEnableMapRotation)
        
        btnAddMapFile.setOnClickListener {
            openFilePicker()
        }
        
        btnMapInstructions.setOnClickListener {
            showInstructions()
        }
        
        setupMapFilesRecyclerView()
    }
    
    private fun initDroneTabViews(view: View) {
        etMissionUpdateInterval = view.findViewById(R.id.etMissionUpdateInterval)
    }
    
    private fun initSecurityTabViews(view: View) {
        cardDefaultCredentialsWarning = view.findViewById(R.id.cardDefaultCredentialsWarning)
        tvCurrentUsername = view.findViewById(R.id.tvCurrentUsername)
        etNewUsername = view.findViewById(R.id.etNewUsername)
        etNewPassword = view.findViewById(R.id.etNewPassword)
        etConfirmPassword = view.findViewById(R.id.etConfirmPassword)
        tilNewUsername = view.findViewById(R.id.tilNewUsername)
        tilNewPassword = view.findViewById(R.id.tilNewPassword)
        tilConfirmPassword = view.findViewById(R.id.tilConfirmPassword)
        btnUpdateCredentials = view.findViewById(R.id.btnUpdateCredentials)
        
        btnUpdateCredentials.setOnClickListener {
            updateCredentials()
        }
    }
    
    private fun loadSettings() {
        // Radar settings
        etRadarBaseUrl.setText(appSettings.radarBaseUrl)
        etPollInterval.setText(appSettings.pollInterval.toString())
        etStaleTimeout.setText(appSettings.staleTimeout.toString())
        
        // FOV settings
        etFovPollInterval.setText(appSettings.fovPollInterval.toString())
        switchShowFov.isChecked = appSettings.showFOV
        switchShowBoresight.isChecked = appSettings.showBoresight
        switchShowRadarMarkers.isChecked = appSettings.showRadarMarkers
        
        // Drone settings
        etMissionUpdateInterval.setText(appSettings.missionUpdateInterval.toString())
        
        // Map Display settings
        switchShowCompass.isChecked = appSettings.showCompass
        switchShowZoomButtons.isChecked = appSettings.showZoomButtons
        switchShowScaleBar.isChecked = appSettings.showScaleBar
        switchEnableMapRotation.isChecked = appSettings.enableMapRotation
        
        // Security settings
        updateSecurityUI()
        
        // Load map files
        loadMapFiles()
    }
    
    private fun updateSecurityUI() {
        tvCurrentUsername.text = appSettings.authUsername
        
        if (appSettings.isUsingDefaultCredentials()) {
            cardDefaultCredentialsWarning.visibility = View.VISIBLE
        } else {
            cardDefaultCredentialsWarning.visibility = View.GONE
        }
        
        etNewUsername.text?.clear()
        etNewPassword.text?.clear()
        etConfirmPassword.text?.clear()
        
        tilNewUsername.error = null
        tilNewPassword.error = null
        tilConfirmPassword.error = null
    }
    
    private fun updateCredentials() {
        val newUsername = etNewUsername.text?.toString()?.trim() ?: ""
        val newPassword = etNewPassword.text?.toString() ?: ""
        val confirmPassword = etConfirmPassword.text?.toString() ?: ""
        
        tilNewUsername.error = null
        tilNewPassword.error = null
        tilConfirmPassword.error = null
        
        var hasError = false
        
        if (newUsername.isEmpty()) {
            tilNewUsername.error = "Username required"
            hasError = true
        } else if (newUsername.length < 3) {
            tilNewUsername.error = "Username must be at least 3 characters"
            hasError = true
        }
        
        if (newPassword.isEmpty()) {
            tilNewPassword.error = "Password required"
            hasError = true
        } else if (newPassword.length < 4) {
            tilNewPassword.error = "Password must be at least 4 characters"
            hasError = true
        }
        
        if (confirmPassword.isEmpty()) {
            tilConfirmPassword.error = "Please confirm password"
            hasError = true
        } else if (newPassword != confirmPassword) {
            tilConfirmPassword.error = "Passwords do not match"
            hasError = true
        }
        
        if (hasError) return
        
        AlertDialog.Builder(this)
            .setTitle("Update Credentials")
            .setMessage("Are you sure you want to update your login credentials?\n\nNew username: $newUsername")
            .setPositiveButton("Update") { _, _ ->
                if (appSettings.updateCredentials(newUsername, newPassword)) {
                    Toast.makeText(this, "✓ Credentials updated successfully", Toast.LENGTH_LONG).show()
                    updateSecurityUI()
                } else {
                    Toast.makeText(this, "Failed to update credentials", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }
    
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
    }
    
    private fun loadMapFiles() {
        lifecycleScope.launch {
            val mapFiles = MapFileManager.scanForMapFiles(this@SettingsActivity)
            
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
    
    private fun openFilePicker() {
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "*/*"
            putExtra(Intent.EXTRA_MIME_TYPES, arrayOf("application/x-sqlite3", "application/octet-stream", "*/*"))
        }
        filePickerLauncher.launch(intent)
    }
    
    private fun handleSelectedFile(uri: Uri) {
        lifecycleScope.launch {
            try {
                Toast.makeText(this@SettingsActivity, "Importing map file...", Toast.LENGTH_SHORT).show()
                
                val result = withContext(Dispatchers.IO) {
                    val fileName = getFileName(uri) ?: "map.mbtiles"
                    val tempFile = File(cacheDir, fileName)
                    
                    contentResolver.openInputStream(uri)?.use { input ->
                        FileOutputStream(tempFile).use { output ->
                            input.copyTo(output)
                        }
                    }
                    
                    if (!MapFileManager.isValidMapFile(tempFile)) {
                        tempFile.delete()
                        return@withContext kotlin.Result.failure<MapFileManager.MapFile>(
                            Exception("Invalid map file format")
                        )
                    }
                    
                    val importResult = MapFileManager.importMapFile(this@SettingsActivity, tempFile)
                    tempFile.delete()
                    
                    importResult
                }
                
                result.onSuccess { mapFile ->
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
                Toast.makeText(
                    this@SettingsActivity, 
                    "Error: ${e.message}", 
                    Toast.LENGTH_LONG
                ).show()
            }
        }
    }
    
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
    
    private fun deleteMapFile(mapFile: MapFileManager.MapFile) {
        val success = MapFileManager.deleteMapFile(mapFile)
        if (success) {
            Toast.makeText(this, "Map file deleted", Toast.LENGTH_SHORT).show()
            loadMapFiles()
        } else {
            Toast.makeText(this, "Error deleting map file", Toast.LENGTH_SHORT).show()
        }
    }
    
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
            
            • Create separate files for different zoom ranges
            • All files are combined automatically
            • No internet needed once maps are loaded
            
            ─────────────────────
            SUPPORTED FORMATS:
            ─────────────────────
            • MBTiles (.mbtiles) ✓ Recommended
            • SQLite (.sqlite, .db)
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
        val fovPollInterval = etFovPollInterval.text.toString().toIntOrNull() ?: AppSettings.DEFAULT_FOV_POLL_INTERVAL
        val missionUpdateInterval = etMissionUpdateInterval.text.toString().toIntOrNull() ?: AppSettings.DEFAULT_MISSION_UPDATE_INTERVAL
        
        if (baseUrl.isEmpty()) {
            Toast.makeText(this, "Please enter a valid radar base URL", Toast.LENGTH_SHORT).show()
            viewPager.currentItem = 0 // Switch to Radar tab
            return
        }
        
        if (pollInterval < 1) {
            Toast.makeText(this, "Poll interval must be at least 1 second", Toast.LENGTH_SHORT).show()
            viewPager.currentItem = 0
            return
        }
        
        if (staleTimeout < 1) {
            Toast.makeText(this, "Stale timeout must be at least 1 second", Toast.LENGTH_SHORT).show()
            viewPager.currentItem = 0
            return
        }
        
        if (fovPollInterval < 5) {
            Toast.makeText(this, "FOV poll interval must be at least 5 seconds", Toast.LENGTH_SHORT).show()
            viewPager.currentItem = 0
            return
        }
        
        if (missionUpdateInterval < 1) {
            Toast.makeText(this, "Mission update interval must be at least 1 second", Toast.LENGTH_SHORT).show()
            viewPager.currentItem = 2 // Switch to Drone tab
            return
        }
        
        // Save radar settings
        appSettings.radarBaseUrl = baseUrl
        appSettings.pollInterval = pollInterval
        appSettings.staleTimeout = staleTimeout
        
        // Save FOV settings
        appSettings.fovPollInterval = fovPollInterval
        appSettings.showFOV = switchShowFov.isChecked
        appSettings.showBoresight = switchShowBoresight.isChecked
        appSettings.showRadarMarkers = switchShowRadarMarkers.isChecked
        
        // Save drone settings
        appSettings.missionUpdateInterval = missionUpdateInterval
        
        // Save map display settings
        appSettings.showCompass = switchShowCompass.isChecked
        appSettings.showZoomButtons = switchShowZoomButtons.isChecked
        appSettings.showScaleBar = switchShowScaleBar.isChecked
        appSettings.enableMapRotation = switchEnableMapRotation.isChecked
        
        Toast.makeText(this, "✓ Settings saved successfully", Toast.LENGTH_SHORT).show()
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
    
    private fun testFovConnection() {
        val baseUrl = etRadarBaseUrl.text.toString().trim()
        
        if (baseUrl.isEmpty()) {
            Toast.makeText(this, "Please enter a valid radar base URL first", Toast.LENGTH_SHORT).show()
            return
        }
        
        tvFovTestResult.visibility = View.VISIBLE
        tvFovTestResult.text = "Testing FOV endpoint..."
        btnTestFovConnection.isEnabled = false
        
        val fovRepository = RadarFOVRepository(baseUrl)
        
        lifecycleScope.launch {
            val (connectionSuccess, message) = fovRepository.testConnection()
            
            if (connectionSuccess) {
                // Also try to parse the data
                val fetchResult = fovRepository.fetchFOVData(forceRefresh = true)
                
                if (fetchResult.isSuccess) {
                    val data = fetchResult.getOrNull()
                    if (data != null) {
                        tvFovTestResult.text = buildString {
                            append("✓ Connected\n")
                            append("Radars found: ${data.radarCount}\n")
                            data.radars.forEach { radar ->
                                append("• ${radar.name}\n")
                            }
                        }
                        tvFovTestResult.setBackgroundColor(0xFF4CAF50.toInt())
                    } else {
                        tvFovTestResult.text = "✓ Connected but no data"
                        tvFovTestResult.setBackgroundColor(0xFFFF9800.toInt())
                    }
                } else {
                    val error = fetchResult.exceptionOrNull()
                    tvFovTestResult.text = "✓ Connected but parsing failed:\n${error?.message}"
                    tvFovTestResult.setBackgroundColor(0xFFFF9800.toInt())
                }
            } else {
                tvFovTestResult.text = message
                tvFovTestResult.setBackgroundColor(0xFFF44336.toInt())
            }
            
            btnTestFovConnection.isEnabled = true
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
    
    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        if (isAuthenticated) {
            super.onBackPressed()
        } else {
            finish()
        }
    }
    
    // ViewPager Adapter
    private class SettingsPagerAdapter(
        private val layouts: List<View>
    ) : androidx.recyclerview.widget.RecyclerView.Adapter<SettingsPagerAdapter.ViewHolder>() {
        
        class ViewHolder(val view: View) : androidx.recyclerview.widget.RecyclerView.ViewHolder(view)
        
        override fun onCreateViewHolder(parent: android.view.ViewGroup, viewType: Int): ViewHolder {
            return ViewHolder(layouts[viewType])
        }
        
        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            // Views are already inflated and bound
        }
        
        override fun getItemCount(): Int = layouts.size
        
        override fun getItemViewType(position: Int): Int = position
    }
}
