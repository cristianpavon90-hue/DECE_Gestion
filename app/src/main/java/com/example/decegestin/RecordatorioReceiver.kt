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
import com.google.firebase.database.FirebaseDatabase
import java.util.*

class RecordatorioReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val nombreRep = intent.getStringExtra("nombreRep") ?: "Representante"
        val telefonoRep = intent.getStringExtra("telefonoRep") ?: ""
        val idCaso = intent.getIntExtra("idCaso", 0)

        // Resubscribir para el siguiente ciclo
        rescheduleNext(context, idCaso, intent)

        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val channelId = "dece_seguimientos"

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(channelId, "Seguimientos DECE", NotificationManager.IMPORTANCE_HIGH)
            notificationManager.createNotificationChannel(channel)
        }

        val mensajeWpp = context.getString(R.string.notif_message_whatsapp, nombreRep)
        val wppPhone = telefonoRep.replace("+", "").replace("\\s+".toRegex(), "")
        val wppUri = Uri.parse("https://api.whatsapp.com/send?phone=$wppPhone&text=${Uri.encode(mensajeWpp)}")
        val wppIntent = Intent(Intent.ACTION_VIEW, wppUri)
        val wppPendingIntent = PendingIntent.getActivity(context, idCaso + 1, wppIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)

        val callUri = Uri.parse("tel:$telefonoRep")
        val callIntent = Intent(Intent.ACTION_DIAL, callUri)
        val callPendingIntent = PendingIntent.getActivity(context, idCaso + 2, callIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)

        val builder = NotificationCompat.Builder(context, channelId)
            .setSmallIcon(android.R.drawable.ic_popup_reminder) 
            .setContentTitle("Seguimiento Obligatorio DECE")
            .setContentText("Realizar seguimiento a: $nombreRep")
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setAutoCancel(true)
            .addAction(0, context.getString(R.string.notif_action_whatsapp), wppPendingIntent) 
            .addAction(0, context.getString(R.string.notif_action_call), callPendingIntent)     
            .build()

        notificationManager.notify(idCaso, builder)
    }

    private fun rescheduleNext(context: Context, id: Int, originalIntent: Intent) {
        val freq = originalIntent.getStringExtra("freq") ?: "Semanal"
        val nextTime = Calendar.getInstance()
        
        when (freq) {
            "Semanal" -> nextTime.add(Calendar.WEEK_OF_YEAR, 1)
            "Bisemanal" -> nextTime.add(Calendar.WEEK_OF_YEAR, 2)
            "Mensual" -> nextTime.add(Calendar.MONTH, 1)
            else -> nextTime.add(Calendar.WEEK_OF_YEAR, 1)
        }

        val nextIntent = Intent(context, RecordatorioReceiver::class.java).apply {
            putExtras(originalIntent)
        }

        AppUtils.scheduleExactAlarm(context, id, nextTime.timeInMillis, nextIntent)
    }
}
