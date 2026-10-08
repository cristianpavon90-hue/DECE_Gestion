package com.example.decegestin

import android.content.Intent
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.AutoCompleteTextView
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.decegestin.databinding.FragmentInstitutionalDistributionBinding
import com.example.decegestin.databinding.ItemCourseDistributionCardBinding
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.*

/**
 * Pantalla de Distribución Institucional de Cursos por Profesional DECE.
 */
class InstitutionalDistributionFragment : Fragment() {

    private var _binding: FragmentInstitutionalDistributionBinding? = null
    private val binding get() = _binding!!

    private val database = FirebaseDatabase.getInstance().reference
    private val auth = FirebaseAuth.getInstance()

    private var currentInstKey: String = ""
    private var currentInstName: String = ""
    private var canManageDistribution: Boolean = false

    private val professionalsList = mutableListOf<String>()
    private val profNameToUid = mutableMapOf<String, String>()

    private val subnivelesOptions = arrayOf(
        "Todos los subniveles",
        "Inicial",
        "Preparatoria",
        "Elemental",
        "Media",
        "Superior",
        "Bachillerato"
    )

    private val allDistribution = mutableListOf<Map<String, Any>>()
    private val filteredDistribution = mutableListOf<Map<String, Any>>()

    private lateinit var adapter: DistributionAdapter
    private var selectedProfFilter = "Todos los profesionales"
    private var selectedSubnivelFilter = "Todos los subniveles"

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentInstitutionalDistributionBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        binding.topBar.applyTopBarPadding()

        binding.backIcon.setOnClickListener { findNavController().navigateUp() }

        setupRecyclerView()
        setupUserAvatarAndPermissions()

