package com.ccs.radarpoc.util

import android.content.Context
import android.util.Log
import java.io.File

/**
 * Manager for handling offline map files (MBTiles, SQLite archives)
 * Uses app-specific storage - no permissions needed!
 */
object MapFileManager {
    private const val TAG = "MapFileManager"
    
    /**
     * Represents a map file on the device
     */
    data class MapFile(
        val name: String,
        val path: String,
        val sizeBytes: Long,
        val lastModified: Long,
        val isActive: Boolean = false
    ) {
        val sizeMB: Double get() = sizeBytes / (1024.0 * 1024.0)
        val formattedSize: String get() = String.format("%.1f MB", sizeMB)
        val sizeFormatted: String get() = formattedSize
        val displayName: String get() = name.removeSuffix(".sqlite").removeSuffix(".mbtiles").removeSuffix(".db")
    }
    
    /**
     * Get OSMDroid base path - uses app-specific storage (no permissions needed)
     */
    fun getOsmDroidBasePath(context: Context): File {
        val appSpecificDir = context.getExternalFilesDir(null)
        return File(appSpecificDir, "osmdroid")
    }
    
    /**
     * Get the directory where offline map archives are stored
     */
    fun getOfflineMapsDirectory(context: Context): File {
        val baseDir = getOsmDroidBasePath(context)
        if (!baseDir.exists()) {
            baseDir.mkdirs()
            Log.d(TAG, "Created maps directory: ${baseDir.absolutePath}")
        }
        return baseDir
    }
    
    /**
     * Scan for available offline map files
     */
    fun scanForMapFiles(context: Context, activeMapPath: String? = null): List<MapFile> {
        val mapFiles = mutableListOf<MapFile>()
        val mapsDir = getOfflineMapsDirectory(context)
        
        if (!mapsDir.exists() || !mapsDir.isDirectory) {
            Log.w(TAG, "Maps directory doesn't exist: ${mapsDir.absolutePath}")
            return emptyList()
        }
        
        try {
            mapsDir.listFiles()?.forEach { file ->
                if (file.isFile && isValidMapFile(file)) {
                    mapFiles.add(
                        MapFile(
                            name = file.name,
                            path = file.absolutePath,
                            sizeBytes = file.length(),
                            lastModified = file.lastModified(),
                            isActive = file.absolutePath == activeMapPath
                        )
                    )
                }
            }
            
            Log.d(TAG, "Found ${mapFiles.size} map files in ${mapsDir.absolutePath}")
        } catch (e: Exception) {
            Log.e(TAG, "Error scanning for map files", e)
        }
        
        return mapFiles.sortedByDescending { it.lastModified }
    }
    
    /**
     * Get all available map files from default directory
     */
    fun getAvailableMapFiles(context: Context, activeMapPath: String?): List<MapFile> {
        return scanForMapFiles(context, activeMapPath)
    }
    
    /**
     * Check if file is a valid map file based on extension
     */
    fun isValidMapFile(file: File): Boolean {
        val extension = file.extension.lowercase()
        val validExtensions = listOf("mbtiles", "sqlite", "sqlite3", "db", "zip", "gemf")
        
        val isValid = extension in validExtensions
        
        if (!isValid) {
            Log.w(TAG, "Invalid extension: $extension (file: ${file.name})")
        } else {
            Log.d(TAG, "Valid map file: ${file.name} (${file.length()} bytes, extension: $extension)")
        }
        
        return isValid
    }
    
    /**
     * Import a map file from external location to app directory
     */
    fun importMapFile(context: Context, sourceFile: File): Result<MapFile> {
        if (!isValidMapFile(sourceFile)) {
            Log.e(TAG, "Invalid map file: ${sourceFile.name}")
            return Result.failure(Exception("Invalid file format"))
        }
        
        return try {
            val destDir = getOfflineMapsDirectory(context)
            val destFile = File(destDir, sourceFile.name)
            
            // Check if file already exists
            if (destFile.exists()) {
                return Result.failure(Exception("A map file with this name already exists"))
            }
            
            Log.d(TAG, "Copying from: ${sourceFile.absolutePath}")
            Log.d(TAG, "Copying to: ${destFile.absolutePath}")
            
            // Copy file
            sourceFile.copyTo(destFile, overwrite = false)
            
            Log.d(TAG, "Import successful: ${destFile.name} (${destFile.length()} bytes)")
            
            val mapFile = MapFile(
                name = destFile.name,
                path = destFile.absolutePath,
                sizeBytes = destFile.length(),
                lastModified = destFile.lastModified(),
                isActive = false
            )
            
            Result.success(mapFile)
        } catch (e: Exception) {
            Log.e(TAG, "Error importing map file: ${sourceFile.name}", e)
            Result.failure(e)
        }
    }
    
    /**
     * Validate setup
     */
    fun validateSetup(context: Context): ValidationResult {
        val issues = mutableListOf<String>()
        
        val baseDir = getOsmDroidBasePath(context)
        if (!baseDir.exists()) {
            try {
                baseDir.mkdirs()
                Log.d(TAG, "Created OSMDroid base directory: ${baseDir.absolutePath}")
            } catch (e: Exception) {
                issues.add("Cannot create maps directory: ${e.message}")
            }
        }
        
        if (!baseDir.canWrite()) {
            issues.add("Maps directory is not writable")
        }
        
        val mapFiles = scanForMapFiles(context)
        if (mapFiles.isEmpty()) {
            issues.add("No offline map files found in ${baseDir.absolutePath}")
        }
        
        return ValidationResult(
            isValid = issues.isEmpty(),
            issues = issues,
            mapsDirectory = baseDir.absolutePath,
            mapFilesFound = mapFiles.size
        )
    }
    
    /**
     * Validation result
     */
    data class ValidationResult(
        val isValid: Boolean,
        val issues: List<String>,
        val mapsDirectory: String,
        val mapFilesFound: Int
    )
    
    /**
     * Delete a map file
     */
    fun deleteMapFile(mapFile: MapFile): Boolean {
        return try {
            val file = File(mapFile.path)
            val deleted = file.delete()
            if (deleted) {
                Log.d(TAG, "Deleted map file: ${mapFile.name}")
            } else {
                Log.w(TAG, "Failed to delete map file: ${mapFile.name}")
            }
            deleted
        } catch (e: Exception) {
            Log.e(TAG, "Error deleting map file: ${mapFile.name}", e)
            false
        }
    }
    
    /**
     * Get instructions for adding offline maps
     */
    fun getInstructions(context: Context): String {
        val mapsDir = getOfflineMapsDirectory(context)
        return """
            To use offline maps:
            
            1. Download or create a map file (.mbtiles format recommended)
            2. Tap 'Add Map File' to import the file
            3. App will copy it to: ${mapsDir.absolutePath}
            4. Select the map to use it
            
            Supported formats:
            • MBTiles (.mbtiles) - Recommended
            • SQLite archives (.sqlite, .db)
            • ZIP archives (.zip)
            • GEMF (.gemf)
            
            No storage permissions needed - files are stored in app-specific storage!
        """.trimIndent()
    }
    
    /**
     * Alias that works without context
     */
    fun getInstructionsText(): String {
        return """
            To use offline maps:
            
            1. Download or create a map file (.mbtiles format recommended)
            2. Tap 'Add Map File' to import the file
            3. App will copy it to app-specific storage
            4. Select the map to use it
            
            Supported formats:
            • MBTiles (.mbtiles) - Recommended
            • SQLite archives (.sqlite, .db)
            • ZIP archives (.zip)
            • GEMF (.gemf)
        """.trimIndent()
    }
}
