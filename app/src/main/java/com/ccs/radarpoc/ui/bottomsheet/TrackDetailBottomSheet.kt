package com.ccs.radarpoc.ui.bottomsheet

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.ContextCompat
import com.ccs.radarpoc.R
import com.ccs.radarpoc.ui.main.TrackMarkerHelper
import com.ccs.radarpoc.ui.main.TrackUiModel
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.google.android.material.button.MaterialButton

/**
 * Material 3 Bottom Sheet for Track Details
 * 
 * Features:
 * - Three states: collapsed, half-expanded, expanded
 * - Swipe to dismiss
 * - Real-time updates
 * - Action buttons for lock, center, navigate
 */
class TrackDetailBottomSheet : BottomSheetDialogFragment() {
    
    companion object {
        private const val ARG_TRACK_ID = "track_id"
        
        fun newInstance(trackId: String): TrackDetailBottomSheet {
            return TrackDetailBottomSheet().apply {
                arguments = Bundle().apply {
                    putString(ARG_TRACK_ID, trackId)
                }
            }
        }
    }
    
    // Views
    private lateinit var vTrackColor: View
    private lateinit var ivTrackDirection: ImageView
    private lateinit var tvTrackTitle: TextView
    private lateinit var tvTrackSubtitle: TextView
    private lateinit var tvLockedBadge: TextView
    private lateinit var tvStaleBadge: TextView
    
    // Quick Stats
    private lateinit var tvDistance: TextView
    private lateinit var tvSpeed: TextView
    private lateinit var tvAltitude: TextView
    private lateinit var tvHeading: TextView
    
    // Detailed Info
    private lateinit var tvPosition: TextView
    private lateinit var tvClassification: TextView
    private lateinit var tvRadarData: TextView
    private lateinit var tvLastUpdate: TextView
    
    // Actions
    private lateinit var btnLock: ImageButton
    private lateinit var btnCenter: MaterialButton
    private lateinit var btnNavigate: MaterialButton
    private lateinit var btnShare: MaterialButton
    
    // Callbacks
    var onLockClicked: ((String) -> Unit)? = null
    var onCenterClicked: ((String) -> Unit)? = null
    var onNavigateClicked: ((String) -> Unit)? = null
    var onDismissed: (() -> Unit)? = null
    
    // Current track data
    private var currentTrack: TrackUiModel? = null
    private var isLocked: Boolean = false
    private var distanceFromDrone: Double? = null
    
