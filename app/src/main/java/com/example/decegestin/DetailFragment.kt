package com.example.decegestin

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.content.Context
import android.graphics.Color
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.AutoCompleteTextView
import android.widget.TextView
import android.widget.Toast
import androidx.core.content.edit
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.decegestin.databinding.DialogSearchExpedienteBinding
import com.example.decegestin.databinding.FragmentDetailBinding
import com.example.decegestin.databinding.ItemCategoryDbRecordBinding
import com.example.decegestin.databinding.ItemExpedienteSearchBinding
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.*
import com.skydoves.balloon.*
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

/**
 * Fragmento que muestra la cuadrícula de números marcados para una categoría específica y bifurcación condicional de expedientes.
 */
class DetailFragment : Fragment() {

    private var _binding: FragmentDetailBinding? = null
    private val binding get() = _binding!!

    private var currentPage = 0
    private val pageSize = 100
    private var categoryTitle = ""
    private var institution = ""

    private val database: DatabaseReference by lazy { FirebaseDatabase.getInstance().reference }
    private val auth: FirebaseAuth by lazy { FirebaseAuth.getInstance() }

    private val checkedData = mutableMapOf<Int, String>()
    private var detailListener: ValueEventListener? = null
    private var userListener: ValueEventListener? = null

    private var currentUserColor: String = "#0097A7"
    private var currentUserName: String = ""

    private lateinit var adapter: NumberAdapter

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentDetailBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        binding.topBar.applyTopBarPadding()

        categoryTitle = arguments?.getString("title") ?: "Detalle"
        institution = arguments?.getString("institution") ?: ""
        binding.detailTitle.text = categoryTitle

        setupRecyclerView()
        setupUserAvatar()
        loadDataFromFirebase()

        binding.backIcon.setOnClickListener { findNavController().navigateUp() }
        binding.btnCategoryDb.setOnClickListener { showCategoryDatabaseDialog() }
        binding.btnNext.setOnClickListener { changePage(1) }
        binding.btnPrev.setOnClickListener { changePage(-1) }

