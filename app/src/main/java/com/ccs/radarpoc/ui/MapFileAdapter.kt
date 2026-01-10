package com.ccs.radarpoc.ui

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.ccs.radarpoc.R
import com.ccs.radarpoc.util.MapFileManager
import java.text.SimpleDateFormat
import java.util.*

/**
 * Adapter for displaying map files in a RecyclerView
 * All map files are automatically loaded - no selection needed
 */
class MapFileAdapter(
    private val onMapSelected: (MapFileManager.MapFile) -> Unit,
    private val onMapDeleted: (MapFileManager.MapFile) -> Unit
) : ListAdapter<MapFileManager.MapFile, MapFileAdapter.MapFileViewHolder>(MapFileDiffCallback()) {
    
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): MapFileViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_map_file, parent, false)
        return MapFileViewHolder(view, onMapSelected, onMapDeleted)
    }
    
    override fun onBindViewHolder(holder: MapFileViewHolder, position: Int) {
        holder.bind(getItem(position))
    }
    
    class MapFileViewHolder(
        itemView: View,
        private val onMapSelected: (MapFileManager.MapFile) -> Unit,
        private val onMapDeleted: (MapFileManager.MapFile) -> Unit
    ) : RecyclerView.ViewHolder(itemView) {
        
        private val ivMapIcon: ImageView = itemView.findViewById(R.id.ivMapIcon)
        private val tvMapName: TextView = itemView.findViewById(R.id.tvMapName)
        private val tvMapInfo: TextView = itemView.findViewById(R.id.tvMapInfo)
        private val btnDelete: ImageButton = itemView.findViewById(R.id.btnDeleteMap)
        
        private val dateFormat = SimpleDateFormat("MMM dd, yyyy", Locale.getDefault())
        
        fun bind(mapFile: MapFileManager.MapFile) {
            tvMapName.text = mapFile.displayName
            tvMapInfo.text = "${mapFile.sizeFormatted} • ${dateFormat.format(Date(mapFile.lastModified))}"
            
            // All maps are always active (green checkmark)
            ivMapIcon.setImageResource(R.drawable.ic_check_circle)
            ivMapIcon.setColorFilter(0xFF4CAF50.toInt()) // Green color
            
            // Handle tap to show info
            itemView.setOnClickListener {
                onMapSelected(mapFile)
            }
            
            // Handle deletion
            btnDelete.setOnClickListener {
                onMapDeleted(mapFile)
            }
        }
    }
    
    class MapFileDiffCallback : DiffUtil.ItemCallback<MapFileManager.MapFile>() {
        override fun areItemsTheSame(
            oldItem: MapFileManager.MapFile,
            newItem: MapFileManager.MapFile
        ): Boolean {
            return oldItem.path == newItem.path
        }
        
        override fun areContentsTheSame(
            oldItem: MapFileManager.MapFile,
            newItem: MapFileManager.MapFile
        ): Boolean {
            return oldItem == newItem
        }
    }
}
