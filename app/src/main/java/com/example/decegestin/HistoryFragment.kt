package com.example.decegestin

import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.core.content.edit
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
import com.skydoves.balloon.*
import java.text.SimpleDateFormat
import java.util.*

class HistoryFragment : Fragment() {

    private var _binding: FragmentHistoryBinding? = null
    private val binding get() = _binding!!

    private val database = FirebaseDatabase.getInstance().reference
    private val auth = FirebaseAuth.getInstance()

    private val formsList = mutableListOf<UnifiedRecord>()
    private val informesList = mutableListOf<UnifiedRecord>()
    private val educandoList = mutableListOf<UnifiedRecord>()
    private val derivacionesList = mutableListOf<UnifiedRecord>()

    private val displayItems = mutableListOf<HistoryUiItem>()
    private lateinit var adapter: HistoryAdapter

    private var currentInstKey: String? = null

    private val listeners = mutableListOf<Pair<DatabaseReference, ValueEventListener>>()

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentHistoryBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        binding.topBar.applyTopBarPadding()

        binding.backIcon.setOnClickListener { findNavController().navigateUp() }

        setupRecyclerView()
        loadHistory()

        binding.tabLayoutFilter.addOnTabSelectedListener(object : com.google.android.material.tabs.TabLayout.OnTabSelectedListener {
            override fun onTabSelected(tab: com.google.android.material.tabs.TabLayout.Tab?) {
                refreshUI()
            }
            override fun onTabUnselected(tab: com.google.android.material.tabs.TabLayout.Tab?) {}
            override fun onTabReselected(tab: com.google.android.material.tabs.TabLayout.Tab?) {}
        })

        binding.btnSummary.setOnClickListener {
            findNavController().navigate(R.id.action_HistoryFragment_to_HistorySummaryFragment)
        }

