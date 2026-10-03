package com.example.decegestin

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.decegestin.databinding.FragmentHistorySummaryBinding
import com.google.android.material.tabs.TabLayout
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.*
import java.util.*

class HistorySummaryFragment : Fragment() {

    private var _binding: FragmentHistorySummaryBinding? = null
    private val binding get() = _binding!!

    private val database = FirebaseDatabase.getInstance().reference
    private val auth = FirebaseAuth.getInstance()
    private var allRecords = mutableListOf<Map<String, Any>>()
    private val adapter = HistorySummaryAdapter()

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentHistorySummaryBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        binding.topBar.applyTopBarPadding()

        binding.backIcon.setOnClickListener { findNavController().navigateUp() }
        binding.recyclerSummary.layoutManager = LinearLayoutManager(context)
        binding.recyclerSummary.adapter = adapter

        loadData()

        binding.tabLayoutPeriod.addOnTabSelectedListener(object : TabLayout.OnTabSelectedListener {
            override fun onTabSelected(tab: TabLayout.Tab?) { updateSummary() }
            override fun onTabUnselected(tab: TabLayout.Tab?) {}
            override fun onTabReselected(tab: TabLayout.Tab?) {}
        })
    }

    private fun loadData() {
        val user = auth.currentUser ?: return
        binding.progressSummary.visibility = View.VISIBLE

        database.child("users").child(user.uid).child("institution").get().addOnSuccessListener { snapshot ->
            if (_binding == null) return@addOnSuccessListener
            val institution = snapshot.value?.toString() ?: return@addOnSuccessListener
            val safeKey = AppUtils.getSafeKey(institution)
            fetchAllData(safeKey)
        }
    }

    private fun fetchAllData(instKey: String) {
        val records = mutableListOf<Map<String, Any>>()
        var completed = 0
        val totalSources = 4

        val checkCompletion = {
            completed++
            if (completed == totalSources) {
                if (_binding != null) {
                    binding.progressSummary.visibility = View.GONE
                    allRecords = records
                    updateSummary()
                }
            }
        }

        // 1. Formularios
        database.child("institutions").child(instKey).child("forms").get().addOnSuccessListener { snapshot ->
            for (child in snapshot.children) {
                @Suppress("UNCHECKED_CAST")
                (child.value as? Map<String, Any>)?.let { records.add(it) }
            }
            checkCompletion()
        }.addOnFailureListener { checkCompletion() }

        // 2. Informes
        database.child("institutions").child(instKey).child("categories").child("Informes Técnicos").get().addOnSuccessListener { snapshot ->
            for (child in snapshot.children) {
                @Suppress("UNCHECKED_CAST")
                (child.value as? Map<String, Any>)?.let { 
                    val mutable = it.toMutableMap()
                    mutable["caseType"] = it["tipoInforme"] ?: "Informe Técnico"
                    mutable["grado"] = it["cursoParalelo"]?.toString()?.substringBefore(" \"") ?: "General"
                    mutable["paralelo"] = it["cursoParalelo"]?.toString()?.substringAfter("\"")?.substringBefore("\"") ?: ""
                    records.add(mutable)
                }
            }
            checkCompletion()
        }.addOnFailureListener { checkCompletion() }

        // 3. Educando
        database.child("institutions").child(instKey).child("educando").get().addOnSuccessListener { snapshot ->
            for (child in snapshot.children) {
                val record = child.getValue(EducandoRecord::class.java) ?: continue
                records.add(mapOf(
                    "caseType" to "Educando en Familia",
                    "grado" to "Docente",
                    "paralelo" to "",
                    "timestamp" to record.timestamp
                ))
            }
            checkCompletion()
        }.addOnFailureListener { checkCompletion() }

        // 4. Derivaciones (MSP y UDAI)
        database.child("institutions").child(instKey).child("categories").get().addOnSuccessListener { snapshot ->
            val cats = listOf("Derivación MSP", "Derivación UDAI")
            cats.forEach { catName ->
                val catData = snapshot.child(catName)
                for (child in catData.children) {
                    @Suppress("UNCHECKED_CAST")
                    val data = child.value as? Map<String, Any> ?: continue
                    val mutable = data.toMutableMap()
                    mutable["caseType"] = catName
                    records.add(mutable)
                }
            }
            checkCompletion()
        }.addOnFailureListener { checkCompletion() }
    }

    private fun updateSummary() {
        val selectedTab = binding.tabLayoutPeriod.selectedTabPosition
        val now = Calendar.getInstance()
        
        val filteredRecords = allRecords.filter { record ->
            val timestamp = record["timestamp"] as? Long ?: 0L
            if (timestamp == 0L) return@filter false
            
            val recordCal = Calendar.getInstance().apply { timeInMillis = timestamp }
            
            when (selectedTab) {
                0 -> { // Año
                    recordCal[Calendar.YEAR] == now[Calendar.YEAR]
                }
                1 -> { // Mes
                    recordCal[Calendar.YEAR] == now[Calendar.YEAR] &&
                            recordCal[Calendar.MONTH] == now[Calendar.MONTH]
                }
                2 -> { // Trimestre
                    recordCal[Calendar.YEAR] == now[Calendar.YEAR] &&
                            (recordCal[Calendar.MONTH] / 3) == (now[Calendar.MONTH] / 3)
                }
                3 -> { // Semestre
                    recordCal[Calendar.YEAR] == now[Calendar.YEAR] &&
                            (recordCal[Calendar.MONTH] / 6) == (now[Calendar.MONTH] / 6)
                }
                else -> true
            }
        }

        val grouped = filteredRecords.groupBy { it["caseType"]?.toString() ?: "Otros" }
            .map { (caseType, list) ->
                val breakdown = list.groupBy { 
                    val grado = it["grado"]?.toString() ?: "S/G"
                    val paralelo = it["paralelo"]?.toString() ?: ""
                    if (paralelo.isNotEmpty()) "$grado $paralelo" else grado
                }.mapValues { it.value.size }
                
                val details = breakdown.entries.joinToString(", ") { "${it.key}: ${it.value}" }
                SummaryItem(caseType, list.size, details)
            }.sortedByDescending { it.totalCount }

        adapter.setData(grouped)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
