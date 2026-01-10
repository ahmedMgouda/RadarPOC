package com.ccs.radarpoc.util

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.util.Log
import org.osmdroid.tileprovider.IRegisterReceiver
import org.osmdroid.tileprovider.MapTileProviderArray
import org.osmdroid.tileprovider.modules.ArchiveFileFactory
import org.osmdroid.tileprovider.modules.IArchiveFile
import org.osmdroid.tileprovider.modules.MapTileFileArchiveProvider
import org.osmdroid.tileprovider.modules.MapTileModuleProviderBase
import org.osmdroid.tileprovider.tilesource.ITileSource
import org.osmdroid.tileprovider.tilesource.XYTileSource
import org.osmdroid.tileprovider.util.SimpleRegisterReceiver
import java.io.File

/**
 * Factory for creating optimized tile providers that support:
 * - Multiple MBTiles files (different zoom levels, different areas)
 * - Both TMS and XYZ tile schemes
 * - Overzoom support (stretching tiles beyond max zoom)
 * - Smooth tile loading
 */
object MapTileProviderFactory {
    private const val TAG = "MapTileProviderFactory"
    
    /**
     * MBTiles metadata
     */
    data class MBTilesInfo(
        val file: File,
        val name: String,
        val format: String,
        val minZoom: Int,
        val maxZoom: Int,
        val scheme: String, // "tms" or "xyz"
        val bounds: String?
    )
    
    /**
     * Result of creating a tile provider
     */
    data class TileProviderResult(
        val provider: MapTileProviderArray?,
        val tileSource: ITileSource,
        val loadedFiles: List<MBTilesInfo>,
        val errors: List<String>
    )
    
    /**
     * Read metadata from MBTiles file
     */
    fun readMBTilesInfo(file: File): MBTilesInfo? {
        if (!file.exists() || !file.canRead()) {
            Log.w(TAG, "Cannot read file: ${file.absolutePath}")
            return null
        }
        
        var db: SQLiteDatabase? = null
        try {
            db = SQLiteDatabase.openDatabase(
                file.absolutePath,
                null,
                SQLiteDatabase.OPEN_READONLY
            )
            
            val metadata = mutableMapOf<String, String>()
            
            // Read metadata table
            db.rawQuery("SELECT name, value FROM metadata", null)?.use { cursor ->
                while (cursor.moveToNext()) {
                    val name = cursor.getString(0)
                    val value = cursor.getString(1)
                    metadata[name] = value
                }
            }
            
            // If no metadata table, try to detect from tiles
            var minZoom = metadata["minzoom"]?.toIntOrNull() ?: 0
            var maxZoom = metadata["maxzoom"]?.toIntOrNull() ?: 20
            
            // Detect actual zoom range from tiles table if not in metadata
            if (!metadata.containsKey("minzoom") || !metadata.containsKey("maxzoom")) {
                db.rawQuery("SELECT MIN(zoom_level), MAX(zoom_level) FROM tiles", null)?.use { cursor ->
                    if (cursor.moveToFirst()) {
                        minZoom = cursor.getInt(0)
                        maxZoom = cursor.getInt(1)
                    }
                }
            }
            
            val info = MBTilesInfo(
                file = file,
                name = metadata["name"] ?: file.nameWithoutExtension,
                format = metadata["format"] ?: "jpg",
                minZoom = minZoom,
                maxZoom = maxZoom,
                scheme = metadata["scheme"] ?: "tms", // MBTiles default is TMS
                bounds = metadata["bounds"]
            )
            
            Log.d(TAG, "MBTiles info: ${info.name}, zoom ${info.minZoom}-${info.maxZoom}, scheme: ${info.scheme}, format: ${info.format}")
            return info
            
        } catch (e: Exception) {
            Log.e(TAG, "Error reading MBTiles metadata: ${file.name}", e)
            return null
        } finally {
            db?.close()
        }
    }
    