        binding.btnTutorial.setOnClickListener { startTutorial() }
        checkFirstTimeTutorial()
    }

    private fun checkFirstTimeTutorial() {
        val uid = auth.currentUser?.uid ?: return
        val prefs = requireContext().getSharedPreferences("HistoryTutorialPrefs_$uid", Context.MODE_PRIVATE)
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
            setText(getString(R.string.tut_history_step1))
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
            setText(getString(R.string.tut_history_step2))
            setTextColorResource(R.color.white)
            setTextSize(14f)
            setBackgroundColorResource(R.color.md_theme_primary)
            setBalloonAnimation(BalloonAnimation.ELASTIC)
            setLifecycleOwner(viewLifecycleOwner)
            setDismissWhenClicked(true)
        }

        val b3 = createBalloon(requireContext()) {
            setArrowSize(10)
            setArrowOrientation(ArrowOrientation.TOP)
            setArrowPosition(0.85f)
            setWidthRatio(0.75f)
            setHeight(BalloonSizeSpec.WRAP)
            setText(getString(R.string.tut_history_step3))
            setTextColorResource(R.color.white)
            setTextSize(14f)
            setBackgroundColorResource(R.color.md_theme_primary)
            setBalloonAnimation(BalloonAnimation.ELASTIC)
            setLifecycleOwner(viewLifecycleOwner)
            setDismissWhenClicked(true)
        }

        b1.showAlignBottom(binding.tabLayoutFilter)
        b1.setOnBalloonDismissListener {
            if (_binding != null) b2.showAlignBottom(binding.recyclerHistory)
        }
        b2.setOnBalloonDismissListener {
            if (_binding != null) b3.showAlignBottom(binding.btnSummary)
        }
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
            if (_binding == null) return@addOnSuccessListener
            val institution = snapshot.value?.toString() ?: return@addOnSuccessListener
            val safeKey = AppUtils.getSafeKey(institution)
            currentInstKey = safeKey
            startListeningToAll(safeKey)
        }
    }

    private fun startListeningToAll(instKey: String) {
        clearListeners()

        // 1. Formularios
        val formsRef = database.child("institutions").child(instKey).child("forms")
        val formsListener = formsRef.addValueEventListener(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                formsList.clear()
                for (child in snapshot.children) {
                    val data = child.value as? Map<String, Any> ?: continue
                    formsList.add(UnifiedRecord(
                        id = child.key ?: "",
                        name = data["studentName"]?.toString() ?: "N/N",
                        date = data["date"]?.toString() ?: "",
                        grado = data["grado"]?.toString() ?: "S/G",
                        paralelo = data["paralelo"]?.toString() ?: "S/P",
                        type = data["caseType"]?.toString() ?: "Formulario",
                        source = "FORM",
                        fullData = data
                    ))
                }
                refreshUI()
            }
            override fun onCancelled(error: DatabaseError) {}
        })
        listeners.add(formsRef to formsListener)

        // 2. Informes Técnicos
        val informesRef = database.child("institutions").child(instKey).child("categories").child("Informes Técnicos")
        val informesListener = informesRef.addValueEventListener(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                informesList.clear()
                for (child in snapshot.children) {
                    val data = child.value as? Map<String, Any> ?: continue
                    val cursoParalelo = data["cursoParalelo"]?.toString() ?: "General"
                    // Parsing cursoParalelo: "1 EGB \"A\""
                    val grado = cursoParalelo.substringBefore(" \"").ifEmpty { "General" }
                    val paralelo = cursoParalelo.substringAfter("\"").substringBefore("\"").ifEmpty { "-" }
                    
                    val timestamp = data["timestamp"] as? Long ?: 0L
                    val dateStr = if (timestamp > 0) SimpleDateFormat("dd/MM/yyyy", Locale.getDefault()).format(Date(timestamp)) else "N/A"

                    informesList.add(UnifiedRecord(
                        id = child.key ?: "",
                        name = "Inf: ${data["tipoInforme"]?.toString() ?: "Técnico"}",
                        date = dateStr,
                        grado = grado,
                        paralelo = paralelo,
                        type = "Informe Técnico",
                        source = "INFO",
                        fullData = data
                    ))
                }
                refreshUI()
            }
            override fun onCancelled(error: DatabaseError) {}
        })
        listeners.add(informesRef to informesListener)

        // 3. Educando en Familia
        val educandoRef = database.child("institutions").child(instKey).child("educando")
        val educandoListener = educandoRef.addValueEventListener(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                educandoList.clear()
                for (child in snapshot.children) {
                    val record = child.getValue(EducandoRecord::class.java) ?: continue
                    educandoList.add(UnifiedRecord(
                        id = record.id,
                        name = record.nombreCompleto,
                        date = record.anio.toString(),
                        grado = "Educando",
                        paralelo = "Familia",
                        type = "Docente",
                        source = "EDUC",
                        fullData = mapOf("record" to record)
                    ))
                }
                refreshUI()
            }
            override fun onCancelled(error: DatabaseError) {}
        })
        listeners.add(educandoRef to educandoListener)

        // 4. Derivaciones (MSP y UDAI)
        val categoriesRef = database.child("institutions").child(instKey).child("categories")
        val derivacionesListener = categoriesRef.addValueEventListener(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                derivacionesList.clear()
                val cats = listOf("Derivación MSP", "Derivación UDAI")
                cats.forEach { catName ->
                    val catData = snapshot.child(catName)
                    for (child in catData.children) {
                        val data = child.value as? Map<String, Any> ?: continue
                        val studentName = data["studentName"]?.toString() ?: continue
                        val grado = data["grado"]?.toString() ?: "S/G"
                        val paralelo = data["paralelo"]?.toString() ?: "S/P"
                        val docDate = data["docDate"]?.toString() ?: ""

                        derivacionesList.add(UnifiedRecord(
                            id = child.key ?: "",
                            name = "$studentName (#${child.key})",
                            date = docDate,
                            grado = grado,
                            paralelo = paralelo,
                            type = catName,
                            source = "DERIV",
                            fullData = data
                        ))
                    }
                }
                refreshUI()
            }
            override fun onCancelled(error: DatabaseError) {}
        })
        listeners.add(categoriesRef to derivacionesListener)
    }

    private fun refreshUI() {
        if (_binding == null) return
        binding.progressHistory.visibility = View.GONE
        
        val selectedTab = binding.tabLayoutFilter.selectedTabPosition
        val filtered = when (selectedTab) {
            1 -> formsList + derivacionesList // Solo Estudiantes
            2 -> informesList + educandoList  // Resto de Datos
            else -> formsList + informesList + educandoList + derivacionesList // Todos
        }

        if (filtered.isEmpty()) {
            binding.textEmptyHistory.visibility = View.VISIBLE
            displayItems.clear()
            adapter.notifyDataSetChanged()
            return
        }

        binding.textEmptyHistory.visibility = View.GONE
        processAndGroup(filtered)
    }

    private fun processAndGroup(records: List<UnifiedRecord>) {
        val grouped = records.groupBy { it.grado }
            .mapValues { (_, gradeRecords) ->
                gradeFormsToMap(gradeRecords)
            }

        displayItems.clear()
        
        val sortedGrades = grouped.keys.sortedBy { grade ->
            val index = AppUtils.grades.indexOf(grade)
            if (index == -1) {
                if (grade == "Educando") -1 else Int.MAX_VALUE 
            } else index
        }

        for (grade in sortedGrades) {
            val parallels = grouped[grade] ?: continue
            val sortedParallels = parallels.keys.sorted()
            
            for (parallel in sortedParallels) {
                val title = if (grade == "Educando") "Educando en Familia" 
                           else "${getString(R.string.history_grade_label, grade)} - ${getString(R.string.history_parallel_label, parallel)}"
                
                displayItems.add(HistoryUiItem.Header(title))
                
                val types = parallels[parallel] ?: continue
                for (type in types.keys.sorted()) {
                    val list = types[type] ?: continue
                    displayItems.add(HistoryUiItem.SubHeader(type, list.size))
                    for (rec in list) {
                        displayItems.add(HistoryUiItem.Record(rec.name, rec.date, rec.id, rec.fullData, rec.source))
                    }
                }
            }
        }
        adapter.notifyDataSetChanged()
    }

    private fun gradeFormsToMap(records: List<UnifiedRecord>): Map<String, Map<String, List<UnifiedRecord>>> {
        return records.groupBy { it.paralelo }
            .mapValues { (_, parallelRecords) ->
                parallelRecords.groupBy { it.type }
            }
    }

    private fun clearListeners() {
        listeners.forEach { (ref, listener) -> ref.removeEventListener(listener) }
        listeners.clear()
    }

    data class UnifiedRecord(
        val id: String,
        val name: String,
        val date: String,
        val grado: String,
        val paralelo: String,
        val type: String,
        val source: String, // FORM, INFO, EDUC
        val fullData: Map<String, Any>
    )

    sealed class HistoryUiItem {
        data class Header(val title: String) : HistoryUiItem()
        data class SubHeader(val type: String, val count: Int) : HistoryUiItem()
        data class Record(val name: String, val date: String, val id: String, val fullData: Map<String, Any>, val source: String) : HistoryUiItem()
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
                
                val isForm = item.source == "FORM"
                val currentUid = auth.currentUser?.uid
                val creatorUid = if (isForm) item.fullData["createdBy"]?.toString() else ""
                val isOwner = !isForm || currentUid == creatorUid

                b.recordInfoContainer.setOnClickListener {
                    if (isForm) {
                        if (isOwner) showEditConfirmation(item)
                        else showToast("Solo el propietario puede editar este formulario")
                    } else {
                        showDetailsDialog(item)
                    }
                }

                b.btnDeleteForm.visibility = if (isOwner) View.VISIBLE else View.GONE
                b.btnDeleteForm.setOnClickListener {
                    showDeleteConfirmation(item)
                }
            }
        }
    }

    private fun showDetailsDialog(item: HistoryUiItem.Record) {
        val msg = when (item.source) {
            "INFO" -> {
                "Audiencia: ${item.fullData["audiencia"]}\nParticipantes: ${item.fullData["participantes"]}"
            }
            "EDUC" -> {
                val rec = item.fullData["record"] as? EducandoRecord
                "Calificación: ${rec?.calificacion}"
            }
            "DERIV" -> {
                val citaDate = item.fullData["citaDate"]?.toString()
                val citaTime = item.fullData["citaTime"]?.toString()
                val citaNum = item.fullData["citaNumero"]?.toString()
                var details = "Estudiante: ${item.fullData["studentName"]}\nCurso: ${item.fullData["grado"]} \"${item.fullData["paralelo"]}\""
                if (!citaDate.isNullOrEmpty()) {
                    details += "\nCita #$citaNum: $citaDate a las $citaTime"
                }
                details
            }
            else -> ""
        }
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(item.name)
            .setMessage(msg)
            .setPositiveButton("Cerrar", null)
            .show()
    }

    private fun showEditConfirmation(item: HistoryUiItem.Record) {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.dialog_confirmation)
            .setMessage("¿Estás seguro que desea modificar?")
            .setPositiveButton("SI") { _, _ -> navigateToEdit(item) }
            .setNegativeButton("NO", null)
            .show()
    }

    private fun showDeleteConfirmation(item: HistoryUiItem.Record) {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.warning)
            .setMessage("Esta apunto de eliminar estos datos")
            .setPositiveButton("SI") { _, _ -> deleteRecord(item) }
            .setNegativeButton("NO", null)
            .show()
    }

    private fun deleteRecord(item: HistoryUiItem.Record) {
        val key = currentInstKey ?: return
        val ref = when (item.source) {
            "FORM" -> database.child("institutions").child(key).child("forms").child(item.id)
            "INFO" -> database.child("institutions").child(key).child("categories").child("Informes Técnicos").child(item.id)
            "EDUC" -> database.child("institutions").child(key).child("educando").child(item.id)
            else -> return
        }
        
        ref.removeValue()
            .addOnSuccessListener { showToast(getString(R.string.success_record_deleted)) }
            .addOnFailureListener { e -> showToast("Error: ${e.message}") }
    }

    private fun navigateToEdit(item: HistoryUiItem.Record) {
        val bundle = Bundle().apply {
            putString("formId", item.id)
            putString("studentName", item.name)
            putString("grado", item.fullData["grado"]?.toString())
            putString("paralelo", item.fullData["paralelo"]?.toString())
            putString("caseType", item.fullData["caseType"]?.toString())
            putString("date", item.fullData["date"]?.toString())
            putString("observations", item.fullData["observations"]?.toString())
            putString("derivedBy", item.fullData["derivedBy"]?.toString())
            putString("createdBy", item.fullData["createdBy"]?.toString())
            
            val actions = item.fullData["actions"] as? Map<String, Boolean>
            actions?.let { putStringArrayList("selectedActions", ArrayList(it.keys)) }

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
        clearListeners()
        _binding = null
    }
}
