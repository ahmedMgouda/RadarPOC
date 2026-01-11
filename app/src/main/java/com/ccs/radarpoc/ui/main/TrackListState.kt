package com.ccs.radarpoc.ui.main

/**
 * Filter mode for track list
 */
enum class TrackFilterMode {
    ALL,
    ACTIVE,
    STALE,
    LOCKED
}

/**
 * Sort mode for track list
 */
enum class TrackSortMode {
    DISTANCE,      // Closest first
    ID,            // By track ID
    SPEED,         // Fastest first
    ALTITUDE,      // Highest first
    LAST_SEEN      // Most recent first
}

/**
 * State for track list drawer
 */
data class TrackListState(
    val filterMode: TrackFilterMode = TrackFilterMode.ALL,
    val sortMode: TrackSortMode = TrackSortMode.DISTANCE,
    val selectedTrackId: String? = null,
    val lastUpdateTime: Long = System.currentTimeMillis()
) {
    /**
     * Get human-readable time since last update
     */
    fun getLastUpdateText(): String {
        val elapsed = System.currentTimeMillis() - lastUpdateTime
        return when {
            elapsed < 1000 -> "Just now"
            elapsed < 60_000 -> "${elapsed / 1000}s ago"
            elapsed < 3600_000 -> "${elapsed / 60_000}m ago"
            else -> "${elapsed / 3600_000}h ago"
        }
    }
}

/**
 * Events for track list interactions
 */
sealed class TrackListEvent {
    data class FilterChanged(val mode: TrackFilterMode) : TrackListEvent()
    data class SortChanged(val mode: TrackSortMode) : TrackListEvent()
    data class TrackClicked(val trackId: String) : TrackListEvent()
    data class TrackLockToggled(val trackId: String) : TrackListEvent()
    data class TrackCenterRequested(val trackId: String) : TrackListEvent()
    object ClearAllLocks : TrackListEvent()
    object RefreshRequested : TrackListEvent()
}
