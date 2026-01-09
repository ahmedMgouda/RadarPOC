package com.ccs.radarpoc.di

import android.content.Context
import com.ccs.radarpoc.data.AppSettings
import com.ccs.radarpoc.data.repository.DroneConfig
import com.ccs.radarpoc.data.repository.DroneRepository
import com.ccs.radarpoc.data.repository.RadarConfig
import com.ccs.radarpoc.data.repository.RadarRepository
import com.ccs.radarpoc.domain.repository.IDroneRepository
import com.ccs.radarpoc.domain.repository.IRadarRepository
import com.ccs.radarpoc.network.RadarApiService
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Hilt module for providing application-level dependencies.
 * Installed in SingletonComponent to provide singleton scoped dependencies.
 */
@Module
@InstallIn(SingletonComponent::class)
object AppModule {
    
    /**
     * Provides AppSettings instance.
     */
    @Provides
    @Singleton
    fun provideAppSettings(
        @ApplicationContext context: Context
    ): AppSettings {
        return AppSettings(context)
    }
    
    /**
     * Provides RadarConfig based on AppSettings.
     */
    @Provides
    fun provideRadarConfig(appSettings: AppSettings): RadarConfig {
        return RadarConfig(
            pollIntervalSeconds = appSettings.pollInterval,
            staleTimeoutSeconds = appSettings.staleTimeout
        )
    }
    
    /**
     * Provides DroneConfig based on AppSettings.
     */
    @Provides
    fun provideDroneConfig(appSettings: AppSettings): DroneConfig {
        return DroneConfig(
            missionUpdateIntervalMs = appSettings.missionUpdateInterval * 1000L,
            minimumDistanceMeters = appSettings.minimumDistanceMeters
        )
    }
    
    /**
     * Binds RadarRepository implementation to IRadarRepository interface.
     */
    @Provides
    @Singleton
    fun provideRadarRepository(
        apiService: RadarApiService,
        config: RadarConfig
    ): IRadarRepository {
        return RadarRepository(apiService, config)
    }
    
    /**
     * Binds DroneRepository implementation to IDroneRepository interface.
     */
    @Provides
    @Singleton
    fun provideDroneRepository(
        config: DroneConfig
    ): IDroneRepository {
        return DroneRepository(config)
    }
}
