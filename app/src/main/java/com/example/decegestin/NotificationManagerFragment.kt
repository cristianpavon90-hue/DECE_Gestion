package com.example.decegestin

import android.app.AlarmManager
import android.app.PendingIntent
import android.app.TimePickerDialog
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.AutoCompleteTextView
import android.widget.Button
import android.widget.TextView
import androidx.core.content.edit
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.decegestin.databinding.FragmentNotificationManagerBinding
import com.example.decegestin.databinding.ItemReminderBinding
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.*
import com.skydoves.balloon.*
import java.util.*
import java.util.concurrent.TimeUnit

class NotificationManagerFragment : Fragment() {

    private var _binding: FragmentNotificationManagerBinding? = null
    private val binding get() = _binding!!

    private val database = FirebaseDatabase.getInstance().reference
    private val auth = FirebaseAuth.getInstance()
    private var remindersListener: ValueEventListener? = null
    private var currentInstKey: String? = null
    private val remindersList = mutableListOf<Map<String, Any>>()
    private lateinit var adapter: RemindersAdapter

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentNotificationManagerBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        binding.topBar.applyTopBarPadding()

        binding.backIcon.setOnClickListener { findNavController().navigateUp() }

        setupRecyclerView()
        loadReminders()

        binding.btnTutorial.setOnClickListener { startTutorial() }
        checkFirstTimeTutorial()
    }

    private fun checkFirstTimeTutorial() {
        val uid = auth.currentUser?.uid ?: return
        val prefs = requireContext().getSharedPreferences("NotifTutorialPrefs_$uid", Context.MODE_PRIVATE)
        val isFirstTime = prefs.getBoolean("tutorial_shown", true)
        if (isFirstTime) {
            binding.root.postDelayed({
                if (_binding != null) startTutorial()
                prefs.edit { putBoolean("tutorial_shown", false) }
            }, 800)
        }
    }

    private fun startTutorial() {
        val b1 = createBalloon(requireContext()) {
            setArrowSize(10)
            setArrowOrientation(ArrowOrientation.TOP)
            setArrowPosition(0.5f)
            setWidthRatio(0.8f)
            setHeight(BalloonSizeSpec.WRAP)
            setText(getString(R.string.tut_notif_step1))
            setTextColorResource(R.color.white)
            setTextSize(14f)
            setBackgroundColorResource(R.color.md_theme_primary)
            setBalloonAnimation(BalloonAnimation.ELASTIC)
            setLifecycleOwner(viewLifecycleOwner)
            setDismissWhenClicked(true)
        }

        val b2 = createBalloon(requireContext()) {
            setArrowSize(10)
            setArrowOrientation(ArrowOrientation.TOP)
            setArrowPosition(0.5f)
            setWidthRatio(0.8f)
            setHeight(BalloonSizeSpec.WRAP)
            setText(getString(R.string.tut_notif_step2))
            setTextColorResource(R.color.white)
            setTextSize(14f)
            setBackgroundColorResource(R.color.md_theme_primary)
            setBalloonAnimation(BalloonAnimation.ELASTIC)
            setLifecycleOwner(viewLifecycleOwner)
            setDismissWhenClicked(true)
        }

        b1.showAlignBottom(binding.recyclerReminders)
        b1.setOnBalloonDismissListener {
            if (_binding != null) b2.showAlignBottom(binding.recyclerReminders)
        }
    }

    private fun setupRecyclerView() {
        adapter = RemindersAdapter()
        binding.recyclerReminders.layoutManager = LinearLayoutManager(context)
        binding.recyclerReminders.adapter = adapter
    }

    private fun loadReminders() {
        val user = auth.currentUser ?: return
        database.child("users").child(user.uid).child("institution").get().addOnSuccessListener { snapshot ->
            if (_binding == null) return@addOnSuccessListener
            val institution = snapshot.value?.toString() ?: return@addOnSuccessListener
            currentInstKey = AppUtils.getSafeKey(institution)
            listenToReminders()
        }
    }

    private fun listenToReminders() {
        val key = currentInstKey ?: return
        remindersListener = database.child("institutions").child(key).child("reminders").addValueEventListener(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                if (_binding == null) return
                remindersList.clear()
                val currentUid = auth.currentUser?.uid
                for (child in snapshot.children) {
                    val reminder = child.value as? Map<String, Any>
                    if (reminder != null && reminder["createdBy"] == currentUid) {
                        remindersList.add(reminder)
                    }
                }
                
                binding.textNoReminders.visibility = if (remindersList.isEmpty()) View.VISIBLE else View.GONE
                adapter.notifyDataSetChanged()
            }
            override fun onCancelled(error: DatabaseError) {}
        })
    }

    private fun cancelReminder(reminder: Map<String, Any>) {
        val id = (reminder["id"] as? Long)?.toInt() ?: return
        val key = currentInstKey ?: return

        val alarmManager = requireContext().getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val intent = Intent(requireContext(), RecordatorioReceiver::class.java)
        val pendingIntent = PendingIntent.getBroadcast(
            requireContext(), id, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        alarmManager.cancel(pendingIntent)

        database.child("institutions").child(key).child("reminders").child(id.toString()).removeValue()
            .addOnSuccessListener { showToast(getString(R.string.notif_manager_cancelled)) }
    }

    private fun showEditDialog(reminder: Map<String, Any>) {
        val dialogView = LayoutInflater.from(requireContext()).inflate(R.layout.dialog_edit_reminder, null)
        val editDay = dialogView.findViewById<AutoCompleteTextView>(R.id.edit_day)
        val editFreq = dialogView.findViewById<AutoCompleteTextView>(R.id.edit_freq)
        val btnPickTime = dialogView.findViewById<Button>(R.id.btn_pick_time)
        val tvSelectedTime = dialogView.findViewById<TextView>(R.id.tv_selected_time)

        // Set current values
        var currentHour = (reminder["hour"] as? Long)?.toInt() ?: 8
        var currentMinute = (reminder["minute"] as? Long)?.toInt() ?: 0
        val currentDay = reminder["day"]?.toString() ?: "Lunes"
        val currentFreq = reminder["freq"]?.toString() ?: "Semanal"

        editDay.setText(currentDay, false)
        editFreq.setText(currentFreq, false)
        tvSelectedTime.text = getString(R.string.notif_manager_time_label, currentHour, currentMinute)

        val dayAdapter = ArrayAdapter(requireContext(), android.R.layout.simple_dropdown_item_1line, AppUtils.daysOfWeek)
        editDay.setAdapter(dayAdapter)
        val freqAdapter = ArrayAdapter(requireContext(), android.R.layout.simple_dropdown_item_1line, AppUtils.frequencies)
        editFreq.setAdapter(freqAdapter)

        btnPickTime.setOnClickListener {
            TimePickerDialog(requireContext(), { _, h, m ->
                currentHour = h
                currentMinute = m
                tvSelectedTime.text = getString(R.string.notif_manager_time_label, h, m)
            }, currentHour, currentMinute, true).show()
        }

        MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.notif_manager_edit_title)
            .setView(dialogView)
            .setPositiveButton(R.string.notif_manager_save) { _, _ ->
                val newDay = editDay.text.toString()
                val newFreq = editFreq.text.toString()
                updateReminder(reminder, newDay, newFreq, currentHour, currentMinute)
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private fun updateReminder(reminder: Map<String, Any>, day: String, freq: String, hour: Int, minute: Int) {
        val id = (reminder["id"] as? Long)?.toInt() ?: return
        val key = currentInstKey ?: return

        val updates = mapOf(
            "day" to day,
            "freq" to freq,
            "hour" to hour,
            "minute" to minute,
            "timestamp" to ServerValue.TIMESTAMP
        )

        database.child("institutions").child(key).child("reminders").child(id.toString()).updateChildren(updates)
            .addOnSuccessListener {
                rescheduleAlarm(reminder, day, freq, hour, minute)
                showToast(getString(R.string.notif_manager_updated))
            }
    }

    private fun rescheduleAlarm(reminder: Map<String, Any>, day: String, freq: String, hour: Int, minute: Int) {
        val id = (reminder["id"] as? Long)?.toInt() ?: return
        val repName = reminder["repName"]?.toString() ?: ""
        val repPhone = reminder["repPhone"]?.toString() ?: ""

        val diaCalendario = when (day.lowercase()) {
            "lunes" -> Calendar.MONDAY
            "martes" -> Calendar.TUESDAY
            "miércoles", "miercoles" -> Calendar.WEDNESDAY
            "jueves" -> Calendar.THURSDAY
            "viernes" -> Calendar.FRIDAY
            else -> Calendar.MONDAY
        }

        val unDiaMs = AlarmManager.INTERVAL_DAY
        val intervaloRepeticion = when (freq) {
            "Semanal" -> unDiaMs * 7
            "Bisemanal" -> unDiaMs * 14
            "Mensual" -> unDiaMs * 28
            else -> unDiaMs * 7
        }

        val alarmManager = requireContext().getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val reminderCalendar = Calendar.getInstance().apply {
            set(Calendar.DAY_OF_WEEK, diaCalendario)
            set(Calendar.HOUR_OF_DAY, hour)
            set(Calendar.MINUTE, minute)
            set(Calendar.SECOND, 0)
            if (before(Calendar.getInstance())) {
                add(Calendar.WEEK_OF_YEAR, 1)
            }
        }

        val intent = Intent(requireContext(), RecordatorioReceiver::class.java).apply {
            putExtra("nombreRep", repName)
            putExtra("telefonoRep", repPhone)
            putExtra("idCaso", id)
            putExtra("freq", freq)
            addFlags(Intent.FLAG_RECEIVER_FOREGROUND)
        }

        AppUtils.scheduleExactAlarm(requireContext(), id, reminderCalendar.timeInMillis, intent)
    }

    private fun getTimeRemaining(dayStr: String, hour: Int, minute: Int): String {
        val diaCalendario = when (dayStr.lowercase()) {
            "lunes" -> Calendar.MONDAY
            "martes" -> Calendar.TUESDAY
            "miércoles", "miercoles" -> Calendar.WEDNESDAY
            "jueves" -> Calendar.THURSDAY
            "viernes" -> Calendar.FRIDAY
            else -> Calendar.MONDAY
        }

        val now = Calendar.getInstance()
        val target = Calendar.getInstance().apply {
            set(Calendar.DAY_OF_WEEK, diaCalendario)
            set(Calendar.HOUR_OF_DAY, hour)
            set(Calendar.MINUTE, minute)
            set(Calendar.SECOND, 0)
            if (before(now)) {
                add(Calendar.WEEK_OF_YEAR, 1)
            }
        }

        val diffMs = target.timeInMillis - now.timeInMillis
        val days = TimeUnit.MILLISECONDS.toDays(diffMs)
        val hours = TimeUnit.MILLISECONDS.toHours(diffMs) % 24
        val minutes = TimeUnit.MILLISECONDS.toMinutes(diffMs) % 60

        return when {
            days > 0 -> getString(R.string.notif_manager_remaining_days, days, hours, minutes)
            hours > 0 -> getString(R.string.notif_manager_remaining_hours, hours, minutes)
            else -> getString(R.string.notif_manager_remaining_minutes, minutes)
        }
    }

    inner class RemindersAdapter : RecyclerView.Adapter<RemindersAdapter.ReminderViewHolder>() {
        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ReminderViewHolder {
            val b = ItemReminderBinding.inflate(LayoutInflater.from(parent.context), parent, false)
            return ReminderViewHolder(b)
        }

        override fun onBindViewHolder(holder: ReminderViewHolder, position: Int) {
            holder.bind(remindersList[position])
        }

        override fun getItemCount() = remindersList.size

        inner class ReminderViewHolder(private val b: ItemReminderBinding) : RecyclerView.ViewHolder(b.root) {
            fun bind(item: Map<String, Any>) {
                b.textStudentName.text = item["studentName"]?.toString()
                val day = item["day"]?.toString() ?: "Lunes"
                val freq = item["freq"]?.toString() ?: "Semanal"
                val hour = (item["hour"] as? Long)?.toInt() ?: 8
                val minute = (item["minute"] as? Long)?.toInt() ?: 0
                val repName = item["repName"]?.toString() ?: ""

                b.textReminderInfo.text = "$day - $freq ($repName) @ ${String.format("%02d:%02d", hour, minute)}"
                b.textTimeRemaining.text = getTimeRemaining(day, hour, minute)
                
                b.btnDeleteReminder.setOnClickListener {
                    cancelReminder(item)
                }

                b.btnEditReminder.setOnClickListener {
                    showEditDialog(item)
                }
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        remindersListener?.let { currentInstKey?.let { key -> database.child("institutions").child(key).child("reminders").removeEventListener(it) } }
        _binding = null
    }
}
