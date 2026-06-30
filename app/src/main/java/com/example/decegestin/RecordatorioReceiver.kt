package com.example.decegestin

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import androidx.core.app.NotificationCompat

class RecordatorioReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val nombreRep = intent.getStringExtra("nombreRep") ?: "Representante"
        val telefonoRep = intent.getStringExtra("telefonoRep") ?: ""
        val idCaso = intent.getIntExtra("idCaso", 0)

        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val channelId = "dece_seguimientos"

        // Crear canal para versiones Android 8.0 o superiores
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(channelId, "Seguimientos DECE", NotificationManager.IMPORTANCE_HIGH)
            notificationManager.createNotificationChannel(channel)
        }

        // 1. ACCIÓN: Enviar WhatsApp
        val mensajeWpp = "Estimado/a $nombreRep, le saludamos del DECE. Solicitamos su asistencia a la institución para la entrevista de seguimiento programada."
        val wppUri = Uri.parse("https://api.whatsapp.com/send?phone=$telefonoRep&text=${Uri.encode(mensajeWpp)}")
        val wppIntent = Intent(Intent.ACTION_VIEW, wppUri)
        val wppPendingIntent = PendingIntent.getActivity(context, idCaso + 1, wppIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)

        // 2. ACCIÓN: Llamar por teléfono
        val callUri = Uri.parse("tel:$telefonoRep")
        val callIntent = Intent(Intent.ACTION_DIAL, callUri)
        val callPendingIntent = PendingIntent.getActivity(context, idCaso + 2, callIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)

        // Construir la notificación con botones de acción
        val builder = NotificationCompat.Builder(context, channelId)
            .setSmallIcon(R.drawable.ic_launcher_foreground) 
            .setContentTitle("Seguimiento Obligatorio DECE")
            .setContentText("Realizar seguimiento a: $nombreRep")
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .addAction(0, "Enviar WhatsApp", wppPendingIntent) 
            .addAction(0, "Llamar", callPendingIntent)     
            .build()

        notificationManager.notify(idCaso, builder)
    }
}
