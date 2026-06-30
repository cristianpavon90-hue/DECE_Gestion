package com.example.decegestin

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.decegestin.databinding.FragmentNotificationManagerBinding
import com.example.decegestin.databinding.ItemReminderBinding
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.*

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

        binding.backIcon.setOnClickListener { findNavController().navigateUp() }

        setupRecyclerView()
        loadReminders()
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
                for (child in snapshot.children) {
                    val reminder = child.value as? Map<String, Any>
                    if (reminder != null) remindersList.add(reminder)
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

        // 1. Cancelar en AlarmManager
        val alarmManager = requireContext().getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val intent = Intent(requireContext(), RecordatorioReceiver::class.java)
        val pendingIntent = PendingIntent.getBroadcast(
            requireContext(), id, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        alarmManager.cancel(pendingIntent)

        // 2. Eliminar de Firebase
        database.child("institutions").child(key).child("reminders").child(id.toString()).removeValue()
            .addOnSuccessListener { showToast("Recordatorio cancelado") }
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
                val info = "${item["day"]} - ${item["freq"]} (${item["repName"]})"
                b.textReminderInfo.text = info
                
                b.btnDeleteReminder.setOnClickListener {
                    cancelReminder(item)
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
