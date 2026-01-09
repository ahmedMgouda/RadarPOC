package com.ccs.radarpoc

import android.app.Application
import dagger.hilt.android.HiltAndroidApp

/**
 * Application class annotated with @HiltAndroidApp.
 * This triggers Hilt's code generation and serves as the application-level dependency container.
 */
@HiltAndroidApp
class RadarPOCApplication : Application() {
    
    override fun onCreate() {
        super.onCreate()
        // Application-level initialization can go here
    }
}
