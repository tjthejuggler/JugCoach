package com.example.jugcoach

import android.app.Application
import dagger.hilt.android.HiltAndroidApp

@HiltAndroidApp
class JugCoachApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        // Firebase will be initialized in FirebaseManager
    }
}
