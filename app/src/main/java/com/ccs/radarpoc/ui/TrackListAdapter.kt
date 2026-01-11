package com.ccs.radarpoc.ui

import android.graphics.drawable.GradientDrawable
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.ccs.radarpoc.R
import com.ccs.radarpoc.ui.main.TrackFilterMode
import com.ccs.radarpoc.ui.main.TrackMarkerHelper
import com.ccs.radarpoc.ui.main.TrackSortMode
import com.ccs.radarpoc.ui.main.TrackUiModel

/**
 * Improved TrackListAdapter with:
 * - Simplified item display (minimal info)
 * - Selection highlight
 * - Swipe actions (lock/dismiss)
 * - Filter and sort support
 * - Smooth animations
 */
class TrackListAdapter(
    private val onTrackClick: (TrackUiModel) -> Unit,
    private val onTrackLockToggle: ((TrackUiModel) -> Unit)? = null,
    private val onTrackCenter: ((TrackUiModel) -> Unit)? = null
) : ListAdapter<TrackListAdapter.TrackListItem, TrackListAdapter.TrackViewHolder>(TrackDiffCallback()) {
    
    // Currently selected track ID for highlight
    var selectedTrackId: String? = null
        set(value) {
            val oldValue = field
            field = value
            // Refresh old and new selections
            currentList.forEachIndexed { index, item ->
                if (item.track.id == oldValue || item.track.id == value) {
                    notifyItemChanged(index, PAYLOAD_SELECTION)
                }
            }
        }
    
    // Filter and sort modes
    private var filterMode: TrackFilterMode = TrackFilterMode.ALL
    private var sortMode: TrackSortMode = TrackSortMode.DISTANCE
    private var fullList: List<TrackListItem> = emptyList()
    
    companion object {
        private const val PAYLOAD_SELECTION = "selection"
        private const val PAYLOAD_STATUS = "status"
    }
    
    /**
     * Track list item with computed display data
     */
    data class TrackListItem(
        val track: TrackUiModel,
        val distance: String,
        val distanceMeters: Double,
        val isLocked: Boolean,
        val classification: String
    )
    
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): TrackViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_track, parent, false)
        return TrackViewHolder(view, onTrackClick, onTrackLockToggle, onTrackCenter)
    }
    
    override fun onBindViewHolder(holder: TrackViewHolder, position: Int) {
        holder.bind(getItem(position), selectedTrackId)
    }
    
    override fun onBindViewHolder(holder: TrackViewHolder, position: Int, payloads: List<Any>) {
        if (payloads.isEmpty()) {
            onBindViewHolder(holder, position)
        } else {
            // Partial update for better performance
            payloads.forEach { payload ->
                when (payload) {
                    PAYLOAD_SELECTION -> holder.updateSelection(getItem(position), selectedTrackId)
                    PAYLOAD_STATUS -> holder.updateStatus(getItem(position))
                }
            }
        }
    }
    
    /**
     * Submit list with filtering and sorting
     */
    fun submitFullList(items: List<TrackListItem>) {
        fullList = items
        applyFilterAndSort()
    }
    
    /**
     * Set filter mode and refresh
     */
    fun setFilterMode(mode: TrackFilterMode) {
        filterMode = mode
        applyFilterAndSort()
    }
    
    /**
     * Set sort mode and refresh
     */
    fun setSortMode(mode: TrackSortMode) {
        sortMode = mode
        applyFilterAndSort()
    }
    
    private fun applyFilterAndSort() {
        var filtered = when (filterMode) {
            TrackFilterMode.ALL -> fullList
            TrackFilterMode.ACTIVE -> fullList.filter { !it.track.isStale }
            TrackFilterMode.STALE -> fullList.filter { it.track.isStale }
            TrackFilterMode.LOCKED -> fullList.filter { it.isLocked }
        }
        
        val sorted = when (sortMode) {
            TrackSortMode.DISTANCE -> filtered.sortedBy { it.distanceMeters }
            TrackSortMode.ID -> filtered.sortedBy { it.track.id }
            TrackSortMode.SPEED -> filtered.sortedByDescending { it.track.speed }
            TrackSortMode.ALTITUDE -> filtered.sortedByDescending { it.track.altitude }
            TrackSortMode.LAST_SEEN -> filtered // Already sorted by update time
        }
        
        submitList(sorted)
    }
    
    /**
     * Get counts for stats display
     */
    fun getCounts(): Triple<Int, Int, Int> {
        val active = fullList.count { !it.track.isStale }
        val stale = fullList.count { it.track.isStale }
        val locked = fullList.count { it.isLocked }
        return Triple(active, stale, locked)
    }
    
    /**
     * Attach swipe helper for swipe-to-lock gesture
     */
    fun attachSwipeHelper(recyclerView: RecyclerView) {
        val swipeCallback = object : ItemTouchHelper.SimpleCallback(
            0, ItemTouchHelper.RIGHT
        ) {
            override fun onMove(
                recyclerView: RecyclerView,
                viewHolder: RecyclerView.ViewHolder,
                target: RecyclerView.ViewHolder
            ): Boolean = false
            
            override fun onSwiped(viewHolder: RecyclerView.ViewHolder, direction: Int) {
                val position = viewHolder.adapterPosition
                if (position != RecyclerView.NO_POSITION) {
                    val item = getItem(position)
                    onTrackLockToggle?.invoke(item.track)
                    // Reset swipe - the list will be updated by parent
                    notifyItemChanged(position)
                }
            }
            
            override fun getSwipeThreshold(viewHolder: RecyclerView.ViewHolder): Float = 0.5f
        }
        
        ItemTouchHelper(swipeCallback).attachToRecyclerView(recyclerView)
    }
    
    class TrackViewHolder(
        itemView: View,
        private val onTrackClick: (TrackUiModel) -> Unit,
        private val onTrackLockToggle: ((TrackUiModel) -> Unit)?,
        private val onTrackCenter: ((TrackUiModel) -> Unit)?
    ) : RecyclerView.ViewHolder(itemView) {
        
        private val trackCard: com.google.android.material.card.MaterialCardView = 
            itemView.findViewById(R.id.trackCard)
        private val vTrackColorBg: View = itemView.findViewById(R.id.vTrackColorBg)
        private val ivTrackDirection: ImageView = itemView.findViewById(R.id.ivTrackDirection)
        private val tvTrackId: TextView = itemView.findViewById(R.id.tvTrackId)
        private val ivLockedIcon: ImageView = itemView.findViewById(R.id.ivLockedIcon)
        private val tvTrackDistance: TextView = itemView.findViewById(R.id.tvTrackDistance)
        private val tvClassification: TextView = itemView.findViewById(R.id.tvClassification)
        private val vStatusIndicator: View = itemView.findViewById(R.id.vStatusIndicator)
        private val vSelectionHighlight: View = itemView.findViewById(R.id.vSelectionHighlight)
        
        private var currentItem: TrackListItem? = null
        
        fun bind(item: TrackListItem, selectedTrackId: String?) {
            currentItem = item
            val track = item.track
            
            // Track ID
            tvTrackId.text = "Track ${track.id}"
            
            // Track color indicator
            val color = TrackMarkerHelper.getColorForTrack(track.id)
            val colorWithAlpha = if (track.isStale) {
                // Desaturate for stale
                0xFF666666.toInt()
            } else {
                color
            }
            (vTrackColorBg.background as? GradientDrawable)?.setColor(colorWithAlpha)
                ?: vTrackColorBg.setBackgroundColor(colorWithAlpha)
            
            // Direction arrow rotation based on heading
            ivTrackDirection.rotation = track.heading.toFloat()
            
            // Locked icon
            ivLockedIcon.visibility = if (item.isLocked) View.VISIBLE else View.GONE
            
            // Distance
            tvTrackDistance.text = item.distance
            
            // Classification
            tvClassification.text = item.classification
            tvClassification.setTextColor(
                when (item.classification.uppercase()) {
                    "DRONE" -> ContextCompat.getColor(itemView.context, R.color.classification_drone)
                    "BIRD" -> ContextCompat.getColor(itemView.context, R.color.classification_bird)
                    else -> ContextCompat.getColor(itemView.context, R.color.classification_unknown)
                }
            )
            
            // Status indicator (green = active, orange = stale)
            vStatusIndicator.setBackgroundResource(
                if (track.isStale) R.drawable.bg_status_dot_orange
                else R.drawable.bg_status_dot_green
            )
            
            // Selection highlight
            updateSelection(item, selectedTrackId)
            
            // Click handlers
            itemView.setOnClickListener {
                onTrackClick(track)
            }
            
            itemView.setOnLongClickListener {
                onTrackLockToggle?.invoke(track)
                true
            }
        }
        
        fun updateSelection(item: TrackListItem, selectedTrackId: String?) {
            val isSelected = item.track.id == selectedTrackId
            vSelectionHighlight.visibility = if (isSelected) View.VISIBLE else View.GONE
            
            // Also update card background slightly
            trackCard.setCardBackgroundColor(
                if (isSelected) {
                    ContextCompat.getColor(itemView.context, R.color.track_item_selected)
                } else {
                    ContextCompat.getColor(itemView.context, R.color.track_item_background)
                }
            )
        }
        
        fun updateStatus(item: TrackListItem) {
            val track = item.track
            
            // Update stale appearance
            val color = TrackMarkerHelper.getColorForTrack(track.id)
            val colorWithAlpha = if (track.isStale) 0xFF666666.toInt() else color
            (vTrackColorBg.background as? GradientDrawable)?.setColor(colorWithAlpha)
            
            // Status indicator
            vStatusIndicator.setBackgroundResource(
                if (track.isStale) R.drawable.bg_status_dot_orange
                else R.drawable.bg_status_dot_green
            )
            
            // Locked icon
            ivLockedIcon.visibility = if (item.isLocked) View.VISIBLE else View.GONE
        }
    }
    
    private class TrackDiffCallback : DiffUtil.ItemCallback<TrackListItem>() {
        override fun areItemsTheSame(oldItem: TrackListItem, newItem: TrackListItem): Boolean {
            return oldItem.track.id == newItem.track.id
        }
        
        override fun areContentsTheSame(oldItem: TrackListItem, newItem: TrackListItem): Boolean {
            return oldItem == newItem
        }
        
        override fun getChangePayload(oldItem: TrackListItem, newItem: TrackListItem): Any? {
            return when {
                oldItem.isLocked != newItem.isLocked ||
                oldItem.track.isStale != newItem.track.isStale -> PAYLOAD_STATUS
                else -> null
            }
        }
    }
}
