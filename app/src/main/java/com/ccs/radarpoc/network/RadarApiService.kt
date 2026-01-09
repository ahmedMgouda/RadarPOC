package com.ccs.radarpoc.network

import com.ccs.radarpoc.data.RadarTracksResponse
import retrofit2.Response
import retrofit2.http.GET

/**
 * Retrofit service interface for Radar API.
 * Uses suspend functions for proper coroutine integration.
 */
interface RadarApiService {
    
    /**
     * Fetch all radar tracks from the API.
     * Uses Retrofit's native coroutine support for async operations.
     * @return Response containing RadarTracksResponse
     */
    @GET("api/tracks.json")
    suspend fun getTracks(): Response<RadarTracksResponse>
}
