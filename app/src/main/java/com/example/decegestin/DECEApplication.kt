package com.example.decegestin

import android.app.Application
import com.google.firebase.database.FirebaseDatabase

class DECEApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        // Habilitar persistencia local globalmente para toda la app y el widget
        try {
            FirebaseDatabase.getInstance().setPersistenceEnabled(true)
        } catch (e: Exception) {
            // Ya estaba habilitado o error de inicialización
        }
    }
}
