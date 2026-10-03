package com.example.decegestin

import android.app.DatePickerDialog
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.InputMethodManager
import android.widget.ArrayAdapter
import android.widget.AutoCompleteTextView
import android.widget.CheckBox
import android.widget.EditText
import androidx.core.content.edit
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import com.example.decegestin.databinding.FragmentNewFormBinding
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.*
import com.skydoves.balloon.*
import java.text.SimpleDateFormat
import java.util.*

/**
 * Fragmento para el registro de nuevas atenciones (Nuevo Formulario / Expediente).
 */
class NewFormFragment : Fragment() {

    private var _binding: FragmentNewFormBinding? = null
    private val binding get() = _binding!!

    private val auth by lazy { FirebaseAuth.getInstance() }
    private val database by lazy { FirebaseDatabase.getInstance().reference }
    private val calendar = Calendar.getInstance()

    private var selectedCategory: String = "Docentes"
    private val derivadoresList = mutableListOf<String>()
    private lateinit var derivadoresAdapter: ArrayAdapter<String>
    private var currentInstitutionKey: String? = null
    private var derivadoresListener: ValueEventListener? = null
    private var canManageCatalog: Boolean = false

    private var editFormId: String? = null
    private var originalCreatorId: String? = null

    private var originCasillaNumber: Int? = null
    private var originCategory: String? = null

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentNewFormBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        binding.topBar.applyTopBarPadding()
        setupUI()
        checkEditMode()

