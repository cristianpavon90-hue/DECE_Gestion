package com.example.decegestin

import android.app.DatePickerDialog
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.AutoCompleteTextView
import android.widget.Toast
import androidx.core.content.edit
import androidx.core.graphics.toColorInt
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import com.example.decegestin.databinding.FragmentSexualViolenceDetailBinding
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.textfield.TextInputEditText
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.*
import com.skydoves.balloon.*
import java.text.SimpleDateFormat
import java.util.*

class SexualViolenceDetailFragment : Fragment() {

    private var _binding: FragmentSexualViolenceDetailBinding? = null
    private val binding get() = _binding!!

    private val database = FirebaseDatabase.getInstance().reference
    private val auth = FirebaseAuth.getInstance()

    private var formId: String = ""
    private var studentName: String = ""
    private var grado: String = ""
    private var paralelo: String = ""
    private var currentInstKey: String = ""

    private val months = arrayOf(
        "Enero", "Febrero", "Marzo", "Abril", "Mayo", "Junio",
        "Julio", "Agosto", "Septiembre", "Octubre", "Noviembre", "Diciembre"
    )

    private var selectedMonthIndex: Int = Calendar.getInstance().get(Calendar.MONTH)

    private val followupsList = mutableListOf<Map<String, Any>>()
    private val comunidadList = mutableListOf<Map<String, Any>>()
    private val cursoList = mutableListOf<Map<String, Any>>()

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentSexualViolenceDetailBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.topBar.applyTopBarPadding()
        binding.backIcon.setOnClickListener { findNavController().navigateUp() }

        formId = arguments?.getString("formId") ?: ""
        studentName = arguments?.getString("studentName") ?: "Estudiante"
        grado = arguments?.getString("grado") ?: ""
        paralelo = arguments?.getString("paralelo") ?: ""

        binding.textStudentHeader.text = "$studentName $grado \"$paralelo\""

        setupMonthFilter()
        setupUserAvatar()
        loadData()

        binding.btnAddFollowup.setOnClickListener { showAddFollowupDialog() }
        binding.btnShare.setOnClickListener { shareCompiledInfo() }