    override fun getTheme(): Int = R.style.ThemeOverlay_App_BottomSheetDialog
    
    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.bottom_sheet_track_detail, container, false)
    }
    
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        
        initViews(view)
        setupClickListeners()
        setupBottomSheetBehavior()
        
        // Load initial data if available
        arguments?.getString(ARG_TRACK_ID)?.let { trackId ->
            // Request track data from parent activity
        }
    }
    
    private fun initViews(view: View) {
        // Header
        vTrackColor = view.findViewById(R.id.vTrackColor)
        ivTrackDirection = view.findViewById(R.id.ivTrackDirection)
        tvTrackTitle = view.findViewById(R.id.tvTrackTitle)
        tvTrackSubtitle = view.findViewById(R.id.tvTrackSubtitle)
        tvLockedBadge = view.findViewById(R.id.tvLockedBadge)
        tvStaleBadge = view.findViewById(R.id.tvStaleBadge)
        
        // Quick Stats
        tvDistance = view.findViewById(R.id.tvDistance)
        tvSpeed = view.findViewById(R.id.tvSpeed)
        tvAltitude = view.findViewById(R.id.tvAltitude)
        tvHeading = view.findViewById(R.id.tvHeading)
        
        // Detailed Info
        tvPosition = view.findViewById(R.id.tvPosition)
        tvClassification = view.findViewById(R.id.tvClassification)
        tvRadarData = view.findViewById(R.id.tvRadarData)
        tvLastUpdate = view.findViewById(R.id.tvLastUpdate)
        
        // Actions
        btnLock = view.findViewById(R.id.btnLock)
        btnCenter = view.findViewById(R.id.btnCenter)
        btnNavigate = view.findViewById(R.id.btnNavigate)
        btnShare = view.findViewById(R.id.btnShare)
    }
    
    private fun setupClickListeners() {
        btnLock.setOnClickListener {
            currentTrack?.let { track ->
                onLockClicked?.invoke(track.id)
            }
        }
        
        btnCenter.setOnClickListener {
            currentTrack?.let { track ->
                onCenterClicked?.invoke(track.id)
                dismiss()
            }
        }
        
        btnNavigate.setOnClickListener {
            currentTrack?.let { track ->
                onNavigateClicked?.invoke(track.id)
            }
        }
        
        btnShare.setOnClickListener {
            shareTrackInfo()
        }
    }
    
    private fun setupBottomSheetBehavior() {
        dialog?.setOnShowListener { dialogInterface ->
            val bottomSheet = dialog?.findViewById<View>(
                com.google.android.material.R.id.design_bottom_sheet
            )
            bottomSheet?.let {
                val behavior = BottomSheetBehavior.from(it)
                
                // Set half-expanded as default state
                behavior.state = BottomSheetBehavior.STATE_HALF_EXPANDED
                behavior.halfExpandedRatio = 0.5f
                behavior.skipCollapsed = false
                behavior.isDraggable = true
                behavior.isFitToContents = false
                
                // Calculate peek height based on header content
                val peekHeight = resources.getDimensionPixelSize(R.dimen.bottom_sheet_peek_height)
                behavior.peekHeight = peekHeight
                
                behavior.addBottomSheetCallback(object : BottomSheetBehavior.BottomSheetCallback() {
                    override fun onStateChanged(bottomSheet: View, newState: Int) {
                        when (newState) {
                            BottomSheetBehavior.STATE_HIDDEN -> {
                                onDismissed?.invoke()
                                dismiss()
                            }
                            BottomSheetBehavior.STATE_COLLAPSED -> {
                                // Show minimal info
                            }
                            BottomSheetBehavior.STATE_HALF_EXPANDED -> {
                                // Show main info
                            }
                            BottomSheetBehavior.STATE_EXPANDED -> {
                                // Show all info including actions
                            }
                        }
                    }
                    
                    override fun onSlide(bottomSheet: View, slideOffset: Float) {
                        // Animate content based on slide
                    }
                })
            }
        }
    }
    
    /**
     * Update the bottom sheet with new track data
     */
    fun updateTrack(track: TrackUiModel, isLocked: Boolean, distanceFromDrone: Double? = null) {
        this.currentTrack = track
        this.isLocked = isLocked
        this.distanceFromDrone = distanceFromDrone
        
        if (!isAdded) return
        
        // Update header
        tvTrackTitle.text = "Track ${track.id}"
        
        // Track color indicator
        val color = TrackMarkerHelper.getColorForTrack(track.id)
        vTrackColor.background.setTint(if (track.isStale) 0xFF666666.toInt() else color)
        
        // Direction arrow rotation
        ivTrackDirection.rotation = track.heading.toFloat()
        
        // Badges
        tvLockedBadge.visibility = if (isLocked) View.VISIBLE else View.GONE
        tvStaleBadge.visibility = if (track.isStale) View.VISIBLE else View.GONE
        
        // Subtitle with classification
        val classification = track.track.stats.classifications.maxByOrNull { it.confidence }
        tvTrackSubtitle.text = classification?.type?.uppercase() ?: "UNKNOWN"
        
        // Quick stats
        tvDistance.text = distanceFromDrone?.let { formatDistance(it) } ?: "—"
        tvSpeed.text = "${String.format("%.1f", track.speed)} m/s"
        tvAltitude.text = "${String.format("%.0f", track.altitude)} m"
        tvHeading.text = "${String.format("%.0f", track.heading)}°"
        
        // Detailed info
        tvPosition.text = "${String.format("%.6f", track.latitude)}°, ${String.format("%.6f", track.longitude)}°"
        tvClassification.text = classification?.let { 
            "${it.type} (${String.format("%.0f", it.confidence * 100)}%)" 
        } ?: "Unknown"
        
        val observation = track.track.observation
        tvRadarData.text = "Range: ${String.format("%.0f", observation.range)}m • Az: ${String.format("%.1f", observation.azimuthAngle)}°"
        
        // Last update
        tvLastUpdate.text = "Updated just now"
        
        // Lock button state
        btnLock.setImageResource(
            if (isLocked) R.drawable.ic_lock else R.drawable.ic_lock_open
        )
        btnLock.contentDescription = if (isLocked) "Unlock track" else "Lock track"
        
        // Tint lock button when locked
        val lockTint = if (isLocked) {
            ContextCompat.getColor(requireContext(), R.color.locked_track_color)
        } else {
            ContextCompat.getColor(requireContext(), R.color.icon_secondary)
        }
        btnLock.setColorFilter(lockTint)
    }
    
    private fun formatDistance(meters: Double): String {
        return when {
            meters < 1000 -> "${meters.toInt()} m"
            else -> String.format("%.1f km", meters / 1000)
        }
    }
    
    private fun shareTrackInfo() {
        currentTrack?.let { track ->
            val shareText = buildString {
                appendLine("Track ${track.id}")
                appendLine("Position: ${track.latitude}, ${track.longitude}")
                appendLine("Altitude: ${track.altitude}m")
                appendLine("Speed: ${track.speed} m/s")
                appendLine("Heading: ${track.heading}°")
            }
            
            val intent = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(android.content.Intent.EXTRA_TEXT, shareText)
            }
            startActivity(android.content.Intent.createChooser(intent, "Share Track Info"))
        }
    }
    
    override fun onDestroyView() {
        super.onDestroyView()
        onDismissed?.invoke()
    }
}
