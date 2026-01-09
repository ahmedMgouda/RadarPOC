package com.ccs.radarpoc

import android.os.Bundle
import android.view.MenuItem
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.ccs.radarpoc.data.AppSettings
import com.ccs.radarpoc.network.RadarApiClient
import kotlinx.coroutines.launch

class SettingsActivity : AppCompatActivity() {
    
    private lateinit var etRadarBaseUrl: EditText
    private lateinit var etPollInterval: EditText
    private lateinit var etStaleTimeout: EditText
    private lateinit var etMissionUpdateInterval: EditText
    private lateinit var btnTestConnection: Button
    private lateinit var btnSave: Button
    private lateinit var tvTestResult: TextView
    
    private lateinit var appSettings: AppSettings
    
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