        binding.btnTutorial.setOnClickListener { startTutorial() }
        checkFirstTimeTutorial()
    }

    private fun checkFirstTimeTutorial() {
        val uid = auth.currentUser?.uid ?: return
        val prefs = requireContext().getSharedPreferences("FormTutorialPrefs_$uid", Context.MODE_PRIVATE)
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
            setText(getString(R.string.tut_form_step1))
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
            setText(getString(R.string.tut_form_step2))
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
            setArrowPosition(0.5f)
            setWidthRatio(0.8f)
            setHeight(BalloonSizeSpec.WRAP)
            setText(getString(R.string.tut_form_step3))
            setTextColorResource(R.color.white)
            setTextSize(14f)
            setBackgroundColorResource(R.color.md_theme_primary)
            setBalloonAnimation(BalloonAnimation.ELASTIC)
            setLifecycleOwner(viewLifecycleOwner)
            setDismissWhenClicked(true)
        }

        val b4 = createBalloon(requireContext()) {
            setArrowSize(10)
            setArrowOrientation(ArrowOrientation.BOTTOM)
            setArrowPosition(0.5f)
            setWidthRatio(0.8f)
            setHeight(BalloonSizeSpec.WRAP)
            setText(getString(R.string.tut_form_step4))
            setTextColorResource(R.color.white)
            setTextSize(14f)
            setBackgroundColorResource(R.color.md_theme_primary)
            setBalloonAnimation(BalloonAnimation.ELASTIC)
            setLifecycleOwner(viewLifecycleOwner)
            setDismissWhenClicked(true)
        }

        b1.showAlignBottom(binding.editStudentName)
        b1.setOnBalloonDismissListener {
            if (_binding != null) b2.showAlignBottom(binding.editDerivedCategory)
        }
        b2.setOnBalloonDismissListener {
            if (_binding != null) {
                if (binding.layoutSexualViolenceExtras.visibility == View.VISIBLE) {
                    b3.showAlignBottom(binding.editRepName)
                } else {
                    b4.showAlignTop(binding.buttonSaveForm)
                }
            }
        }
        b3.setOnBalloonDismissListener {
            if (_binding != null) b4.showAlignTop(binding.buttonSaveForm)
        }
    }

    private fun checkEditMode() {
        arguments?.let { bundle ->
            editFormId = bundle.getString("formId")
            originalCreatorId = bundle.getString("createdBy")

            if (bundle.containsKey("originCasillaNumber")) {
                originCasillaNumber = bundle.getInt("originCasillaNumber")
            }
            originCategory = bundle.getString("originCategory")

            val originDoc = bundle.getString("originDocument")
            val prepopulatedName = bundle.getString("studentName")
            val prepopulatedDate = bundle.getString("date")
            val prepopulatedCaseType = bundle.getString("caseType")

            if (editFormId == null && originCasillaNumber != null) {
                // Apertura de expediente desde documento/casilla prioritaria
                if (!prepopulatedName.isNullOrEmpty()) {
                    binding.editStudentName.setText(prepopulatedName)
                }
                if (!prepopulatedDate.isNullOrEmpty()) {
                    binding.editDate.setText(prepopulatedDate)
                }
                if (!prepopulatedCaseType.isNullOrEmpty()) {
                    if (AppUtils.caseTypes.contains(prepopulatedCaseType)) {
                        binding.editCaseType.setText(prepopulatedCaseType, false)
                    } else {
                        binding.editCaseType.setText("Otros", false)
                        binding.layoutCustomCaseType.visibility = View.VISIBLE
                        binding.editCustomCaseType.setText(prepopulatedCaseType)
                    }
                }
                if (!originDoc.isNullOrEmpty()) {
                    binding.editObservations.setText("Expediente aperturado a partir del documento prioritario: $originDoc.")
                }
            } else if (editFormId != null) {
                // Modo edición de expediente existente
                binding.buttonSaveForm.text = getString(R.string.form_updated)

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

                val derivedCategory = bundle.getString("derivedCategory") ?: ""
                val derivedBy = bundle.getString("derivedBy") ?: ""

                if (derivedCategory.isNotEmpty()) {
                    binding.editDerivedCategory.setText(derivedCategory, false)
                    selectedCategory = derivedCategory
                }
                val cleanName = if (derivedBy.contains(" (")) derivedBy.substringBefore(" (").trim() else derivedBy
                binding.editDerivedBy.setText(cleanName, false)

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

        // 4. Quien deriva (Categorizado + Smart Selector)
        setupDerivadores()

        // 5. Configurar dropdowns de Recordatorio (Seguimiento Especial)
        val countryCodeAdapter = ArrayAdapter(requireContext(), android.R.layout.simple_dropdown_item_1line, AppUtils.countryCodes)
        setupExposedDropdown(binding.editCountryCode, countryCodeAdapter)
        binding.editCountryCode.setText(AppUtils.countryCodes[0], false)

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

        // Restricción de teléfono sin "0" inicial
        binding.editRepPhone.addTextChangedListener(object : TextWatcher {
            override fun afterTextChanged(s: Editable?) {
                if (s != null && s.startsWith("0")) {
                    s.delete(0, 1)
                    showToast("Ingresa el teléfono sin el '0' inicial (Ejemplo: 962934745)")
                }
            }
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
        })

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
        val categoryAdapter = ArrayAdapter(requireContext(), android.R.layout.simple_dropdown_item_1line, AppUtils.derivadorCategorias)
        setupExposedDropdown(binding.editDerivedCategory, categoryAdapter)
        binding.editDerivedCategory.setText(selectedCategory, false)

        binding.editDerivedCategory.setOnItemClickListener { _, _, position, _ ->
            val cat = categoryAdapter.getItem(position) ?: "Docentes"
            selectedCategory = cat
            binding.editDerivedBy.text?.clear()
            binding.layoutCustomDerivedBy.visibility = View.GONE
            binding.editCustomDerivedBy.text?.clear()
            loadDerivadoresFromDb(selectedCategory)
        }

        derivadoresAdapter = ArrayAdapter(requireContext(), android.R.layout.simple_dropdown_item_1line, derivadoresList)
        setupExposedDropdown(binding.editDerivedBy, derivadoresAdapter)

        binding.editDerivedBy.setOnItemClickListener { _, _, position, _ ->
            val selected = derivadoresAdapter.getItem(position)
            val addNewLabel = getString(R.string.derivador_add_new)
            if (selected == addNewLabel || selected == "Otros" || selected == "Añadir nuevo…") {
                binding.layoutCustomDerivedBy.visibility = View.VISIBLE
                binding.editCustomDerivedBy.requestFocus()
                showKeyboard(binding.editCustomDerivedBy)
            } else {
                binding.layoutCustomDerivedBy.visibility = View.GONE
                binding.editCustomDerivedBy.text?.clear()
            }
        }

        val handler = Handler(Looper.getMainLooper())
        val longPressRunnable = Runnable {
            if (canManageCatalog) {
                showDerivadoresManagementDialog()
            } else {
                showToast(getString(R.string.derivador_restricted_msg))
            }
        }

        binding.editDerivedBy.setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> handler.postDelayed(longPressRunnable, 3000)
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> handler.removeCallbacks(longPressRunnable)
            }
            if (event.action == MotionEvent.ACTION_UP) {
                binding.editDerivedBy.showDropDown()
            }
            false
        }

        val user = auth.currentUser ?: return
        database.child("users").child(user.uid).get().addOnSuccessListener { snapshot ->
            if (_binding == null) return@addOnSuccessListener
            val cargo = snapshot.child("cargo").value?.toString() ?: ""
            val role = snapshot.child("role").value?.toString() ?: ""
            canManageCatalog = cargo.contains("Institucional", ignoreCase = true) 
                || cargo.contains("Distrital", ignoreCase = true) 
                || role == "institucional" || role == "distrital"

            val instName = snapshot.child("institution").value?.toString() ?: "General"
            currentInstitutionKey = AppUtils.getSafeKey(instName)
            loadDerivadoresFromDb(selectedCategory)
        }
    }

    private fun loadDerivadoresFromDb(category: String) {
        val key = currentInstitutionKey ?: return
        derivadoresListener?.let {
            database.child("institutions").child(key).child("derivadores").child(selectedCategory).removeEventListener(it)
        }

        val ref = database.child("institutions").child(key).child("derivadores").child(category)
        derivadoresListener = ref.addValueEventListener(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                if (_binding == null) return
                val updatedList = mutableListOf<String>()
                val addNewLabel = getString(R.string.derivador_add_new)

                if (snapshot.exists() && snapshot.childrenCount > 0) {
                    for (child in snapshot.children) {
                        val name = child.value?.toString()
                        if (!name.isNullOrEmpty() && name != "Otros" && name != addNewLabel) {
                            updatedList.add(name)
                        }
                    }
                } else {
                    val defaults = AppUtils.defaultDerivadoresCategorias[category] ?: emptyList()
                    updatedList.addAll(defaults)
                    ref.setValue(defaults)
                }

                updatedList.sort()
                updatedList.add(addNewLabel)

                derivadoresList.clear()
                derivadoresList.addAll(updatedList)
                derivadoresAdapter.notifyDataSetChanged()
                binding.editDerivedBy.setAdapter(derivadoresAdapter)
            }

            override fun onCancelled(error: DatabaseError) {}
        })
    }

    private fun showDerivadoresManagementDialog() {
        val options = arrayOf(
            "Agregar nuevo a $selectedCategory",
            "Eliminar existente de $selectedCategory",
            "Restablecer categoría $selectedCategory"
        )
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.dialog_mgmt_derivador)
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
        val input = EditText(requireContext()).apply {
            hint = "Nombre del derivador"
        }
        MaterialAlertDialogBuilder(requireContext())
            .setTitle("Agregar Derivador ($selectedCategory)")
            .setView(input)
            .setPositiveButton("Guardar") { _, _ ->
                val name = input.text.toString().trim()
                if (name.isNotEmpty()) {
                    val key = currentInstitutionKey ?: return@setPositiveButton
                    val addNewLabel = getString(R.string.derivador_add_new)
                    val newList = derivadoresList.filter { it != "Otros" && it != addNewLabel }.toMutableList()
                    if (!newList.contains(name)) {
                        newList.add(name)
                        database.child("institutions").child(key).child("derivadores").child(selectedCategory).setValue(newList)
                    }
                }
            }
            .setNegativeButton("Cancelar", null)
            .show()
    }

    private fun showDeleteDerivadorDialog() {
        val addNewLabel = getString(R.string.derivador_add_new)
        val names = derivadoresList.filter { it != "Otros" && it != addNewLabel }.toTypedArray()
        if (names.isEmpty()) {
            showToast(getString(R.string.validation_no_names_delete))
            return
        }
        MaterialAlertDialogBuilder(requireContext())
            .setTitle("Eliminar Derivador ($selectedCategory)")
            .setItems(names) { _, which ->
                val key = currentInstitutionKey ?: return@setItems
                val nameToDelete = names[which]
                val newList = names.filter { it != nameToDelete }
                database.child("institutions").child(key).child("derivadores").child(selectedCategory).setValue(newList)
            }
            .setNegativeButton("Cancelar", null)
            .show()
    }

    private fun resetDerivadores() {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.dialog_reset_list)
            .setMessage("¿Estás seguro de que deseas restablecer los valores por defecto de $selectedCategory?")
            .setPositiveButton("Sí") { _, _ ->
                val key = currentInstitutionKey ?: return@setPositiveButton
                val defaults = AppUtils.defaultDerivadoresCategorias[selectedCategory] ?: emptyList()
                database.child("institutions").child(key).child("derivadores").child(selectedCategory).setValue(defaults)
            }
            .setNegativeButton("No", null)
            .show()
    }

    private fun setLoading(isLoading: Boolean) {
        binding.progressSaving.visibility = if (isLoading) View.VISIBLE else View.GONE
        binding.buttonSaveForm.isEnabled = !isLoading
    }

    private fun saveForm() {
        val studentName = AppUtils.sanitizeInput(binding.editStudentName.text.toString())
        val grado = AppUtils.sanitizeInput(binding.editGrado.text.toString())
        val paralelo = AppUtils.sanitizeInput(binding.editParalelo.text.toString())

        var caseType = AppUtils.sanitizeInput(binding.editCaseType.text.toString())
        if (caseType == "Otros") {
            caseType = AppUtils.sanitizeInput(binding.editCustomCaseType.text.toString())
            if (caseType.isEmpty()) {
                showToast(getString(R.string.validation_specify_case))
                return
            }
        }

        val date = AppUtils.sanitizeInput(binding.editDate.text.toString())
        val observations = AppUtils.sanitizeInput(binding.editObservations.text.toString())

        val derivedCategory = binding.editDerivedCategory.text.toString()
        var derivedByRaw = AppUtils.sanitizeInput(binding.editDerivedBy.text.toString())
        val addNewLabel = getString(R.string.derivador_add_new)

        val derivedBy = if (derivedByRaw == addNewLabel || derivedByRaw == "Otros" || derivedByRaw == "Añadir nuevo…") {
            val custom = AppUtils.sanitizeInput(binding.editCustomDerivedBy.text.toString())
            if (custom.isEmpty()) {
                showToast(getString(R.string.validation_specify_derived))
                return
            }
            if (canManageCatalog && currentInstitutionKey != null && derivedCategory.isNotEmpty()) {
                val currentCatalog = derivadoresList.filter { it != "Otros" && it != addNewLabel }.toMutableList()
                if (!currentCatalog.contains(custom)) {
                    currentCatalog.add(custom)
                    database.child("institutions").child(currentInstitutionKey!!).child("derivadores").child(derivedCategory).setValue(currentCatalog)
                }
            }
            custom
        } else {
            derivedByRaw
        }

        if (studentName.isEmpty() || grado.isEmpty() || paralelo.isEmpty() || caseType.isEmpty() || derivedBy.isEmpty()) {
            showToast(getString(R.string.login_error_fields))
            return
        }

        val isSexualViolence = binding.layoutSexualViolenceExtras.visibility == View.VISIBLE
        val repName = AppUtils.sanitizeInput(binding.editRepName.text.toString())
        val countryCode = binding.editCountryCode.text.toString()
        val rawRepPhone = AppUtils.sanitizeInput(binding.editRepPhone.text.toString())
        val repPhone = AppUtils.formatPhoneNumber(countryCode, rawRepPhone)
        val reminderDay = AppUtils.sanitizeInput(binding.editReminderDay.text.toString())
        val reminderFreq = AppUtils.sanitizeInput(binding.editReminderFreq.text.toString())

        if (isSexualViolence) {
            if (repName.isEmpty() || repPhone.isEmpty() || reminderDay.isEmpty() || reminderFreq.isEmpty()) {
                showToast("Completa los datos de seguimiento especial")
                return
            }
        }

        setLoading(true)
        val user = auth.currentUser ?: return

        if (!AppUtils.canMakeRequest(user.uid)) {
            setLoading(false)
            showToast(getString(R.string.error_too_many_requests))
            return
        }

        if (editFormId != null && originalCreatorId != null && originalCreatorId != user.uid) {
            setLoading(false)
            showToast("No tienes permiso para modificar este formulario")
            return
        }

        val actions = mutableMapOf<String, Boolean>()
        for (i in 0 until binding.checkboxContainer.childCount) {
            val child = binding.checkboxContainer.getChildAt(i)
            if (child is CheckBox && child.isChecked) actions[child.text.toString()] = true
        }

        val formattedDerivedBy = if (derivedCategory.isNotEmpty()) "$derivedBy ($derivedCategory)" else derivedBy

        val formData = mutableMapOf<String, Any>(
            "studentName" to studentName,
            "grado" to grado,
            "paralelo" to paralelo,
            "caseType" to caseType,
            "date" to date,
            "actions" to actions,
            "observations" to observations,
            "derivedCategory" to derivedCategory,
            "derivedBy" to formattedDerivedBy,
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

        val saveToDb = { instKey: String ->
            val formRef = if (editFormId != null) {
                database.child("institutions").child(instKey).child("forms").child(editFormId!!)
            } else {
                database.child("institutions").child(instKey).child("forms").push()
            }

            formRef.setValue(formData).addOnCompleteListener { task ->
                if (_binding == null) return@addOnCompleteListener
                if (task.isSuccessful) {
                    val formId = formRef.key ?: ""

                    // Si este expediente se inició a partir de un documento/casilla prioritaria, actualizar la casilla
                    if (originCasillaNumber != null && !originCategory.isNullOrEmpty()) {
                        database.child("institutions").child(instKey).child("categories")
                            .child(originCategory!!).child(originCasillaNumber.toString())
                            .updateChildren(mapOf("vinculadoAExpediente" to true, "idExpediente" to formId))
                    }

                    if (isSexualViolence) {
                        scheduleReminder(repName, repPhone, reminderDay, reminderFreq, studentName, instKey)
                    }
                    setLoading(false)
                    showToast(if (editFormId != null) getString(R.string.form_updated) else getString(R.string.new_form_success))
                    findNavController().navigateUp()
                } else {
                    setLoading(false)
                    showToast("Error al guardar: ${task.exception?.message}")
                }
            }
        }

        if (currentInstitutionKey != null) {
            saveToDb(currentInstitutionKey!!)
        } else {
            database.child("users").child(user.uid).child("institution").get().addOnSuccessListener { snapshot ->
                if (_binding == null) return@addOnSuccessListener
                val institutionName = snapshot.value?.toString()
                if (institutionName == null) {
                    setLoading(false)
                    showToast(getString(R.string.error_no_inst_profile))
                    return@addOnSuccessListener
                }
                val safeKey = AppUtils.getSafeKey(institutionName)
                currentInstitutionKey = safeKey
                saveToDb(safeKey)
            }.addOnFailureListener { e ->
                setLoading(false)
                showToast(getString(R.string.error_firebase, e.message))
            }
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

        val reminderData = mapOf(
            "id" to idUnico,
            "studentName" to student,
            "repName" to nombre,
            "repPhone" to telefono,
            "day" to diaStr,
            "freq" to frecuenciaStr,
            "hour" to 8,
            "minute" to 0,
            "createdBy" to auth.currentUser?.uid as Any,
            "timestamp" to ServerValue.TIMESTAMP
        )
        database.child("institutions").child(instKey).child("reminders").child(idUnico.toString()).setValue(reminderData)

        val intent = Intent(requireContext(), RecordatorioReceiver::class.java).apply {
            putExtra("nombreRep", nombre)
            putExtra("telefonoRep", telefono)
            putExtra("idCaso", idUnico)
            putExtra("freq", frecuenciaStr)
            addFlags(Intent.FLAG_RECEIVER_FOREGROUND)
        }

        AppUtils.scheduleExactAlarm(requireContext(), idUnico, reminderCalendar.timeInMillis, intent)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        currentInstitutionKey?.let { key ->
            derivadoresListener?.let {
                database.child("institutions").child(key).child("derivadores").child(selectedCategory).removeEventListener(it)
            }
        }
        _binding = null
    }
}
