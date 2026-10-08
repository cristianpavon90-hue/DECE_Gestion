package com.example.decegestin

import android.app.Application
import androidx.appcompat.app.AppCompatDelegate
import com.google.firebase.FirebaseApp
import com.google.firebase.appcheck.playintegrity.PlayIntegrityAppCheckProviderFactory
import com.google.firebase.appcheck.ktx.appCheck
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.ktx.Firebase

class DECEApplication : Application() {
    override fun onCreate() {
        super.onCreate()

        // Forzar Modo Claro siempre en toda la aplicación
        AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO)

        // Inicializar Firebase con protección de excepciones
        try {
            FirebaseApp.initializeApp(this)
            Firebase.appCheck.installAppCheckProviderFactory(
                PlayIntegrityAppCheckProviderFactory.getInstance()
            )
        } catch (_: Exception) {}

        // Habilitar persistencia local globalmente para toda la app y el widget
        try {
            FirebaseDatabase.getInstance().setPersistenceEnabled(true)
        } catch (_: Exception) {}
    }
}