        binding.btnTutorial.setOnClickListener { startTutorial() }
        checkFirstTimeTutorial()
    }

    private fun checkFirstTimeTutorial() {
        val uid = auth.currentUser?.uid ?: return
        val prefs = requireContext().getSharedPreferences("DetailTutorialPrefs_$uid", Context.MODE_PRIVATE)
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
            setText(getString(R.string.tut_detail_step1))
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
            setText(getString(R.string.tut_detail_step2))
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
            setArrowPosition(0.7f)
            setWidthRatio(0.75f)
            setHeight(BalloonSizeSpec.WRAP)
            setText(getString(R.string.tut_detail_step3))
            setTextColorResource(R.color.white)
            setTextSize(14f)
            setBackgroundColorResource(R.color.md_theme_primary)
            setBalloonAnimation(BalloonAnimation.ELASTIC)
            setLifecycleOwner(viewLifecycleOwner)
            setDismissWhenClicked(true)
        }

        b1.showAlignBottom(binding.numbersRecycler)
        b1.setOnBalloonDismissListener {
            if (_binding != null) b2.showAlignBottom(binding.numbersRecycler)
        }
        b2.setOnBalloonDismissListener {
            if (_binding != null) b3.showAlignBottom(binding.btnCategoryDb)
        }
    }

    private fun changePage(delta: Int) {
        currentPage += delta
        updatePage()
    }

    private fun setupUserAvatar() {
        val user = auth.currentUser ?: return
        userListener = database.child("users").child(user.uid).addValueEventListener(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                if (_binding == null || !snapshot.exists()) return

                binding.avatarText.text = snapshot.child("initials").value?.toString() ?: "U"
                currentUserName = snapshot.child("fullName").value?.toString() ?: user.email ?: "Un usuario"
                val colorHex = snapshot.child("profileColor").value?.toString() ?: "#0097A7"
                currentUserColor = colorHex

                try {
                    binding.avatarCard.setCardBackgroundColor(Color.parseColor(colorHex))
                } catch (_: Exception) {}
            }
            override fun onCancelled(error: DatabaseError) {}
        })
    }

    private fun setupRecyclerView() {
        adapter = NumberAdapter { number, isChecked ->
            updateNumberStatus(number, isChecked)
        }
        binding.numbersRecycler.apply {
            layoutManager = GridLayoutManager(context, 5)
            adapter = this@DetailFragment.adapter
        }
    }

    private fun loadDataFromFirebase() {
        if (institution.isEmpty()) return
        val safeInstKey = AppUtils.getSafeKey(institution)

        detailListener = database.child("institutions").child(safeInstKey).child("categories").child(categoryTitle)
            .addValueEventListener(object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    if (_binding == null) return
                    checkedData.clear()
                    for (child in snapshot.children) {
                        val num = child.key?.toIntOrNull()
                        val color = child.child("color").value?.toString()
                        if (num != null && color != null) {
                            checkedData[num] = color
                        }
                    }
                    updatePage()
                }

                override fun onCancelled(error: DatabaseError) {
                    if (error.code == DatabaseError.PERMISSION_DENIED) {
                        showToast(getString(R.string.permission_denied))
                    }
                }
            })
    }

    private fun updatePage() {
        val start = (currentPage * pageSize) + 1
        val end = (currentPage + 1) * pageSize
        binding.pageIndicator.text = getString(R.string.page_range, start, end)

        val numbers = (start..end).toList()
        val currentColors = checkedData.filterKeys { it in start..end }

        adapter.setData(numbers, currentColors)

        binding.btnPrev.visibility = if (currentPage > 0) View.VISIBLE else View.GONE

        val countOnPage = currentColors.size
        binding.countText.text = getString(R.string.page_count, countOnPage, pageSize)
        binding.btnNext.visibility = if (countOnPage == pageSize && currentPage < 3) View.VISIBLE else View.GONE
    }

    private fun updateNumberStatus(number: Int, isChecked: Boolean) {
        if (institution.isEmpty()) return
        val user = auth.currentUser ?: return
        val safeInstKey = AppUtils.getSafeKey(institution)
        val ref = database.child("institutions").child(safeInstKey).child("categories").child(categoryTitle).child(number.toString())

        if (isChecked) {
            when (categoryTitle) {
                "Informes Técnicos" -> showInformeDetalleDialog(number, user.uid, ref)
                "Derivación MSP", "Derivación UDAI" -> showDerivacionDetalleDialog(number, user.uid, ref)
                else -> showDateSelectionDialog(number, user.uid, ref)
            }
        } else {
            if (categoryTitle == "Derivación MSP" || categoryTitle == "Derivación UDAI") {
                showDerivacionMarkedMenu(number, user.uid, ref)
            } else {
                deleteNumberMark(user.uid, ref)
            }
        }
    }

    private fun deleteNumberMark(uid: String, ref: DatabaseReference) {
        ref.get().addOnSuccessListener { snapshot ->
            if (snapshot.child("uid").value?.toString() == uid) {
                ref.removeValue().addOnCompleteListener {
                    context?.let { DashboardWidget.notifyUpdate(it) }
                }
            } else {
                showToast(getString(R.string.only_owner_can_uncheck))
                updatePage()
            }
        }
    }

    private fun isPriorityCase(categoryTitle: String, tipoInforme: String? = null): Boolean {
        if (categoryTitle == "Reporte de Violencia" ||
            categoryTitle == "Derivación MSP" ||
            categoryTitle == "Derivación UDAI") {
            return true
        }
        if (categoryTitle == "Informes Técnicos" && !tipoInforme.isNullOrEmpty()) {
            val lower = tipoInforme.lowercase()
            return lower.contains("riesgo") || lower.contains("violencia") || lower.contains("psicosocial") || lower.contains("ruta")
        }
        return false
    }

    private fun showPriorityDecisionDialog(
        number: Int,
        categoryTitle: String,
        docDate: String,
        studentName: String?,
        tipoInforme: String?,
        ref: DatabaseReference
    ) {
        val formattedNum = String.format("%03d", number)
        val dialogView = LayoutInflater.from(requireContext()).inflate(R.layout.dialog_priority_case_decision, null)

        val textTitle = dialogView.findViewById<TextView>(R.id.text_priority_title)
        val textMessage = dialogView.findViewById<TextView>(R.id.text_priority_message)
        val btnOpenNew = dialogView.findViewById<com.google.android.material.button.MaterialButton>(R.id.btn_open_new_expediente)
        val btnAssignExisting = dialogView.findViewById<com.google.android.material.button.MaterialButton>(R.id.btn_assign_existing_expediente)
        val btnOnlySave = dialogView.findViewById<com.google.android.material.button.MaterialButton>(R.id.btn_only_save_document)

        textTitle.text = getString(R.string.expediente_priority_title, formattedNum)
        textMessage.text = getString(R.string.expediente_priority_msg, categoryTitle, formattedNum)

        var dialog: androidx.appcompat.app.AlertDialog? = null

        // Opción 1: Abrir nuevo expediente
        btnOpenNew.setOnClickListener {
            val bundle = Bundle().apply {
                putString("studentName", studentName ?: "")
                putString("date", docDate)
                putString("originDocument", "$categoryTitle #$formattedNum")
                putString("originCategory", categoryTitle)
                putInt("originCasillaNumber", number)
                putString("caseType", if (!tipoInforme.isNullOrEmpty()) tipoInforme else categoryTitle)
            }
            dialog?.dismiss()
            findNavController().navigate(R.id.NewFormFragment, bundle)
        }

        // Opción 2: Asignar a expediente existente
        btnAssignExisting.setOnClickListener {
            dialog?.dismiss()
            showSearchExistingExpedienteDialog(number, categoryTitle, formattedNum, docDate, studentName, ref)
        }

        // Opción 3: Solo guardar documento
        btnOnlySave.setOnClickListener {
            ref.updateChildren(mapOf("vinculadoAExpediente" to false))
            showToast("Documento guardado en el numerador")
            dialog?.dismiss()
            updatePage()
        }

        dialog = MaterialAlertDialogBuilder(requireContext())
            .setView(dialogView)
            .setCancelable(false)
            .create()

        dialog.show()
    }

    private fun showSearchExistingExpedienteDialog(
        number: Int,
        categoryTitle: String,
        formattedNum: String,
        docDate: String,
        studentName: String?,
        ref: DatabaseReference
    ) {
        if (institution.isEmpty()) return
        val safeInstKey = AppUtils.getSafeKey(institution)

        val dialogBinding = DialogSearchExpedienteBinding.inflate(LayoutInflater.from(requireContext()))
        val allExpedientes = mutableListOf<Map<String, Any>>()
        val filteredExpedientes = mutableListOf<Map<String, Any>>()

        var searchDialog: androidx.appcompat.app.AlertDialog? = null

        val adapter = object : RecyclerView.Adapter<ExpedienteViewHolder>() {
            override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ExpedienteViewHolder {
                val b = ItemExpedienteSearchBinding.inflate(LayoutInflater.from(parent.context), parent, false)
                return ExpedienteViewHolder(b)
            }

            override fun onBindViewHolder(holder: ExpedienteViewHolder, position: Int) {
                val currentPos = holder.adapterPosition
                if (currentPos == RecyclerView.NO_POSITION || currentPos >= filteredExpedientes.size) return

                val item = filteredExpedientes[currentPos]
                val student = item["studentName"]?.toString() ?: "Estudiante"
                val grado = item["grado"]?.toString() ?: ""
                val paralelo = item["paralelo"]?.toString() ?: ""
                val caseType = item["caseType"]?.toString() ?: ""
                val date = item["date"]?.toString() ?: ""
                val formId = item["formId"]?.toString() ?: ""

                holder.binding.textExpedienteStudent.text = student
                holder.binding.textExpedienteDetails.text = "$grado \"$paralelo\" | $caseType | $date"

                holder.binding.root.setOnClickListener {
                    val anexoData = mapOf(
                        "idCasilla" to number,
                        "numeroCorrelativo" to formattedNum,
                        "tipoOrigen" to categoryTitle,
                        "docDate" to docDate,
                        "studentName" to (studentName ?: student),
                        "timestamp" to ServerValue.TIMESTAMP
                    )

                    database.child("institutions").child(safeInstKey).child("forms").child(formId)
                        .child("documentosAnexos").push().setValue(anexoData)

                    val casillaUpdates = mapOf(
                        "vinculadoAExpediente" to true,
                        "idExpediente" to formId
                    )
                    ref.updateChildren(casillaUpdates).addOnSuccessListener {
                        showToast(getString(R.string.expediente_annexed_success))
                        searchDialog?.dismiss()
                        updatePage()
                    }
                }
            }

            override fun getItemCount() = filteredExpedientes.size
        }

        dialogBinding.recyclerExpedientes.layoutManager = LinearLayoutManager(requireContext())
        dialogBinding.recyclerExpedientes.adapter = adapter

        database.child("institutions").child(safeInstKey).child("forms").get().addOnSuccessListener { snapshot ->
            if (_binding == null) return@addOnSuccessListener
            allExpedientes.clear()
            for (child in snapshot.children) {
                val formMap = child.value as? Map<String, Any> ?: continue
                val mutable = formMap.toMutableMap()
                mutable["formId"] = child.key ?: ""
                allExpedientes.add(mutable)
            }

            val initialQuery = studentName ?: ""
            dialogBinding.editSearchExpediente.setText(initialQuery)

            val applyFilter = { query: String ->
                filteredExpedientes.clear()
                val q = query.trim().lowercase()
                if (q.isEmpty()) {
                    filteredExpedientes.addAll(allExpedientes)
                } else {
                    filteredExpedientes.addAll(allExpedientes.filter { item ->
                        val name = item["studentName"]?.toString()?.lowercase() ?: ""
                        val caseType = item["caseType"]?.toString()?.lowercase() ?: ""
                        name.contains(q) || caseType.contains(q)
                    })
                }
                dialogBinding.textEmptyExpedientes.visibility = if (filteredExpedientes.isEmpty()) View.VISIBLE else View.GONE
                adapter.notifyDataSetChanged()
            }

            applyFilter(initialQuery)

            dialogBinding.editSearchExpediente.addTextChangedListener(object : TextWatcher {
                override fun afterTextChanged(s: Editable?) {
                    applyFilter(s?.toString() ?: "")
                }
                override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
                override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            })
        }

        searchDialog = MaterialAlertDialogBuilder(requireContext())
            .setView(dialogBinding.root)
            .setNegativeButton(R.string.cancel) { _, _ -> updatePage() }
            .create()

        searchDialog.show()
    }

    inner class ExpedienteViewHolder(val binding: ItemExpedienteSearchBinding) : RecyclerView.ViewHolder(binding.root)

    private fun showDerivacionMarkedMenu(number: Int, uid: String, ref: DatabaseReference) {
        val options = arrayOf("Agendar Cita", "Editar Datos de Estudiante", "Eliminar (Desmarcar)")

        MaterialAlertDialogBuilder(requireContext())
            .setTitle("$categoryTitle ${String.format("%03d", number)}")
            .setItems(options) { _, which ->
                when (which) {
                    0 -> showAgendarCitaDialog(number, ref)
                    1 -> showDerivacionDetalleDialog(number, uid, ref)
                    2 -> deleteNumberMark(uid, ref)
                }
            }
            .setNegativeButton(R.string.cancel) { _, _ -> updatePage() }
            .setOnCancelListener { updatePage() }
            .show()
    }

    private fun showAgendarCitaDialog(number: Int, ref: DatabaseReference) {
        val dialogView = LayoutInflater.from(requireContext()).inflate(R.layout.dialog_agendar_cita, null)
        val editCitaDate = dialogView.findViewById<TextInputEditText>(R.id.edit_cita_date)
        val editCitaTime = dialogView.findViewById<TextInputEditText>(R.id.edit_cita_time)
        val editCitaNum = dialogView.findViewById<TextInputEditText>(R.id.edit_cita_numero)

        val calendar = Calendar.getInstance()
        val sdfDate = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault())
        val sdfTime = SimpleDateFormat("HH:mm", Locale.getDefault())

        editCitaDate.setText(sdfDate.format(calendar.time))
        editCitaTime.setText(sdfTime.format(calendar.time))

        editCitaDate.setOnClickListener {
            DatePickerDialog(requireContext(), { _, y, m, d ->
                calendar.set(Calendar.YEAR, y)
                calendar.set(Calendar.MONTH, m)
                calendar.set(Calendar.DAY_OF_MONTH, d)
                editCitaDate.setText(sdfDate.format(calendar.time))
            }, calendar.get(Calendar.YEAR), calendar.get(Calendar.MONTH), calendar.get(Calendar.DAY_OF_MONTH)).show()
        }

        editCitaTime.setOnClickListener {
            TimePickerDialog(requireContext(), { _, h, m ->
                calendar.set(Calendar.HOUR_OF_DAY, h)
                calendar.set(Calendar.MINUTE, m)
                editCitaTime.setText(sdfTime.format(calendar.time))
            }, calendar.get(Calendar.HOUR_OF_DAY), calendar.get(Calendar.MINUTE), true).show()
        }

        MaterialAlertDialogBuilder(requireContext())
            .setView(dialogView)
            .setPositiveButton(R.string.save) { _, _ ->
                val date = editCitaDate.text.toString()
                val time = editCitaTime.text.toString()
                val num = editCitaNum.text.toString().trim()

                if (date.isEmpty() || time.isEmpty() || num.isEmpty()) {
                    showToast("Completa los datos de la cita")
                    return@setPositiveButton
                }

                val citaData = mapOf(
                    "citaDate" to date,
                    "citaTime" to time,
                    "citaNumero" to num
                )
                ref.updateChildren(citaData).addOnSuccessListener {
                    showToast("Cita agendada con éxito")
                    sendCategoryNotification(number, "$categoryTitle (Cita Agendada #$num)")
                }
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private fun showDerivacionDetalleDialog(number: Int, uid: String, ref: DatabaseReference) {
        val dialogView = LayoutInflater.from(requireContext()).inflate(R.layout.dialog_derivacion_details, null)

        val editName = dialogView.findViewById<TextInputEditText>(R.id.edit_student_name)
        val editGrado = dialogView.findViewById<AutoCompleteTextView>(R.id.edit_grado)
        val editParalelo = dialogView.findViewById<AutoCompleteTextView>(R.id.edit_paralelo)
        val editDocDate = dialogView.findViewById<TextInputEditText>(R.id.edit_doc_date)

        editGrado.setAdapter(ArrayAdapter(requireContext(), android.R.layout.simple_dropdown_item_1line, AppUtils.grades))
        editParalelo.setAdapter(ArrayAdapter(requireContext(), android.R.layout.simple_dropdown_item_1line, AppUtils.parallels))

        val calendar = Calendar.getInstance()
        val sdf = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault())
        editDocDate.setText(sdf.format(calendar.time))

        ref.get().addOnSuccessListener { snapshot ->
            if (_binding == null) return@addOnSuccessListener
            val existingName = snapshot.child("studentName").value?.toString()
            if (!existingName.isNullOrEmpty()) {
                editName.setText(existingName)
                editGrado.setText(snapshot.child("grado").value?.toString() ?: "", false)
                editParalelo.setText(snapshot.child("paralelo").value?.toString() ?: "", false)
                editDocDate.setText(snapshot.child("docDate").value?.toString() ?: sdf.format(calendar.time))
            }
        }

        editDocDate.setOnClickListener {
            DatePickerDialog(requireContext(), { _, y, m, d ->
                calendar.set(Calendar.YEAR, y)
                calendar.set(Calendar.MONTH, m)
                calendar.set(Calendar.DAY_OF_MONTH, d)
                editDocDate.setText(sdf.format(calendar.time))
            }, calendar.get(Calendar.YEAR), calendar.get(Calendar.MONTH), calendar.get(Calendar.DAY_OF_MONTH)).show()
        }

        MaterialAlertDialogBuilder(requireContext())
            .setView(dialogView)
            .setCancelable(false)
            .setPositiveButton(R.string.save) { _, _ ->
                val name = AppUtils.sanitizeInput(editName.text.toString())
                val grado = editGrado.text.toString()
                val paralelo = editParalelo.text.toString()
                val docDate = editDocDate.text.toString()

                if (name.isEmpty() || grado.isEmpty() || paralelo.isEmpty() || docDate.isEmpty()) {
                    showToast("Completa los datos del estudiante derivado")
                    updatePage()
                    return@setPositiveButton
                }

                val formattedNum = String.format("%03d", number)
                val isPriority = isPriorityCase(categoryTitle)

                val data = mapOf<String, Any>(
                    "uid" to uid,
                    "color" to currentUserColor,
                    "idCasilla" to number,
                    "numeroCorrelativo" to formattedNum,
                    "tipoOrigen" to categoryTitle,
                    "studentName" to name,
                    "grado" to grado,
                    "paralelo" to paralelo,
                    "docDate" to docDate,
                    "vinculadoAExpediente" to false,
                    "timestamp" to ServerValue.TIMESTAMP
                )

                ref.updateChildren(data).addOnCompleteListener {
                    context?.let { DashboardWidget.notifyUpdate(it) }
                    sendCategoryNotification(number, categoryTitle)

                    if (isPriority) {
                        showPriorityDecisionDialog(number, categoryTitle, docDate, name, null, ref)
                    } else {
                        updatePage()
                    }
                }
            }
            .setNegativeButton(R.string.cancel) { _, _ -> updatePage() }
            .show()
    }

    private fun showCategoryDatabaseDialog() {
        if (institution.isEmpty()) return
        val safeInstKey = AppUtils.getSafeKey(institution)

        val dialogView = LayoutInflater.from(requireContext()).inflate(R.layout.dialog_category_db_list, null, false)
        val recycler = dialogView.findViewById<RecyclerView>(R.id.recycler_category_db)

        database.child("institutions").child(safeInstKey).child("categories").child(categoryTitle).get()
            .addOnSuccessListener { snapshot ->
                if (_binding == null) return@addOnSuccessListener
                val records = mutableListOf<Map<String, Any>>()
                for (child in snapshot.children) {
                    val num = child.key ?: ""
                    val data = child.value as? Map<String, Any> ?: continue
                    val mutableData = data.toMutableMap()
                    mutableData["number"] = num
                    records.add(mutableData)
                }

                if (records.isEmpty()) {
                    showToast("No hay registros en esta base de datos")
                    return@addOnSuccessListener
                }

                val adapter = CategoryDbAdapter(records)
                recycler.layoutManager = LinearLayoutManager(requireContext())
                recycler.adapter = adapter

                MaterialAlertDialogBuilder(requireContext())
                    .setTitle("Base de Datos: $categoryTitle")
                    .setView(dialogView)
                    .setPositiveButton(R.string.back, null)
                    .show()
            }
    }

    inner class CategoryDbAdapter(private val items: List<Map<String, Any>>) : RecyclerView.Adapter<CategoryDbAdapter.ViewHolder>() {
        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val b = ItemCategoryDbRecordBinding.inflate(LayoutInflater.from(parent.context), parent, false)
            return ViewHolder(b)
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            val item = items[position]
            val numStr = String.format("%03d", item["number"]?.toString()?.toIntOrNull() ?: 0)
            holder.b.textDbNumberTitle.text = "$categoryTitle #$numStr"
            holder.b.textDbDate.text = item["docDate"]?.toString() ?: ""

            val studentName = item["studentName"]?.toString()
            val grado = item["grado"]?.toString()
            val paralelo = item["paralelo"]?.toString()

            if (!studentName.isNullOrEmpty()) {
                holder.b.textDbStudentInfo.text = "$studentName ($grado \"$paralelo\")"
                holder.b.textDbStudentInfo.visibility = View.VISIBLE
            } else {
                holder.b.textDbStudentInfo.visibility = View.GONE
            }

            val citaDate = item["citaDate"]?.toString()
            val citaTime = item["citaTime"]?.toString()
            val citaNum = item["citaNumero"]?.toString()

            if (!citaDate.isNullOrEmpty()) {
                holder.b.textDbCitaInfo.text = "Cita #$citaNum: $citaDate $citaTime"
                holder.b.textDbCitaInfo.visibility = View.VISIBLE
            } else {
                holder.b.textDbCitaInfo.visibility = View.GONE
            }
        }

        override fun getItemCount() = items.size

        inner class ViewHolder(val b: ItemCategoryDbRecordBinding) : RecyclerView.ViewHolder(b.root)
    }

    private fun showDateSelectionDialog(number: Int, uid: String, ref: DatabaseReference) {
        val calendar = Calendar.getInstance()
        val sdf = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault())

        val dateSetListener = DatePickerDialog.OnDateSetListener { _, year, month, dayOfMonth ->
            calendar.set(Calendar.YEAR, year)
            calendar.set(Calendar.MONTH, month)
            calendar.set(Calendar.DAY_OF_MONTH, dayOfMonth)
            val selectedDate = sdf.format(calendar.time)

            val formattedNum = String.format("%03d", number)
            val isPriority = isPriorityCase(categoryTitle)

            val data = mapOf<String, Any>(
                "uid" to uid,
                "color" to currentUserColor,
                "idCasilla" to number,
                "numeroCorrelativo" to formattedNum,
                "tipoOrigen" to categoryTitle,
                "docDate" to selectedDate,
                "vinculadoAExpediente" to false,
                "timestamp" to ServerValue.TIMESTAMP
            )
            ref.setValue(data).addOnCompleteListener {
                context?.let { DashboardWidget.notifyUpdate(it) }
                sendCategoryNotification(number, categoryTitle)

                if (isPriority) {
                    showPriorityDecisionDialog(number, categoryTitle, selectedDate, null, null, ref)
                } else {
                    updatePage()
                }
            }
        }

        val datePickerDialog = DatePickerDialog(
            requireContext(),
            dateSetListener,
            calendar.get(Calendar.YEAR),
            calendar.get(Calendar.MONTH),
            calendar.get(Calendar.DAY_OF_MONTH)
        )
        datePickerDialog.setTitle("Fecha del Documento")
        datePickerDialog.setOnCancelListener { updatePage() }
        datePickerDialog.show()
    }

    private fun showInformeDetalleDialog(number: Int, uid: String, ref: DatabaseReference) {
        val dialogView = LayoutInflater.from(requireContext()).inflate(R.layout.dialog_informe_tecnico_details, null)

        val editTipo = dialogView.findViewById<AutoCompleteTextView>(R.id.edit_tipo_informe)
        val layoutOtros = dialogView.findViewById<TextInputLayout>(R.id.layout_tipo_otros)
        val editOtros = dialogView.findViewById<TextInputEditText>(R.id.edit_tipo_otros)

        val editAudiencia = dialogView.findViewById<AutoCompleteTextView>(R.id.edit_audiencia)
        val layoutCurso = dialogView.findViewById<TextInputLayout>(R.id.layout_curso_paralelo)
        val editCurso = dialogView.findViewById<AutoCompleteTextView>(R.id.edit_curso_paralelo)
        val editParticipantes = dialogView.findViewById<TextInputEditText>(R.id.edit_participantes)
        val editDocDate = dialogView.findViewById<TextInputEditText>(R.id.edit_doc_date)

        val calendar = Calendar.getInstance()
        val sdf = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault())
        editDocDate.setText(sdf.format(calendar.time))

        val dateSetListener = DatePickerDialog.OnDateSetListener { _, year, month, dayOfMonth ->
            calendar.set(Calendar.YEAR, year)
            calendar.set(Calendar.MONTH, month)
            calendar.set(Calendar.DAY_OF_MONTH, dayOfMonth)
            editDocDate.setText(sdf.format(calendar.time))
        }

        editDocDate.setOnClickListener {
            DatePickerDialog(
                requireContext(),
                dateSetListener,
                calendar.get(Calendar.YEAR),
                calendar.get(Calendar.MONTH),
                calendar.get(Calendar.DAY_OF_MONTH)
            ).show()
        }

        editTipo.setAdapter(ArrayAdapter(requireContext(), android.R.layout.simple_dropdown_item_1line, AppUtils.informeTipos))
        editAudiencia.setAdapter(ArrayAdapter(requireContext(), android.R.layout.simple_dropdown_item_1line, AppUtils.informeAudiencia))
        editCurso.setAdapter(ArrayAdapter(requireContext(), android.R.layout.simple_dropdown_item_1line, AppUtils.fullGradesList))

        editTipo.setOnItemClickListener { _, _, position, _ ->
            layoutOtros.visibility = if (AppUtils.informeTipos[position] == "Otros") View.VISIBLE else View.GONE
        }

        editAudiencia.setOnItemClickListener { _, _, position, _ ->
            layoutCurso.visibility = if (AppUtils.informeAudiencia[position] == "Estudiantes") View.VISIBLE else View.GONE
        }

        MaterialAlertDialogBuilder(requireContext())
            .setView(dialogView)
            .setCancelable(false)
            .setPositiveButton(R.string.save) { _, _ ->
                var finalTipo = editTipo.text.toString()
                if (finalTipo == "Otros") finalTipo = editOtros.text.toString()

                val audiencia = editAudiencia.text.toString()
                val curso = if (audiencia == "Estudiantes") editCurso.text.toString() else ""
                val participantes = editParticipantes.text.toString().toIntOrNull() ?: 0
                val docDate = editDocDate.text.toString()

                if (finalTipo.isEmpty() || audiencia.isEmpty() || docDate.isEmpty()) {
                    Toast.makeText(context, "Completa los campos obligatorios", Toast.LENGTH_SHORT).show()
                    updatePage()
                    return@setPositiveButton
                }

                val formattedNum = String.format("%03d", number)
                val isPriority = isPriorityCase(categoryTitle, finalTipo)

                val data = mutableMapOf<String, Any>(
                    "uid" to uid,
                    "color" to currentUserColor,
                    "idCasilla" to number,
                    "numeroCorrelativo" to formattedNum,
                    "tipoOrigen" to categoryTitle,
                    "tipoInforme" to finalTipo,
                    "audiencia" to audiencia,
                    "participantes" to participantes,
                    "docDate" to docDate,
                    "vinculadoAExpediente" to false,
                    "timestamp" to ServerValue.TIMESTAMP
                )
                if (curso.isNotEmpty()) data["cursoParalelo"] = curso

                ref.setValue(data).addOnCompleteListener {
                    context?.let { DashboardWidget.notifyUpdate(it) }
                    sendCategoryNotification(number, categoryTitle)

                    if (isPriority) {
                        showPriorityDecisionDialog(number, categoryTitle, docDate, null, finalTipo, ref)
                    } else {
                        updatePage()
                    }
                }
            }
            .setNegativeButton(R.string.cancel) { _, _ ->
                updatePage()
            }
            .show()
    }

    private fun sendCategoryNotification(number: Int, categoryTitle: String) {
        val user = auth.currentUser ?: return
        val safeInstKey = AppUtils.getSafeKey(institution)
        val formattedNum = String.format("%03d", number)
        val shortCat = when (categoryTitle) {
            "Oficios" -> "Oficios"
            "Informes Técnicos" -> "Info. Tec"
            "Reporte de Violencia" -> "Rep. H. V"
            "Derivación MSP" -> "Dv. MSP"
            "Derivación UDAI" -> "Dv. UDAI"
            else -> categoryTitle
        }
        val name = currentUserName.ifEmpty { "Un usuario" }
        val message = "$name acaba de ingresar el $shortCat $formattedNum"

        val notifData = mapOf(
            "createdBy" to user.uid,
            "userName" to name,
            "category" to categoryTitle,
            "number" to number,
            "casilla_id" to "$categoryTitle-$number",
            "marcada" to true,
            "message" to message,
            "timestamp" to ServerValue.TIMESTAMP
        )
        database.child("institutions").child(safeInstKey).child("notifications").push().setValue(notifData)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        detailListener?.let {
            val safeInstKey = AppUtils.getSafeKey(institution)
            database.child("institutions").child(safeInstKey).child("categories").child(categoryTitle).removeEventListener(it)
        }
        userListener?.let { database.child("users").child(auth.currentUser?.uid ?: "").removeEventListener(it) }
        _binding = null
    }
}
