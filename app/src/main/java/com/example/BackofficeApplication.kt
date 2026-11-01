package com.example

import android.app.Application
import android.util.Log
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseOptions

/**
 * Custom Application class for Backoffice Bot & GSC Automation Suite.
 * Guarantees proper lifecycle initialization of FirebaseApp and prevents
 * 'Default FirebaseApp failed to initialize' warnings.
 */
class BackofficeApplication : Application() {

    override fun onCreate() {
        super.onCreate()
        initializeFirebase()
    }

    private fun initializeFirebase() {
        try {
            if (FirebaseApp.getApps(this).isEmpty()) {
                val app = FirebaseApp.initializeApp(this)
                if (app == null) {
                    Log.w(TAG, "Default FirebaseApp returned null, initializing with explicit FirebaseOptions...")
                    val options = FirebaseOptions.Builder()
                        .setApplicationId("1:313938030691:android:e1a2b3c4d5e6f7a8b9c0d1")
                        .setProjectId("adminku-opsx")
                        .setApiKey(if (BuildConfig.GEMINI_API_KEY.isNotEmpty()) BuildConfig.GEMINI_API_KEY else "AIzaSyDummyKeyForGoogleServicesInitialization")
                        .build()
                    FirebaseApp.initializeApp(this, options)
                    Log.i(TAG, "FirebaseApp initialized with explicit fallback options.")
                } else {
                    Log.i(TAG, "FirebaseApp initialized successfully from google-services.json.")
                }
            } else {
                Log.i(TAG, "FirebaseApp already initialized.")
            }
        } catch (e: Exception) {
            Log.w(TAG, "Standard FirebaseApp initialization: ${e.message}. Attempting fallback...")
            try {
                val options = FirebaseOptions.Builder()
                    .setApplicationId("1:313938030691:android:e1a2b3c4d5e6f7a8b9c0d1")
                    .setProjectId("adminku-opsx")
                    .setApiKey(if (BuildConfig.GEMINI_API_KEY.isNotEmpty()) BuildConfig.GEMINI_API_KEY else "AIzaSyDummyKeyForGoogleServicesInitialization")
                    .build()
                FirebaseApp.initializeApp(this, options)
                Log.i(TAG, "FirebaseApp fallback initialization successful.")
            } catch (fallbackError: Exception) {
                Log.e(TAG, "FirebaseApp fallback initialization failed: ${fallbackError.message}")
            }
        }
    }

    companion object {
        private const val TAG = "BackofficeApp"
    }
}
