package com.ccs.radarpoc.ui

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.ccs.radarpoc.R
import com.ccs.radarpoc.ui.main.TrackMarkerHelper
import com.ccs.radarpoc.ui.main.TrackUiModel

/**
 * Adapter for displaying tracks in the navigation drawer
 */
class TrackListAdapter(
    private val onTrackClick: (TrackUiModel) -> Unit
) : ListAdapter<TrackListAdapter.TrackListItem, TrackListAdapter.TrackViewHolder>(TrackDiffCallback()) {
    
    /**
     * Track list item with additional UI data
     */
    data class TrackListItem(
        val track: TrackUiModel,
        val distance: String,
        val isLocked: Boolean
    )
    
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): TrackViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_track, parent, false)
        return TrackViewHolder(view, onTrackClick)
    }
    
    override fun onBindViewHolder(holder: TrackViewHolder, position: Int) {
        holder.bind(getItem(position))
    }
    
    class TrackViewHolder(
        itemView: View,
        private val onTrackClick: (TrackUiModel) -> Unit
    ) : RecyclerView.ViewHolder(itemView) {
        
        private val vTrackColor: View = itemView.findViewById(R.id.vTrackColor)
        private val tvTrackId: TextView = itemView.findViewById(R.id.tvTrackId)
        private val tvTrackDistance: TextView = itemView.findViewById(R.id.tvTrackDistance)
        private val tvStaleIndicator: TextView = itemView.findViewById(R.id.tvStaleIndicator)
        private val tvLockedIndicator: TextView = itemView.findViewById(R.id.tvLockedIndicator)
        
        fun bind(item: TrackListItem) {
            val track = item.track
            
            // Set track ID
            tvTrackId.text = "T-${track.id}"
            
            // Set color indicator
            val color = TrackMarkerHelper.getColorForTrack(track.id)
            vTrackColor.setBackgroundColor(if (track.isStale) 0xFF1A1A1A.toInt() else color)
            
            // Set distance
            tvTrackDistance.text = item.distance
            
            // Show stale indicator
            tvStaleIndicator.visibility = if (track.isStale) View.VISIBLE else View.GONE
            
            // Show locked indicator
            tvLockedIndicator.visibility = if (item.isLocked) View.VISIBLE else View.GONE
            
            // Click handler
            itemView.setOnClickListener {
                onTrackClick(track)
            }
        }
    }
    
    private class TrackDiffCallback : DiffUtil.ItemCallback<TrackListItem>() {
        override fun areItemsTheSame(oldItem: TrackListItem, newItem: TrackListItem): Boolean {
            return oldItem.track.id == newItem.track.id
        }
        
        override fun areContentsTheSame(oldItem: TrackListItem, newItem: TrackListItem): Boolean {
            return oldItem == newItem
        }
    }
}