        binding.btnTutorial.setOnClickListener { startTutorial() }
        checkFirstTimeTutorial()
    }

    private fun checkFirstTimeTutorial() {
        val uid = auth.currentUser?.uid ?: return
        val prefs = requireContext().getSharedPreferences("SexDetailTutorialPrefs_$uid", Context.MODE_PRIVATE)
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
            setText(getString(R.string.tut_sex_detail_step1))
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
            setArrowPosition(0.7f)
            setWidthRatio(0.75f)
            setHeight(BalloonSizeSpec.WRAP)
            setText(getString(R.string.tut_sex_detail_step2))
            setTextColorResource(R.color.white)
            setTextSize(14f)
            setBackgroundColorResource(R.color.md_theme_primary)
            setBalloonAnimation(BalloonAnimation.ELASTIC)
            setLifecycleOwner(viewLifecycleOwner)
            setDismissWhenClicked(true)
        }

        val b3 = createBalloon(requireContext()) {
            setArrowSize(10)
            setArrowOrientation(ArrowOrientation.BOTTOM)
            setArrowPosition(0.5f)
            setWidthRatio(0.8f)
            setHeight(BalloonSizeSpec.WRAP)
            setText(getString(R.string.tut_sex_detail_step3))
            setTextColorResource(R.color.white)
            setTextSize(14f)
            setBackgroundColorResource(R.color.md_theme_primary)
            setBalloonAnimation(BalloonAnimation.ELASTIC)
            setLifecycleOwner(viewLifecycleOwner)
            setDismissWhenClicked(true)
        }

        b1.showAlignBottom(binding.editMonthFilter)
        b1.setOnBalloonDismissListener {
            if (_binding != null) b2.showAlignBottom(binding.btnAddFollowup)
        }
        b2.setOnBalloonDismissListener {
            if (_binding != null) b3.showAlignTop(binding.btnShare)
        }
    }

    private fun setupMonthFilter() {
        val adapter = ArrayAdapter(requireContext(), android.R.layout.simple_dropdown_item_1line, months)
        binding.editMonthFilter.setAdapter(adapter)
        binding.editMonthFilter.setText(months[selectedMonthIndex], false)

        binding.editMonthFilter.setOnItemClickListener { _, _, position, _ ->
            selectedMonthIndex = position
            renderCompiledViews()
        }
    }

    private fun setupUserAvatar() {
        val user = auth.currentUser ?: return
        database.child("users").child(user.uid).get().addOnSuccessListener { snapshot ->
            if (_binding == null) return@addOnSuccessListener
            binding.avatarText.text = snapshot.child("initials").value?.toString() ?: "U"
            val colorHex = snapshot.child("profileColor").value?.toString() ?: "#0097A7"
            try {
                binding.avatarCard.setCardBackgroundColor(colorHex.toColorInt())
            } catch (_: Exception) {}
        }
    }

    private fun loadData() {
        val user = auth.currentUser ?: return
        database.child("users").child(user.uid).child("institution").get().addOnSuccessListener { snapshot ->
            if (_binding == null) return@addOnSuccessListener
            val inst = snapshot.value?.toString() ?: return@addOnSuccessListener
            currentInstKey = AppUtils.getSafeKey(inst)

            listenToFollowups()
            listenToInformesTecnicos()
        }
    }

    private fun listenToFollowups() {
        if (formId.isEmpty() || currentInstKey.isEmpty()) return

        database.child("institutions").child(currentInstKey)
            .child("forms").child(formId).child("sexualViolenceFollowups")
            .addValueEventListener(object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    if (_binding == null) return
                    followupsList.clear()
                    for (child in snapshot.children) {
                        val data = child.value as? Map<String, Any> ?: continue
                        followupsList.add(data)
                    }
                    renderCompiledViews()
                }

                override fun onCancelled(error: DatabaseError) {}
            })
    }

    private fun listenToInformesTecnicos() {
        if (currentInstKey.isEmpty()) return

        database.child("institutions").child(currentInstKey)
            .child("categories").child("Informes Técnicos")
            .addValueEventListener(object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    if (_binding == null) return
                    comunidadList.clear()
                    cursoList.clear()

                    val targetCourseStr = "$grado \"$paralelo\""

                    for (child in snapshot.children) {
                        val data = child.value as? Map<String, Any> ?: continue
                        val tipo = data["tipoInforme"]?.toString() ?: ""

                        // Filtrar por temáticas relacionadas a Violencia Sexual
                        if (isRelatedToSexualViolence(tipo)) {
                            val cursoParalelo = data["cursoParalelo"]?.toString() ?: ""
                            val audiencia = data["audiencia"]?.toString() ?: ""

                            if (cursoParalelo.equals(targetCourseStr, ignoreCase = true)) {
                                cursoList.add(data)
                            } else {
                                comunidadList.add(data)
                            }
                        }
                    }
                    renderCompiledViews()
                }

                override fun onCancelled(error: DatabaseError) {}
            })
    }

    private fun isRelatedToSexualViolence(tipo: String): Boolean {
        val lower = tipo.lowercase()
        return lower.contains("violencia") || lower.contains("sexual") || lower.contains("recorrido participativo")
    }

    private fun renderCompiledViews() {
        if (_binding == null) return

        // 1. Render Seguimientos
        val filteredFollowups = followupsList.filter { isDateInSelectedMonth(it["date"]?.toString()) }
        if (filteredFollowups.isEmpty()) {
            binding.textSeguimientosList.text = "No hay seguimientos en ${months[selectedMonthIndex]}"
        } else {
            val sb = StringBuilder()
            filteredFollowups.forEach { f ->
                val tipo = f["tipo"]?.toString() ?: "Atención"
                val date = f["date"]?.toString() ?: ""
                sb.append("● $tipo: $date\n")
            }
            binding.textSeguimientosList.text = sb.toString().trim()
        }

        // 2. Render Comunidad
        val filteredComunidad = comunidadList.filter { isDateInSelectedMonth(it["docDate"]?.toString()) }
        if (filteredComunidad.isEmpty()) {
            binding.textComunidadList.text = "No hay actividades comunitarias en ${months[selectedMonthIndex]}"
        } else {
            val sb = StringBuilder()
            filteredComunidad.forEach { c ->
                val date = c["docDate"]?.toString() ?: ""
                val curso = c["cursoParalelo"]?.toString() ?: "General"
                val tema = c["tipoInforme"]?.toString() ?: ""
                val asist = c["participantes"]?.toString() ?: "0"
                sb.append("● Fecha: $date Curso: $curso\nTema: $tema. Asistentes: $asist\n\n")
            }
            binding.textComunidadList.text = sb.toString().trim()
        }

        // 3. Render Individuales / Curso
        val filteredCurso = cursoList.filter { isDateInSelectedMonth(it["docDate"]?.toString()) }
        if (filteredCurso.isEmpty()) {
            binding.textIndividualesList.text = "No hay actividades del curso en ${months[selectedMonthIndex]}"
        } else {
            val sb = StringBuilder()
            filteredCurso.forEach { c ->
                val date = c["docDate"]?.toString() ?: ""
                val curso = c["cursoParalelo"]?.toString() ?: "$grado \"$paralelo\""
                val tema = c["tipoInforme"]?.toString() ?: ""
                val asist = c["participantes"]?.toString() ?: "0"
                sb.append("● Fecha: $date Curso: $curso\nTema: $tema. Asistentes: $asist\n\n")
            }
            binding.textIndividualesList.text = sb.toString().trim()
        }
    }

    private fun isDateInSelectedMonth(dateStr: String?): Boolean {
        if (dateStr.isNullOrEmpty()) return false
        return try {
            val sdf = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault())
            val date = sdf.parse(dateStr) ?: return false
            val cal = Calendar.getInstance().apply { time = date }
            cal.get(Calendar.MONTH) == selectedMonthIndex
        } catch (e: Exception) {
            false
        }
    }

    private fun showAddFollowupDialog() {
        val dialogView = LayoutInflater.from(requireContext()).inflate(R.layout.dialog_add_sexual_violence_followup, null)

        val editTipo = dialogView.findViewById<AutoCompleteTextView>(R.id.edit_tipo_atencion)
        val editDate = dialogView.findViewById<TextInputEditText>(R.id.edit_followup_date)
        val editDesc = dialogView.findViewById<TextInputEditText>(R.id.edit_followup_desc)

        editTipo.setAdapter(ArrayAdapter(requireContext(), android.R.layout.simple_dropdown_item_1line, AppUtils.atencionTipos))
        editTipo.setText(AppUtils.atencionTipos[0], false)

        val calendar = Calendar.getInstance()
        val sdf = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault())
        editDate.setText(sdf.format(calendar.time))

        editDate.setOnClickListener {
            DatePickerDialog(requireContext(), { _, y, m, d ->
                calendar.set(Calendar.YEAR, y)
                calendar.set(Calendar.MONTH, m)
                calendar.set(Calendar.DAY_OF_MONTH, d)
                editDate.setText(sdf.format(calendar.time))
            }, calendar.get(Calendar.YEAR), calendar.get(Calendar.MONTH), calendar.get(Calendar.DAY_OF_MONTH)).show()
        }

        MaterialAlertDialogBuilder(requireContext())
            .setView(dialogView)
            .setPositiveButton(R.string.save) { _, _ ->
                val tipo = editTipo.text.toString()
                val date = editDate.text.toString()
                val desc = AppUtils.sanitizeInput(editDesc.text.toString())

                if (tipo.isEmpty() || date.isEmpty()) {
                    Toast.makeText(context, "Completa los campos obligatorios", Toast.LENGTH_SHORT).show()
                    return@setPositiveButton
                }

                val followupData = mapOf(
                    "tipo" to tipo,
                    "date" to date,
                    "description" to desc,
                    "timestamp" to ServerValue.TIMESTAMP,
                    "createdBy" to (auth.currentUser?.uid ?: "")
                )

                database.child("institutions").child(currentInstKey)
                    .child("forms").child(formId).child("sexualViolenceFollowups")
                    .push().setValue(followupData)
                    .addOnSuccessListener {
                        Toast.makeText(context, "Seguimiento agregado", Toast.LENGTH_SHORT).show()
                    }
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private fun shareCompiledInfo() {
        val monthName = months[selectedMonthIndex]
        val sb = StringBuilder()

        sb.append("CONGLOMERADO DE ACCIONES - VIOLENCIA SEXUAL\n")
        sb.append("Estudiante: $studentName\n")
        sb.append("Curso: $grado \"$paralelo\"\n")
        sb.append("Mes: $monthName\n\n")

        sb.append("--- SEGUIMIENTOS ---\n")
        val filteredFollowups = followupsList.filter { isDateInSelectedMonth(it["date"]?.toString()) }
        if (filteredFollowups.isEmpty()) {
            sb.append("Sin registros\n")
        } else {
            filteredFollowups.forEach { f ->
                val tipo = f["tipo"]?.toString() ?: ""
                val date = f["date"]?.toString() ?: ""
                val desc = f["description"]?.toString() ?: ""
                sb.append("● $tipo: $date\n")
                if (desc.isNotEmpty()) sb.append("  Detalle: $desc\n")
            }
        }

        sb.append("\n--- ACTIVIDADES EN TORNO A LA COMUNIDAD ---\n")
        val filteredComunidad = comunidadList.filter { isDateInSelectedMonth(it["docDate"]?.toString()) }
        if (filteredComunidad.isEmpty()) {
            sb.append("Sin registros\n")
        } else {
            filteredComunidad.forEach { c ->
                val date = c["docDate"]?.toString() ?: ""
                val curso = c["cursoParalelo"]?.toString() ?: ""
                val tema = c["tipoInforme"]?.toString() ?: ""
                val asist = c["participantes"]?.toString() ?: "0"
                sb.append("● Fecha: $date Curso: $curso\n  Tema: $tema. Asistentes: $asist\n")
            }
        }

        sb.append("\n--- ACTIVIDADES INDIVIDUALES / CURSO ---\n")
        val filteredCurso = cursoList.filter { isDateInSelectedMonth(it["docDate"]?.toString()) }
        if (filteredCurso.isEmpty()) {
            sb.append("Sin registros\n")
        } else {
            filteredCurso.forEach { c ->
                val date = c["docDate"]?.toString() ?: ""
                val curso = c["cursoParalelo"]?.toString() ?: ""
                val tema = c["tipoInforme"]?.toString() ?: ""
                val asist = c["participantes"]?.toString() ?: "0"
                sb.append("● Fecha: $date Curso: $curso\n  Tema: $tema. Asistentes: $asist\n")
            }
        }

        val shareIntent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_SUBJECT, "Informe Violencia Sexual - $studentName")
            putExtra(Intent.EXTRA_TEXT, sb.toString())
        }

        startActivity(Intent.createChooser(shareIntent, "Compartir acciones realizadas"))
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