        binding.avatarCard.setOnClickListener { startTutorial() }
    }

    private fun startTutorial() {
        val b1 = com.skydoves.balloon.createBalloon(requireContext()) {
            setArrowSize(10)
            setArrowOrientation(com.skydoves.balloon.ArrowOrientation.TOP)
            setArrowPosition(0.5f)
            setWidthRatio(0.85f)
            setHeight(com.skydoves.balloon.BalloonSizeSpec.WRAP)
            setText("Filtra por profesional o subnivel. Toca cualquier tarjeta para llamar o enviar mensaje al docente tutor.")
            setTextColorResource(R.color.white)
            setTextSize(14f)
            setBackgroundColorResource(R.color.md_theme_primary)
            setBalloonAnimation(com.skydoves.balloon.BalloonAnimation.ELASTIC)
            setLifecycleOwner(viewLifecycleOwner)
            setDismissWhenClicked(true)
        }
        b1.showAlignBottom(binding.layoutFilterProfesional)
    }

    private fun setupRecyclerView() {
        adapter = DistributionAdapter()
        binding.recyclerCourses.layoutManager = LinearLayoutManager(requireContext())
        binding.recyclerCourses.adapter = adapter
    }

    private fun setupUserAvatarAndPermissions() {
        val user = auth.currentUser ?: return
        database.child("users").child(user.uid).get().addOnSuccessListener { snapshot ->
            if (_binding == null || !snapshot.exists()) return@addOnSuccessListener

            binding.avatarText.text = snapshot.child("initials").value?.toString() ?: "U"
            val colorHex = snapshot.child("profileColor").value?.toString() ?: "#0097A7"
            try {
                binding.avatarCard.setCardBackgroundColor(Color.parseColor(colorHex))
            } catch (_: Exception) {}

            currentInstName = snapshot.child("institution").value?.toString() ?: ""
            currentInstKey = AppUtils.getSafeKey(currentInstName)

            val realRole = snapshot.child("role").value?.toString() ?: ""
            val activeRole = AppUtils.getActiveRole(requireContext(), realRole)

            canManageDistribution = activeRole == "admin" || activeRole == "distrital" || activeRole == "institucional"
            binding.fabAddDistribution.visibility = if (canManageDistribution) View.VISIBLE else View.GONE

            loadProfessionalsAndSetupFilters()
            loadDistributionData()
        }
    }

    private fun loadProfessionalsAndSetupFilters() {
        database.child("users").get().addOnSuccessListener { snapshot ->
            if (_binding == null) return@addOnSuccessListener

            professionalsList.clear()
            profNameToUid.clear()

            professionalsList.add("Todos los profesionales")

            for (child in snapshot.children) {
                val inst = child.child("institution").value?.toString() ?: ""
                val isApproved = child.child("isApproved").value as? Boolean ?: false
                if (AppUtils.getSafeKey(inst) == currentInstKey && isApproved) {
                    val name = child.child("fullName").value?.toString() ?: "Sin nombre"
                    val uid = child.key ?: ""
                    professionalsList.add(name)
                    profNameToUid[name] = uid
                }
            }

            // Filter 1: Professionals
            val profAdapter = ArrayAdapter(requireContext(), android.R.layout.simple_dropdown_item_1line, professionalsList)
            binding.editFilterProfesional.setAdapter(profAdapter)
            if (professionalsList.size > 1 && selectedProfFilter == "Todos los profesionales") {
                selectedProfFilter = professionalsList[1]
            }
            binding.editFilterProfesional.setText(selectedProfFilter, false)
            binding.editFilterProfesional.setOnClickListener { binding.editFilterProfesional.showDropDown() }

            binding.editFilterProfesional.setOnItemClickListener { _, _, position, _ ->
                selectedProfFilter = professionalsList[position]
                applyFilters()
            }

            // Filter 2: Sublevels
            val subAdapter = ArrayAdapter(requireContext(), android.R.layout.simple_dropdown_item_1line, subnivelesOptions)
            binding.editFilterSubnivel.setAdapter(subAdapter)
            binding.editFilterSubnivel.setText(selectedSubnivelFilter, false)
            binding.editFilterSubnivel.setOnClickListener { binding.editFilterSubnivel.showDropDown() }

            binding.editFilterSubnivel.setOnItemClickListener { _, _, position, _ ->
                selectedSubnivelFilter = subnivelesOptions[position]
                applyFilters()
            }

            binding.fabAddDistribution.setOnClickListener { showAddEditDistributionDialog(null) }
        }
    }

    private fun loadDistributionData() {
        if (currentInstKey.isEmpty()) return
        binding.progressCourses.visibility = View.VISIBLE

        database.child("institutions").child(currentInstKey).child("distribution")
            .addValueEventListener(object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    if (_binding == null) return
                    binding.progressCourses.visibility = View.GONE
                    allDistribution.clear()

                    for (child in snapshot.children) {
                        val map = child.value as? Map<String, Any> ?: continue
                        val mutable = map.toMutableMap()
                        mutable["distId"] = child.key ?: ""
                        allDistribution.add(mutable)
                    }

                    applyFilters()
                }

                override fun onCancelled(error: DatabaseError) {
                    if (_binding == null) return
                    binding.progressCourses.visibility = View.GONE
                }
            })
    }

    private fun applyFilters() {
        filteredDistribution.clear()

        for (item in allDistribution) {
            val profName = item["profesionalName"]?.toString() ?: ""
            val curso = item["curso"]?.toString() ?: ""
            val subnivel = item["subnivel"]?.toString() ?: AppUtils.getSubnivelForCurso(curso)

            val matchProf = selectedProfFilter == "Todos los profesionales" || profName.equals(selectedProfFilter, ignoreCase = true)
            val matchSub = selectedSubnivelFilter == "Todos los subniveles" || subnivel.equals(selectedSubnivelFilter, ignoreCase = true)

            if (matchProf && matchSub) {
                filteredDistribution.add(item)
            }
        }

        binding.textEmptyCourses.visibility = if (filteredDistribution.isEmpty()) View.VISIBLE else View.GONE
        adapter.notifyDataSetChanged()
    }

    private fun showCourseActionMenu(item: Map<String, Any>) {
        val course = item["curso"]?.toString() ?: ""
        val paralelo = item["paralelo"]?.toString() ?: ""
        val teacher = item["docente"]?.toString() ?: "Docente"
        val phone = item["phone"]?.toString() ?: ""

        val optionsList = mutableListOf(
            "Llamar al Docente ($phone)",
            "Enviar mensaje por WhatsApp / SMS ($phone)"
        )

        if (canManageDistribution) {
            optionsList.add("Editar Asignación de Curso")
            optionsList.add("Eliminar Asignación")
        }

        MaterialAlertDialogBuilder(requireContext())
            .setTitle("$course \"$paralelo\" - $teacher")
            .setItems(optionsList.toTypedArray()) { _, which ->
                when (which) {
                    0 -> makePhoneCall(phone)
                    1 -> sendWhatsAppOrSms(phone, teacher, course)
                    2 -> if (canManageDistribution) showAddEditDistributionDialog(item)
                    3 -> if (canManageDistribution) deleteDistribution(item["distId"]?.toString() ?: "")
                }
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private fun makePhoneCall(phone: String) {
        if (phone.isEmpty()) {
            Toast.makeText(context, "Número telefónico no registrado", Toast.LENGTH_SHORT).show()
            return
        }
        val cleanPhone = phone.replace("[^0-9+]".toRegex(), "")
        try {
            val intent = Intent(Intent.ACTION_DIAL, Uri.parse("tel:$cleanPhone"))
            startActivity(intent)
        } catch (_: Exception) {
            Toast.makeText(context, "No se puede realizar la llamada", Toast.LENGTH_SHORT).show()
        }
    }

    private fun sendWhatsAppOrSms(phone: String, teacher: String, course: String) {
        if (phone.isEmpty()) {
            Toast.makeText(context, "Número telefónico no registrado", Toast.LENGTH_SHORT).show()
            return
        }
        var cleanPhone = phone.replace("[^0-9]".toRegex(), "")
        if (cleanPhone.startsWith("0")) cleanPhone = "593" + cleanPhone.substring(1)
        if (!cleanPhone.startsWith("593")) cleanPhone = "593$cleanPhone"

        val msg = "Estimado/a docente $teacher de $course, saludos cordiales desde la Coordinación DECE."

        val options = arrayOf("WhatsApp", "Mensaje SMS")
        MaterialAlertDialogBuilder(requireContext())
            .setTitle("Enviar mensaje a $teacher")
            .setItems(options) { _, which ->
                if (which == 0) {
                    try {
                        val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://api.whatsapp.com/send?phone=$cleanPhone&text=${Uri.encode(msg)}"))
                        startActivity(intent)
                    } catch (_: Exception) {
                        Toast.makeText(context, "No se pudo abrir WhatsApp", Toast.LENGTH_SHORT).show()
                    }
                } else {
                    try {
                        val intent = Intent(Intent.ACTION_SENDTO, Uri.parse("smsto:$phone")).apply {
                            putExtra("sms_body", msg)
                        }
                        startActivity(intent)
                    } catch (_: Exception) {
                        Toast.makeText(context, "No se pudo enviar el SMS", Toast.LENGTH_SHORT).show()
                    }
                }
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private fun showAddEditDistributionDialog(editItem: Map<String, Any>?) {
        val dialogView = LayoutInflater.from(requireContext()).inflate(R.layout.dialog_add_edit_distribution, null)

        val editCurso = dialogView.findViewById<AutoCompleteTextView>(R.id.edit_dist_curso)
        val editParalelo = dialogView.findViewById<AutoCompleteTextView>(R.id.edit_dist_paralelo)
        val editDocente = dialogView.findViewById<AutoCompleteTextView>(R.id.edit_dist_docente)
        val editPhone = dialogView.findViewById<com.google.android.material.textfield.TextInputEditText>(R.id.edit_dist_phone)
        val editProfesional = dialogView.findViewById<AutoCompleteTextView>(R.id.edit_dist_profesional)
        val btnSave = dialogView.findViewById<com.google.android.material.button.MaterialButton>(R.id.btn_save_distribution)

        // Cursos
        editCurso.setAdapter(ArrayAdapter(requireContext(), android.R.layout.simple_dropdown_item_1line, AppUtils.fullGradesList))
        editCurso.setOnClickListener { editCurso.showDropDown() }

        // Paralelos
        editParalelo.setAdapter(ArrayAdapter(requireContext(), android.R.layout.simple_dropdown_item_1line, AppUtils.parallels))
        editParalelo.setOnClickListener { editParalelo.showDropDown() }

        // Profesionales
        val profNames = professionalsList.filter { it != "Todos los profesionales" }
        editProfesional.setAdapter(ArrayAdapter(requireContext(), android.R.layout.simple_dropdown_item_1line, profNames))
        editProfesional.setOnClickListener { editProfesional.showDropDown() }

        // Docentes Catalog
        val docentesList = mutableListOf<String>()
        val docentesPhoneMap = mutableMapOf<String, String>()

        database.child("institutions").child(currentInstKey).child("docentes").get().addOnSuccessListener { snapshot ->
            for (child in snapshot.children) {
                val name = child.child("name").value?.toString() ?: child.key ?: ""
                val phone = child.child("phone").value?.toString() ?: ""
                if (name.isNotEmpty()) {
                    docentesList.add(name)
                    if (phone.isNotEmpty()) docentesPhoneMap[name] = phone
                }
            }
            editDocente.setAdapter(ArrayAdapter(requireContext(), android.R.layout.simple_dropdown_item_1line, docentesList))
        }

        editDocente.setOnItemClickListener { _, _, position, _ ->
            val name = editDocente.text.toString()
            docentesPhoneMap[name]?.let { editPhone.setText(it) }
        }

        // Prefill if editing
        if (editItem != null) {
            editCurso.setText(editItem["curso"]?.toString() ?: "", false)
            editParalelo.setText(editItem["paralelo"]?.toString() ?: "", false)
            editDocente.setText(editItem["docente"]?.toString() ?: "", false)
            editPhone.setText(editItem["phone"]?.toString() ?: "")
            editProfesional.setText(editItem["profesionalName"]?.toString() ?: "", false)
        } else {
            if (selectedProfFilter != "Todos los profesionales") {
                editProfesional.setText(selectedProfFilter, false)
            }
        }

        var dialog: androidx.appcompat.app.AlertDialog? = null

        btnSave.setOnClickListener {
            val curso = editCurso.text.toString().trim()
            val paralelo = editParalelo.text.toString().trim()
            val docente = AppUtils.sanitizeInput(editDocente.text.toString())
            val phone = editPhone.text.toString().trim()
            val profName = editProfesional.text.toString().trim()

            if (curso.isEmpty() || paralelo.isEmpty() || docente.isEmpty() || profName.isEmpty()) {
                Toast.makeText(context, "Completa todos los campos obligatorios", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            val subnivel = AppUtils.getSubnivelForCurso(curso)
            val profUid = profNameToUid[profName] ?: ""
            val distId = editItem?.get("distId")?.toString() ?: database.child("institutions").child(currentInstKey).child("distribution").push().key ?: ""

            val data = mapOf<String, Any>(
                "distributionId" to distId,
                "profesionalUid" to profUid,
                "profesionalName" to profName,
                "curso" to curso,
                "paralelo" to paralelo,
                "subnivel" to subnivel,
                "docente" to docente,
                "phone" to phone,
                "timestamp" to ServerValue.TIMESTAMP
            )

            // Guardar en distribution
            database.child("institutions").child(currentInstKey).child("distribution").child(distId).setValue(data).addOnSuccessListener {
                // Guardar/Actualizar catálogo de docentes
                val teacherKey = AppUtils.getSafeKey(docente)
                val teacherData = mapOf("name" to docente, "phone" to phone)
                database.child("institutions").child(currentInstKey).child("docentes").child(teacherKey).setValue(teacherData)

                Toast.makeText(context, "Asignación guardada exitosamente", Toast.LENGTH_SHORT).show()
                dialog?.dismiss()
            }
        }

        dialog = MaterialAlertDialogBuilder(requireContext())
            .setView(dialogView)
            .setNegativeButton(R.string.cancel, null)
            .create()

        dialog.show()
    }

    private fun deleteDistribution(distId: String) {
        if (distId.isEmpty()) return
        database.child("institutions").child(currentInstKey).child("distribution").child(distId).removeValue().addOnSuccessListener {
            Toast.makeText(context, "Asignación eliminada exitosamente", Toast.LENGTH_SHORT).show()
        }
    }

    inner class DistributionAdapter : RecyclerView.Adapter<DistributionAdapter.ViewHolder>() {
        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val b = ItemCourseDistributionCardBinding.inflate(LayoutInflater.from(parent.context), parent, false)
            return ViewHolder(b)
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            val item = filteredDistribution[position]
            val curso = item["curso"]?.toString() ?: ""
            val paralelo = item["paralelo"]?.toString() ?: ""
            val docente = item["docente"]?.toString() ?: "Docente sin asignar"
            val phone = item["phone"]?.toString() ?: ""

            holder.b.textCourseName.text = "$curso \"$paralelo\""
            holder.b.textTeacherName.text = "Docente: $docente"
            holder.b.textTeacherPhone.text = if (phone.isNotEmpty()) "Telf: $phone" else "Telf: Sin teléfono"

            holder.b.root.setOnClickListener { showCourseActionMenu(item) }
        }

        override fun getItemCount() = filteredDistribution.size

        inner class ViewHolder(val b: ItemCourseDistributionCardBinding) : RecyclerView.ViewHolder(b.root)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