    /**
     * Create a tile provider that loads ALL available MBTiles files
     * Tiles are searched in order - first file with matching tile wins
     */
    fun createMultiArchiveProvider(
        context: Context,
        mapFiles: List<MapFileManager.MapFile>
    ): TileProviderResult {
        val errors = mutableListOf<String>()
        val loadedFiles = mutableListOf<MBTilesInfo>()
        val archives = mutableListOf<IArchiveFile>()
        
        var globalMinZoom = 20
        var globalMaxZoom = 0
        
        // Load all MBTiles files
        for (mapFile in mapFiles) {
            val file = File(mapFile.path)
            
            if (!file.exists()) {
                errors.add("File not found: ${mapFile.name}")
                continue
            }
            
            // Read metadata
            val info = readMBTilesInfo(file)
            if (info == null) {
                errors.add("Cannot read metadata: ${mapFile.name}")
                continue
            }
            
            // Open archive
            try {
                val archive = ArchiveFileFactory.getArchiveFile(file)
                if (archive != null) {
                    archives.add(archive)
                    loadedFiles.add(info)
                    
                    // Track global zoom range
                    globalMinZoom = minOf(globalMinZoom, info.minZoom)
                    globalMaxZoom = maxOf(globalMaxZoom, info.maxZoom)
                    
                    Log.d(TAG, "Loaded archive: ${info.name} (zoom ${info.minZoom}-${info.maxZoom})")
                } else {
                    errors.add("Cannot open archive: ${mapFile.name}")
                }
            } catch (e: Exception) {
                errors.add("Error loading ${mapFile.name}: ${e.message}")
                Log.e(TAG, "Error loading archive", e)
            }
        }
        
        // If no archives loaded, return null provider
        if (archives.isEmpty()) {
            Log.w(TAG, "No archives loaded")
            return TileProviderResult(
                provider = null,
                tileSource = createDefaultTileSource(),
                loadedFiles = emptyList(),
                errors = errors
            )
        }
        
        // Create custom tile source with overzoom support
        val tileSource = XYTileSource(
            "OfflineTiles",
            globalMinZoom,           // Min zoom from files
            22,                       // Allow overzoom up to 22
            256,                      // Tile size
            ".jpg",                   // Extension
            arrayOf("")               // No URL (offline only)
        )
        
        // Create archive provider with ALL archives
        val registerReceiver: IRegisterReceiver = SimpleRegisterReceiver(context)
        
        val archiveProvider = MapTileFileArchiveProvider(
            registerReceiver,
            tileSource,
            archives.toTypedArray()
        )
        
        // Create the provider array
        val providerArray = MapTileProviderArray(
            tileSource,
            registerReceiver,
            arrayOf<MapTileModuleProviderBase>(archiveProvider)
        )
        
        Log.d(TAG, "Created multi-archive provider with ${archives.size} files, zoom range: $globalMinZoom-$globalMaxZoom")
        
        return TileProviderResult(
            provider = providerArray,
            tileSource = tileSource,
            loadedFiles = loadedFiles,
            errors = errors
        )
    }
    
    /**
     * Create default online tile source
     */
    private fun createDefaultTileSource(): ITileSource {
        return XYTileSource(
            "Mapnik",
            0,
            19,
            256,
            ".png",
            arrayOf(
                "https://a.tile.openstreetmap.org/",
                "https://b.tile.openstreetmap.org/",
                "https://c.tile.openstreetmap.org/"
            )
        )
    }
    
    /**
     * Get summary of loaded map files
     */
    fun getLoadedMapsSummary(loadedFiles: List<MBTilesInfo>): String {
        if (loadedFiles.isEmpty()) {
            return "No offline maps loaded"
        }
        
        val totalSize = loadedFiles.sumOf { it.file.length() }
        val totalSizeMB = totalSize / (1024.0 * 1024.0)
        
        val zoomRanges = loadedFiles.map { "${it.minZoom}-${it.maxZoom}" }.distinct()
        
        return buildString {
            append("${loadedFiles.size} map file(s)")
            append(" • ${String.format("%.1f", totalSizeMB)} MB")
            append(" • Zoom: ${zoomRanges.joinToString(", ")}")
        }
    }
}
