package com.ccs.radarpoc.ui

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.RadioButton
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
        
        private val radioButton: RadioButton = itemView.findViewById(R.id.rbMapFile)
        private val tvMapName: TextView = itemView.findViewById(R.id.tvMapName)
        private val tvMapInfo: TextView = itemView.findViewById(R.id.tvMapInfo)
        private val btnDelete: ImageButton = itemView.findViewById(R.id.btnDeleteMap)
        
        private val dateFormat = SimpleDateFormat("MMM dd, yyyy", Locale.getDefault())
        
        fun bind(mapFile: MapFileManager.MapFile) {
            radioButton.isChecked = mapFile.isActive
            tvMapName.text = mapFile.displayName
            tvMapInfo.text = "${mapFile.sizeFormatted} • ${dateFormat.format(Date(mapFile.lastModified))}"
            
            // Handle selection
            itemView.setOnClickListener {
                onMapSelected(mapFile)
            }
            
            radioButton.setOnClickListener {
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
