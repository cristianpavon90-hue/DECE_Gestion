package com.example.decegestin

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.FirebaseDatabase
import java.util.*

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED || intent.action == "android.intent.action.QUICKBOOT_POWERON") {
            rescheduleAllAlarms(context)
        }
    }

    private fun rescheduleAllAlarms(context: Context) {
        val user = FirebaseAuth.getInstance().currentUser ?: return
        val database = FirebaseDatabase.getInstance().reference
        
        database.child("users").child(user.uid).child("institution").get().addOnSuccessListener { snapshot ->
            val instName = snapshot.value?.toString() ?: return@addOnSuccessListener
            val safeKey = AppUtils.getSafeKey(instName)
            
            database.child("institutions").child(safeKey).child("reminders").get().addOnSuccessListener { remindersSnapshot ->
                for (child in remindersSnapshot.children) {
                    val reminder = child.value as? Map<String, Any> ?: continue
                    val id = (reminder["id"] as? Long)?.toInt() ?: continue
                    val dayStr = reminder["day"]?.toString() ?: "Lunes"
                    val hour = (reminder["hour"] as? Long)?.toInt() ?: 8
                    val minute = (reminder["minute"] as? Long)?.toInt() ?: 0
                    
                    val diaCalendario = when (dayStr.lowercase()) {
                        "lunes" -> Calendar.MONDAY
                        "martes" -> Calendar.TUESDAY
                        "miércoles", "miercoles" -> Calendar.WEDNESDAY
                        "jueves" -> Calendar.THURSDAY
                        "viernes" -> Calendar.FRIDAY
                        else -> Calendar.MONDAY
                    }

                    val calendar = Calendar.getInstance().apply {
                        set(Calendar.DAY_OF_WEEK, diaCalendario)
                        set(Calendar.HOUR_OF_DAY, hour)
                        set(Calendar.MINUTE, minute)
                        set(Calendar.SECOND, 0)
                        if (before(Calendar.getInstance())) {
                            add(Calendar.WEEK_OF_YEAR, 1)
                        }
                    }

                    val alarmIntent = Intent(context, RecordatorioReceiver::class.java).apply {
                        putExtra("nombreRep", reminder["repName"]?.toString())
                        putExtra("telefonoRep", reminder["repPhone"]?.toString())
                        putExtra("idCaso", id)
                        putExtra("freq", reminder["freq"]?.toString())
                        addFlags(Intent.FLAG_RECEIVER_FOREGROUND)
                    }

                    AppUtils.scheduleExactAlarm(context, id, calendar.timeInMillis, alarmIntent)
                }
            }
        }
    }
}
