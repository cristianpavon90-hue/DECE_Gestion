package com.example.decegestin

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.decegestin.databinding.FragmentHistoryBinding
import com.example.decegestin.databinding.ItemHistoryCaseHeaderBinding
import com.example.decegestin.databinding.ItemHistoryHeaderBinding
import com.example.decegestin.databinding.ItemHistoryRecordBinding
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.*

class HistoryFragment : Fragment() {

    private var _binding: FragmentHistoryBinding? = null
    private val binding get() = _binding!!

    private val database = FirebaseDatabase.getInstance().reference
    private val auth = FirebaseAuth.getInstance()
    private var historyListener: ValueEventListener? = null
    
    private val displayItems = mutableListOf<HistoryUiItem>()
    private lateinit var adapter: HistoryAdapter

    private var currentInstKey: String? = null

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentHistoryBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.backIcon.setOnClickListener { findNavController().navigateUp() }

        setupRecyclerView()
        loadHistory()
    }

    private fun setupRecyclerView() {
        adapter = HistoryAdapter()
        binding.recyclerHistory.layoutManager = LinearLayoutManager(context)
        binding.recyclerHistory.adapter = adapter
    }

    private fun loadHistory() {
        val user = auth.currentUser ?: return
        binding.progressHistory.visibility = View.VISIBLE
        
        database.child("users").child(user.uid).child("institution").get().addOnSuccessListener { snapshot ->
            val institution = snapshot.value?.toString() ?: return@addOnSuccessListener
            val safeKey = AppUtils.getSafeKey(institution)
            listenToForms(safeKey)
        }
    }

    private fun listenToForms(instKey: String) {
        currentInstKey = instKey
        historyListener = database.child("institutions").child(instKey).child("forms")
            .addValueEventListener(object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    if (_binding == null) return
                    binding.progressHistory.visibility = View.GONE
                    
                    val allForms = mutableListOf<Map<String, Any>>()
                    for (child in snapshot.children) {
                        val form = child.value as? Map<String, Any>
                        if (form != null) {
                            val mutableForm = form.toMutableMap()
                            mutableForm["formId"] = child.key ?: ""
                            allForms.add(mutableForm)
                        }
                    }

                    if (allForms.isEmpty()) {
                        binding.textEmptyHistory.visibility = View.VISIBLE
                        displayItems.clear()
                        adapter.notifyDataSetChanged()
                        return
                    }

                    binding.textEmptyHistory.visibility = View.GONE
                    processAndGroupForms(allForms)
                }

                override fun onCancelled(error: DatabaseError) {
                    if (_binding == null) return
                    binding.progressHistory.visibility = View.GONE
                }
            })
    }

    private fun processAndGroupForms(forms: List<Map<String, Any>>) {
        // Grouping: Grade -> Parallel -> CaseType -> List
        val grouped = forms.groupBy { it["grado"]?.toString() ?: "Sin Grado" }
            .mapValues { (_, gradeForms) ->
                gradeForms.groupBy { it["paralelo"]?.toString() ?: "Sin Paralelo" }
                    .mapValues { (_, parallelForms) ->
                        parallelForms.groupBy { it["caseType"]?.toString() ?: "Sin Tipo" }
                    }
            }

        displayItems.clear()
        
        // Sort grades using AppUtils.grades order if possible
        val sortedGrades = grouped.keys.sortedBy { grade ->
            AppUtils.grades.indexOf(grade).let { if (it == -1) Int.MAX_VALUE else it }
        }

        for (grade in sortedGrades) {
            val parallels = grouped[grade] ?: continue
            val sortedParallels = parallels.keys.sorted()
            
            for (parallel in sortedParallels) {
                displayItems.add(HistoryUiItem.Header(getString(R.string.history_grade_label, grade) + " - " + getString(R.string.history_parallel_label, parallel)))
                
                val caseTypes = parallels[parallel] ?: continue
                val sortedCaseTypes = caseTypes.keys.sorted()
                
                for (caseType in sortedCaseTypes) {
                    val records = caseTypes[caseType] ?: continue
                    displayItems.add(HistoryUiItem.SubHeader(caseType, records.size))
                    
                    for (record in records) {
                        displayItems.add(HistoryUiItem.Record(
                            record["studentName"]?.toString() ?: "N/N",
                            record["date"]?.toString() ?: "",
                            record["formId"]?.toString() ?: "",
                            record
                        ))
                    }
                }
            }
        }
        adapter.notifyDataSetChanged()
    }

    sealed class HistoryUiItem {
        data class Header(val title: String) : HistoryUiItem()
        data class SubHeader(val type: String, val count: Int) : HistoryUiItem()
        data class Record(val name: String, val date: String, val id: String, val fullData: Map<String, Any>) : HistoryUiItem()
    }

    inner class HistoryAdapter : RecyclerView.Adapter<RecyclerView.ViewHolder>() {
        private val TYPE_HEADER = 0
        private val TYPE_SUBHEADER = 1
        private val TYPE_RECORD = 2

        override fun getItemViewType(position: Int): Int {
            return when (displayItems[position]) {
                is HistoryUiItem.Header -> TYPE_HEADER
                is HistoryUiItem.SubHeader -> TYPE_SUBHEADER
                is HistoryUiItem.Record -> TYPE_RECORD
            }
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
            val inflater = LayoutInflater.from(parent.context)
            return when (viewType) {
                TYPE_HEADER -> HeaderViewHolder(ItemHistoryHeaderBinding.inflate(inflater, parent, false))
                TYPE_SUBHEADER -> SubHeaderViewHolder(ItemHistoryCaseHeaderBinding.inflate(inflater, parent, false))
                else -> RecordViewHolder(ItemHistoryRecordBinding.inflate(inflater, parent, false))
            }
        }

        override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
            val item = displayItems[position]
            when (holder) {
                is HeaderViewHolder -> holder.bind(item as HistoryUiItem.Header)
                is SubHeaderViewHolder -> holder.bind(item as HistoryUiItem.SubHeader)
                is RecordViewHolder -> holder.bind(item as HistoryUiItem.Record)
            }
        }

        override fun getItemCount() = displayItems.size

        inner class HeaderViewHolder(val b: ItemHistoryHeaderBinding) : RecyclerView.ViewHolder(b.root) {
            fun bind(item: HistoryUiItem.Header) { b.textHeaderTitle.text = item.title }
        }

        inner class SubHeaderViewHolder(val b: ItemHistoryCaseHeaderBinding) : RecyclerView.ViewHolder(b.root) {
            fun bind(item: HistoryUiItem.SubHeader) {
                b.textCaseType.text = item.type
                b.textCaseCount.text = getString(R.string.history_case_count, item.count)
            }
        }

        inner class RecordViewHolder(val b: ItemHistoryRecordBinding) : RecyclerView.ViewHolder(b.root) {
            fun bind(item: HistoryUiItem.Record) {
                b.textStudentName.text = item.name
                b.textRecordDate.text = item.date
                
                b.recordInfoContainer.setOnClickListener {
                    showEditConfirmation(item)
                }

                b.btnDeleteForm.setOnClickListener {
                    showDeleteConfirmation(item)
                }
            }
        }
    }

    private fun showEditConfirmation(item: HistoryUiItem.Record) {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle("Confirmación")
            .setMessage("¿Estás seguro que desea modificar?")
            .setPositiveButton("SI") { _, _ ->
                navigateToEdit(item)
            }
            .setNegativeButton("NO", null)
            .show()
    }

    private fun showDeleteConfirmation(item: HistoryUiItem.Record) {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle("Advertencia")
            .setMessage("Esta apunto de eliminar estos datos")
            .setPositiveButton("SI") { _, _ ->
                deleteForm(item)
            }
            .setNegativeButton("NO", null)
            .show()
    }

    private fun deleteForm(item: HistoryUiItem.Record) {
        val key = currentInstKey ?: return
        database.child("institutions").child(key).child("forms").child(item.id).removeValue()
            .addOnSuccessListener {
                showToast("Registro eliminado con éxito")
            }
            .addOnFailureListener { e ->
                showToast("Error al eliminar: ${e.message}")
            }
    }

    private fun navigateToEdit(item: HistoryUiItem.Record) {
        // Enviar todos los datos necesarios para recrear el formulario
        val bundle = Bundle().apply {
            putString("formId", item.id)
            putString("studentName", item.name)
            putString("grado", item.fullData["grado"]?.toString())
            putString("paralelo", item.fullData["paralelo"]?.toString())
            putString("caseType", item.fullData["caseType"]?.toString())
            putString("date", item.fullData["date"]?.toString())
            putString("observations", item.fullData["observations"]?.toString())
            putString("derivedBy", item.fullData["derivedBy"]?.toString())
            
            // Acciones marcadas
            val actions = item.fullData["actions"] as? Map<String, Boolean>
            actions?.let {
                val actionList = ArrayList(it.keys)
                putStringArrayList("selectedActions", actionList)
            }

            // Datos extra de Violencia Sexual si existen
            val extras = item.fullData["sexualViolenceExtras"] as? Map<String, String>
            extras?.let {
                putString("repName", it["repName"])
                putString("repPhone", it["repPhone"])
                putString("reminderDay", it["reminderDay"])
                putString("reminderFreq", it["reminderFreq"])
            }
        }
        findNavController().navigate(R.id.NewFormFragment, bundle)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        historyListener?.let { 
            currentInstKey?.let { key ->
                database.child("institutions").child(key).child("forms").removeEventListener(it)
            }
        }
        _binding = null
    }
}
