package com.example.decegestin

import android.app.AlarmManager
import android.app.DatePickerDialog
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.InputMethodManager
import android.widget.ArrayAdapter
import android.widget.AutoCompleteTextView
import android.widget.CheckBox
import android.widget.EditText
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import com.example.decegestin.databinding.FragmentNewFormBinding
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.*
import java.text.SimpleDateFormat
import java.util.*

/**
 * Fragmento para el registro de nuevas atenciones (Nuevo Formulario).
 */
class NewFormFragment : Fragment() {

    private var _binding: FragmentNewFormBinding? = null
    private val binding get() = _binding!!

    private val auth by lazy { FirebaseAuth.getInstance() }
    private val database by lazy { FirebaseDatabase.getInstance().reference }
    private val calendar = Calendar.getInstance()

    private val derivadoresList = mutableListOf<String>()
    private lateinit var derivadoresAdapter: ArrayAdapter<String>
    private var currentInstitutionKey: String? = null
    private var derivadoresListener: ValueEventListener? = null
    
    private var editFormId: String? = null

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentNewFormBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupUI()
        checkEditMode()
    }

    private fun checkEditMode() {
        arguments?.let { bundle ->
            editFormId = bundle.getString("formId")
            if (editFormId != null) {
                // Estamos en modo edición
                binding.buttonSaveForm.text = "Actualizar Formulario"
                
                binding.editStudentName.setText(bundle.getString("studentName"))
                binding.editGrado.setText(bundle.getString("grado"), false)
                binding.editParalelo.setText(bundle.getString("paralelo"), false)
                binding.editDate.setText(bundle.getString("date"))
                binding.editObservations.setText(bundle.getString("observations"))
                
                val caseType = bundle.getString("caseType") ?: ""
                if (AppUtils.caseTypes.contains(caseType)) {
                    binding.editCaseType.setText(caseType, false)
                } else {
                    binding.editCaseType.setText("Otros", false)
                    binding.layoutCustomCaseType.visibility = View.VISIBLE
                    binding.editCustomCaseType.setText(caseType)
                }

                val derivedBy = bundle.getString("derivedBy") ?: ""
                binding.editDerivedBy.setText(derivedBy, false)
                
                val selectedActions = bundle.getStringArrayList("selectedActions")
                selectedActions?.forEach { actionName ->
                    for (i in 0 until binding.checkboxContainer.childCount) {
                        val child = binding.checkboxContainer.getChildAt(i)
                        if (child is CheckBox && child.text == actionName) {
                            child.isChecked = true
                        }
                    }
                }

                if (caseType == "Violencia sexual") {
                    binding.layoutSexualViolenceExtras.visibility = View.VISIBLE
                    binding.editRepName.setText(bundle.getString("repName"))
                    binding.editRepPhone.setText(bundle.getString("repPhone"))
                    binding.editReminderDay.setText(bundle.getString("reminderDay"), false)
                    binding.editReminderFreq.setText(bundle.getString("reminderFreq"), false)
                }
            }
        }
    }

    private fun setupUI() {
        binding.backIcon.setOnClickListener { findNavController().navigateUp() }

        // 1. Grados
        val gradesAdapter = ArrayAdapter(requireContext(), android.R.layout.simple_dropdown_item_1line, AppUtils.grades)
        setupExposedDropdown(binding.editGrado, gradesAdapter)

        // 2. Paralelos
        val parallelAdapter = ArrayAdapter(requireContext(), android.R.layout.simple_dropdown_item_1line, AppUtils.parallels)
        setupExposedDropdown(binding.editParalelo, parallelAdapter)

        // 3. Tipos de caso
        val caseAdapter = ArrayAdapter(requireContext(), android.R.layout.simple_dropdown_item_1line, AppUtils.caseTypes)
        setupExposedDropdown(binding.editCaseType, caseAdapter)
        binding.editCaseType.setOnItemClickListener { _, _, position, _ ->
            val selected = caseAdapter.getItem(position)
            
            // Lógica de visibilidad para Violencia Sexual
            binding.layoutSexualViolenceExtras.visibility = if (selected == "Violencia sexual") View.VISIBLE else View.GONE

            if (selected == "Otros") {
                binding.layoutCustomCaseType.visibility = View.VISIBLE
                binding.editCustomCaseType.requestFocus()
                showKeyboard(binding.editCustomCaseType)
            } else {
                binding.layoutCustomCaseType.visibility = View.GONE
                binding.editCustomCaseType.text?.clear()
            }
        }

        // 4. Quien deriva
        setupDerivadores()

        // 5. Configurar dropdowns de Recordatorio (Seguimiento Especial)
        val dayAdapter = ArrayAdapter(requireContext(), android.R.layout.simple_dropdown_item_1line, AppUtils.daysOfWeek)
        setupExposedDropdown(binding.editReminderDay, dayAdapter)

        val freqAdapter = ArrayAdapter(requireContext(), android.R.layout.simple_dropdown_item_1line, AppUtils.frequencies)
        setupExposedDropdown(binding.editReminderFreq, freqAdapter)

        // 6. Fecha
        val dateSetListener = DatePickerDialog.OnDateSetListener { _, year, month, dayOfMonth ->
            calendar.set(Calendar.YEAR, year)
            calendar.set(Calendar.MONTH, month)
            calendar.set(Calendar.DAY_OF_MONTH, dayOfMonth)
            updateDateLabel()
        }
        binding.editDate.setOnClickListener {
            DatePickerDialog(requireContext(), dateSetListener, calendar.get(Calendar.YEAR), calendar.get(Calendar.MONTH), calendar.get(Calendar.DAY_OF_MONTH)).show()
        }
        updateDateLabel()

        binding.buttonSaveForm.setOnClickListener { saveForm() }
    }

    private fun setupExposedDropdown(view: AutoCompleteTextView, adapter: ArrayAdapter<String>) {
        view.setAdapter(adapter)
        view.setOnClickListener { view.showDropDown() }
        view.setOnTouchListener { _, event ->
            if (event.action == MotionEvent.ACTION_UP) {
                view.showDropDown()
            }
            false
        }
    }

    private fun showKeyboard(view: View) {
        val imm = requireContext().getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        imm.showSoftInput(view, InputMethodManager.SHOW_IMPLICIT)
    }

    private fun updateDateLabel() {
        val sdf = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault())
        binding.editDate.setText(sdf.format(calendar.time))
    }

    private fun setupDerivadores() {
        derivadoresList.clear()
        derivadoresList.addAll(AppUtils.defaultDerivadores)
        
        derivadoresAdapter = ArrayAdapter(requireContext(), android.R.layout.simple_dropdown_item_1line, derivadoresList)
        setupExposedDropdown(binding.editDerivedBy, derivadoresAdapter)

        binding.editDerivedBy.setOnItemClickListener { _, _, position, _ ->
            val selected = derivadoresAdapter.getItem(position)
            if (selected == "Otros") {
                binding.layoutCustomDerivedBy.visibility = View.VISIBLE
                binding.editCustomDerivedBy.requestFocus()
                showKeyboard(binding.editCustomDerivedBy)
            } else {
                binding.layoutCustomDerivedBy.visibility = View.GONE
                binding.editCustomDerivedBy.text?.clear()
            }
        }

        val handler = Handler(Looper.getMainLooper())
        val longPressRunnable = Runnable { showDerivadoresManagementDialog() }

        binding.editDerivedBy.setOnTouchListener { v, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> handler.postDelayed(longPressRunnable, 5000)
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> handler.removeCallbacks(longPressRunnable)
            }
            if (event.action == MotionEvent.ACTION_UP) {
                binding.editDerivedBy.showDropDown()
            }
            false 
        }

        val user = auth.currentUser ?: return
        database.child("users").child(user.uid).child("institution").get().addOnSuccessListener { snapshot ->
            if (_binding == null) return@addOnSuccessListener
            currentInstitutionKey = AppUtils.getSafeKey(snapshot.value?.toString() ?: "General")
            loadDerivadoresFromDb()
        }
    }

    private fun loadDerivadoresFromDb() {
        val key = currentInstitutionKey ?: return
        derivadoresListener = database.child("institutions").child(key).child("derivadores").addValueEventListener(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                if (_binding == null) return
                val updatedList = mutableListOf<String>()
                if (snapshot.exists()) {
                    for (child in snapshot.children) {
                        val name = child.value?.toString()
                        if (name != null && name != "Otros") updatedList.add(name)
                    }
                } else {
                    updatedList.addAll(AppUtils.defaultDerivadores.filter { it != "Otros" })
                    database.child("institutions").child(key).child("derivadores").setValue(updatedList)
                }
                
                updatedList.sort()
                updatedList.add("Otros")

                derivadoresList.clear()
                derivadoresList.addAll(updatedList)
                derivadoresAdapter.notifyDataSetChanged()
                binding.editDerivedBy.setAdapter(derivadoresAdapter)
            }
            override fun onCancelled(error: DatabaseError) {}
        })
    }

    private fun showDerivadoresManagementDialog() {
        val options = arrayOf("Agregar nuevo", "Eliminar existente", "Restablecer lista")
        MaterialAlertDialogBuilder(requireContext())
            .setTitle("Gestionar Quien deriva")
            .setItems(options) { _, which ->
                when (which) {
                    0 -> showAddDerivadorDialog()
                    1 -> showDeleteDerivadorDialog()
                    2 -> resetDerivadores()
                }
            }
            .show()
    }

    private fun showAddDerivadorDialog() {
        val input = EditText(requireContext())
        input.hint = "Nombre del derivador"
        MaterialAlertDialogBuilder(requireContext())
            .setTitle("Agregar Derivador")
            .setView(input)
            .setPositiveButton("Guardar") { _, _ ->
                val name = input.text.toString().trim()
                if (name.isNotEmpty()) {
                    val key = currentInstitutionKey ?: return@setPositiveButton
                    val newList = derivadoresList.filter { it != "Otros" }.toMutableList()
                    newList.add(name)
                    database.child("institutions").child(key).child("derivadores").setValue(newList)
                }
            }
            .setNegativeButton("Cancelar", null)
            .show()
    }

    private fun showDeleteDerivadorDialog() {
        val names = derivadoresList.filter { it != "Otros" }.toTypedArray()
        if (names.isEmpty()) {
            showToast("No hay nombres para eliminar")
            return
        }
        MaterialAlertDialogBuilder(requireContext())
            .setTitle("Eliminar Derivador")
            .setItems(names) { _, which ->
                val key = currentInstitutionKey ?: return@setItems
                val nameToDelete = names[which]
                val newList = derivadoresList.filter { it != "Otros" && it != nameToDelete }
                database.child("institutions").child(key).child("derivadores").setValue(newList)
            }
            .setNegativeButton("Cancelar", null)
            .show()
    }

    private fun resetDerivadores() {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle("Restablecer Lista")
            .setMessage("¿Estás seguro de que deseas restablecer la lista original?")
            .setPositiveButton("Sí") { _, _ ->
                val key = currentInstitutionKey ?: return@setPositiveButton
                database.child("institutions").child(key).child("derivadores").setValue(AppUtils.defaultDerivadores.filter { it != "Otros" }.toList())
            }
            .setNegativeButton("No", null)
            .show()
    }

    private fun setLoading(isLoading: Boolean) {
        binding.progressSaving.visibility = if (isLoading) View.VISIBLE else View.GONE
        binding.buttonSaveForm.isEnabled = !isLoading
    }

    private fun saveForm() {
        val studentName = binding.editStudentName.text.toString().trim()
        val grado = binding.editGrado.text.toString().trim()
        val paralelo = binding.editParalelo.text.toString().trim()
        
        var caseType = binding.editCaseType.text.toString().trim()
        if (caseType == "Otros") {
            caseType = binding.editCustomCaseType.text.toString().trim()
            if (caseType.isEmpty()) {
                showToast("Por favor especifica el tipo de caso")
                return
            }
        }
        
        val date = binding.editDate.text.toString()
        val observations = binding.editObservations.text.toString().trim()
        
        var derivedBy = binding.editDerivedBy.text.toString().trim()
        if (derivedBy == "Otros") {
            derivedBy = binding.editCustomDerivedBy.text.toString().trim()
            if (derivedBy.isEmpty()) {
                showToast("Por favor especifica quién deriva")
                return
            }
        }

        if (studentName.isEmpty() || grado.isEmpty() || paralelo.isEmpty() || caseType.isEmpty() || derivedBy.isEmpty()) {
            showToast(getString(R.string.login_error_fields))
            return
        }

        // Validación extra para Violencia Sexual
        val isSexualViolence = binding.layoutSexualViolenceExtras.visibility == View.VISIBLE
        val repName = binding.editRepName.text.toString().trim()
        val repPhone = binding.editRepPhone.text.toString().trim()
        val reminderDay = binding.editReminderDay.text.toString()
        val reminderFreq = binding.editReminderFreq.text.toString()

        if (isSexualViolence) {
            if (repName.isEmpty() || repPhone.isEmpty() || reminderDay.isEmpty() || reminderFreq.isEmpty()) {
                showToast("Completa los datos de seguimiento especial")
                return
            }
        }

        setLoading(true)
        val user = auth.currentUser ?: return
        val actions = mutableMapOf<String, Boolean>()
        for (i in 0 until binding.checkboxContainer.childCount) {
            val child = binding.checkboxContainer.getChildAt(i)
            if (child is CheckBox && child.isChecked) actions[child.text.toString()] = true
        }

        val formData = mutableMapOf<String, Any>(
            "studentName" to studentName,
            "grado" to grado,
            "paralelo" to paralelo,
            "caseType" to caseType,
            "date" to date,
            "actions" to actions,
            "observations" to observations,
            "derivedBy" to derivedBy,
            "createdBy" to user.uid,
            "timestamp" to ServerValue.TIMESTAMP
        )
        
        if (isSexualViolence) {
            formData["sexualViolenceExtras"] = mapOf(
                "repName" to repName,
                "repPhone" to repPhone,
                "reminderDay" to reminderDay,
                "reminderFreq" to reminderFreq
            )
        }

        database.child("users").child(user.uid).child("institution").get().addOnSuccessListener { snapshot ->
            if (_binding == null) return@addOnSuccessListener
            val institutionName = snapshot.value?.toString()
            if (institutionName == null) {
                setLoading(false)
                showToast("Por favor configura tu institución en el Perfil")
                return@addOnSuccessListener
            }
            
            val safeKey = AppUtils.getSafeKey(institutionName)
            val formRef = if (editFormId != null) {
                database.child("institutions").child(safeKey).child("forms").child(editFormId!!)
            } else {
                database.child("institutions").child(safeKey).child("forms").push()
            }

            formRef.setValue(formData).addOnSuccessListener {
                if (_binding == null) return@addOnSuccessListener
                if (isSexualViolence) {
                    scheduleReminder(repName, repPhone, reminderDay, reminderFreq, studentName, safeKey)
                }
                setLoading(false)
                showToast(if (editFormId != null) "Formulario actualizado" else getString(R.string.new_form_success))
                findNavController().navigateUp()
            }.addOnFailureListener { e ->
                setLoading(false)
                showToast("Error al guardar: ${e.message}")
            }
        }.addOnFailureListener { e ->
            setLoading(false)
            showToast("Error de conexión: ${e.message}")
        }
    }

    private fun scheduleReminder(nombre: String, telefono: String, diaStr: String, frecuenciaStr: String, student: String, instKey: String) {
        val diaCalendario = when (diaStr.lowercase()) {
            "lunes" -> Calendar.MONDAY
            "martes" -> Calendar.TUESDAY
            "miércoles", "miercoles" -> Calendar.WEDNESDAY
            "jueves" -> Calendar.THURSDAY
            "viernes" -> Calendar.FRIDAY
            else -> Calendar.MONDAY
        }

        val unDiaMs = AlarmManager.INTERVAL_DAY
        val intervaloRepeticion = when (frecuenciaStr) {
            "Semanal" -> unDiaMs * 7
            "Bisemanal" -> unDiaMs * 14
            "Mensual" -> unDiaMs * 28
            else -> unDiaMs * 7
        }

        val alarmManager = requireContext().getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val reminderCalendar = Calendar.getInstance().apply {
            set(Calendar.DAY_OF_WEEK, diaCalendario)
            set(Calendar.HOUR_OF_DAY, 8) 
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            if (before(Calendar.getInstance())) {
                add(Calendar.WEEK_OF_YEAR, 1)
            }
        }

        val idUnico = (System.currentTimeMillis() % Int.MAX_VALUE).toInt()
        
        // Guardar recordatorio en Firebase para poder gestionarlo
        val reminderData = mapOf(
            "id" to idUnico,
            "studentName" to student,
            "repName" to nombre,
            "repPhone" to telefono,
            "day" to diaStr,
            "freq" to frecuenciaStr,
            "timestamp" to ServerValue.TIMESTAMP
        )
        database.child("institutions").child(instKey).child("reminders").child(idUnico.toString()).setValue(reminderData)

        val intent = Intent(requireContext(), RecordatorioReceiver::class.java).apply {
            putExtra("nombreRep", nombre)
            putExtra("telefonoRep", telefono)
            putExtra("idCaso", idUnico)
        }

        val pendingIntent = PendingIntent.getBroadcast(
            requireContext(),
            idUnico,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        alarmManager.setRepeating(
            AlarmManager.RTC_WAKEUP,
            reminderCalendar.timeInMillis,
            intervaloRepeticion,
            pendingIntent
        )
    }

    override fun onDestroyView() {
        super.onDestroyView()
        currentInstitutionKey?.let { key ->
            derivadoresListener?.let { 
                database.child("institutions").child(key).child("derivadores").removeEventListener(it) 
            }
        }
        _binding = null
    }
}
