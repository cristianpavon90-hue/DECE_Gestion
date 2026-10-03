package com.example.decegestin

import android.content.Context
import android.graphics.Color
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.AutoCompleteTextView
import android.widget.EditText
import android.widget.LinearLayout
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import com.example.decegestin.databinding.FragmentProfileBinding
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.FirebaseDatabase
import com.skydoves.colorpickerview.listeners.ColorEnvelopeListener

/**
 * Pantalla de perfil de usuario para gestionar datos personales, personalización y RBAC.
 */
class ProfileFragment : Fragment() {

    private var _binding: FragmentProfileBinding? = null
    private val binding get() = _binding!!

    private val auth by lazy { FirebaseAuth.getInstance() }
    private val database by lazy { FirebaseDatabase.getInstance() }
    private var selectedColorHex: String = "#0097A7"
    private var currentInstitutionKey: String? = null
    private var userCargo: String = "Analista DECE"

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentProfileBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.topBar.applyTopBarPadding()
        setupUI()
        loadCurrentUserData()
    }

    private fun setupUI() {
        binding.backIcon.setOnClickListener { findNavController().navigateUp() }

        val user = auth.currentUser
        binding.editContactEmail.setText(user?.email)

        // Cargar instituciones dinámicamente
        refreshInstitutionsDropdown()

        // Configurar ColorPicker
        binding.colorPickerView.attachAlphaSlider(binding.alphaSlideBar)
        binding.colorPickerView.attachBrightnessSlider(binding.brightnessSlideBar)
        binding.colorPickerView.setColorListener(ColorEnvelopeListener { envelope, _ ->
            selectedColorHex = "#" + envelope.hexCode
            binding.colorPreview.setBackgroundColor(envelope.color)
        })

        binding.buttonUpdateProfile.setOnClickListener { validateAndUpdateProfile() }
        binding.buttonNewPeriod.setOnClickListener { showNewPeriodConfirmation() }

        binding.buttonManageInstitutions.setOnClickListener { showManageInstitutionsDialog() }
        binding.buttonManageCedulas.setOnClickListener { showManageCedulasDialog() }
    }

    private fun refreshInstitutionsDropdown() {
        AppUtils.loadInstitutions(database) { instList ->
            if (_binding == null) return@loadInstitutions
            val adapter = ArrayAdapter(requireContext(), android.R.layout.simple_dropdown_item_1line, instList)
            binding.editInstitution.setAdapter(adapter)
            binding.editInstitution.setOnClickListener { binding.editInstitution.showDropDown() }
        }
    }

    private fun loadCurrentUserData() {
        val user = auth.currentUser ?: return
        database.reference.child("users").child(user.uid).get().addOnSuccessListener { snapshot ->
            if (_binding != null && snapshot.exists()) {
                val inst = snapshot.child("institution").value?.toString() ?: ""
                currentInstitutionKey = AppUtils.getSafeKey(inst)
                userCargo = snapshot.child("cargo").value?.toString() 
                    ?: snapshot.child("role").value?.toString() 
                    ?: "Analista DECE"

                binding.apply {
                    editFullName.setText(snapshot.child("fullName").value?.toString())
                    editIdCard.setText(snapshot.child("idCard").value?.toString())
                    editCargo.setText(userCargo)
                    editInstitution.setText(inst, false)
                    editCanton.setText(snapshot.child("canton").value?.toString())

                    val savedColor = snapshot.child("profileColor").value?.toString()
                    if (savedColor != null && savedColor.startsWith("#")) {
                        selectedColorHex = savedColor
                        try {
                            colorPreview.setBackgroundColor(Color.parseColor(savedColor))
                        } catch (_: Exception) {}
                    }

                    // Mostrar sección distrital sólo si el usuario es Coordinador Distrital
                    if (userCargo == "Coordinador Distrital" || userCargo.contains("Distrital", ignoreCase = true)) {
                        layoutDistritalSection.visibility = View.VISIBLE
                    } else {
                        layoutDistritalSection.visibility = View.GONE
                    }
                }
            }
        }
    }

    private fun showManageInstitutionsDialog() {
        val options = arrayOf(
            getString(R.string.distrital_add_institution),
            getString(R.string.distrital_delete_institution)
        )

        MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.distrital_manage_institutions)
            .setItems(options) { _, which ->
                if (which == 0) {
                    showAddInstitutionDialog()
                } else {
                    showDeleteInstitutionDialog()
                }
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private fun showAddInstitutionDialog() {
        val input = EditText(requireContext()).apply {
            hint = "Nombre de la institución (Ej: U.E. Vicente Fierro)"
        }

        MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.distrital_add_institution)
            .setView(input)
            .setPositiveButton(R.string.save) { _, _ ->
                val name = input.text.toString().trim()
                if (name.isNotEmpty()) {
                    AppUtils.addInstitution(database, name) { success, errorMsg ->
                        if (success) {
                            showToast("Institución agregada exitosamente")
                            refreshInstitutionsDropdown()
                        } else {
                            showToast("Error: ${errorMsg ?: "No se pudo guardar"}")
                        }
                    }
                }
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private fun showDeleteInstitutionDialog() {
        AppUtils.loadInstitutions(database) { instList ->
            if (instList.isEmpty()) {
                showToast("No hay instituciones disponibles")
                return@loadInstitutions
            }

            MaterialAlertDialogBuilder(requireContext())
                .setTitle(R.string.distrital_delete_institution)
                .setItems(instList.toTypedArray()) { _, which ->
                    val selected = instList[which]
                    AppUtils.deleteInstitution(database, selected) { success, errorMsg ->
                        if (success) {
                            showToast("Institución eliminada exitosamente")
                            refreshInstitutionsDropdown()
                        } else {
                            showToast("Error: ${errorMsg ?: "No se pudo eliminar"}")
                        }
                    }
                }
                .setNegativeButton(R.string.cancel, null)
                .show()
        }
    }

    private fun showManageCedulasDialog() {
        val options = arrayOf(
            getString(R.string.distrital_add_cedula),
            getString(R.string.distrital_delete_cedula)
        )

        MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.distrital_manage_cedulas)
            .setItems(options) { _, which ->
                if (which == 0) {
                    showAuthorizeCedulaDialog()
                } else {
                    showRevokeCedulaDialog()
                }
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private fun showAuthorizeCedulaDialog() {
        val layout = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(48, 16, 48, 16)
        }

        val inputCedula = EditText(requireContext()).apply {
            hint = "Número de Cédula"
            inputType = android.text.InputType.TYPE_CLASS_NUMBER
        }

        val inputCargo = AutoCompleteTextView(requireContext()).apply {
            hint = "Cargo / Perfil"
            val adapter = ArrayAdapter(requireContext(), android.R.layout.simple_dropdown_item_1line, AppUtils.cargos)
            setAdapter(adapter)
            setText(AppUtils.cargos[2], false)
            setOnClickListener { showDropDown() }
        }

        val inputInst = AutoCompleteTextView(requireContext()).apply {
            hint = "Institución"
            setOnClickListener { showDropDown() }
        }

        AppUtils.loadInstitutions(database) { instList ->
            val adapter = ArrayAdapter(requireContext(), android.R.layout.simple_dropdown_item_1line, instList)
            inputInst.setAdapter(adapter)
            if (instList.isNotEmpty()) inputInst.setText(instList[0], false)
        }

        layout.addView(inputCedula)
        layout.addView(inputCargo)
        layout.addView(inputInst)

        MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.distrital_add_cedula)
            .setView(layout)
            .setPositiveButton(R.string.save) { _, _ ->
                val idCard = inputCedula.text.toString().trim()
                val cargo = inputCargo.text.toString()
                val inst = inputInst.text.toString()

                if (idCard.isNotEmpty() && cargo.isNotEmpty() && inst.isNotEmpty()) {
                    val uid = auth.currentUser?.uid ?: ""
                    AppUtils.authorizeCedula(database, idCard, cargo, inst, uid) { success, errorMsg ->
                        if (success) {
                            showToast("Cédula autorizada exitosamente")
                        } else {
                            showToast("Error: ${errorMsg ?: "No se pudo autorizar"}")
                        }
                    }
                } else {
                    showToast("Completa todos los campos")
                }
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private fun showRevokeCedulaDialog() {
        database.reference.child("authorized_cedulas").get().addOnSuccessListener { snapshot ->
            if (!snapshot.exists() || snapshot.childrenCount == 0L) {
                showToast("No hay cédulas autorizadas registradas")
                return@addOnSuccessListener
            }

            val cedulaList = mutableListOf<String>()
            for (child in snapshot.children) {
                val id = child.key ?: continue
                val cargo = child.child("cargo").value?.toString() ?: ""
                val inst = child.child("institution").value?.toString() ?: ""
                cedulaList.add("$id ($cargo - $inst)")
            }

            MaterialAlertDialogBuilder(requireContext())
                .setTitle(R.string.distrital_delete_cedula)
                .setItems(cedulaList.toTypedArray()) { _, which ->
                    val rawItem = cedulaList[which]
                    val idToRevoke = rawItem.split(" ")[0].trim()
                    AppUtils.revokeCedula(database, idToRevoke) { success, errorMsg ->
                        if (success) {
                            showToast("Cédula revocada exitosamente")
                        } else {
                            showToast("Error: ${errorMsg ?: "No se pudo revocar"}")
                        }
                    }
                }
                .setNegativeButton(R.string.cancel, null)
                .show()
        }.addOnFailureListener {
            showToast("Error al cargar cédulas")
        }
    }

    private fun showNewPeriodConfirmation() {
        val options = arrayOf(
            getString(R.string.profile_new_period_option_keep),
            getString(R.string.profile_new_period_option_total)
        )

        MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.profile_new_period)
            .setItems(options) { _, which ->
                val keepSexualViolence = (which == 0)
                startNewPeriod(keepSexualViolence)
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private fun startNewPeriod(keepSexualViolence: Boolean) {
        val instKey = currentInstitutionKey ?: return
        val user = auth.currentUser ?: return

        // 1. Reiniciar Bitácora Local (SharedPreferences)
        val bitacoraPrefs = requireContext().getSharedPreferences("BitacoraPrefs_${user.uid}", Context.MODE_PRIVATE)
        bitacoraPrefs.edit().clear().apply()

        // 2. Limpiar Educando en Familia
        database.reference.child("institutions").child(instKey).child("educando").removeValue()

        // 3. Limpiar Categorías (Contadores/Grid)
        database.reference.child("institutions").child(instKey).child("categories").removeValue()

        // 4. Limpiar Recordatorios si es borrado total
        if (!keepSexualViolence) {
            database.reference.child("institutions").child(instKey).child("reminders").removeValue()
        }

        // 5. Limpiar Histórico
        database.reference.child("institutions").child(instKey).child("forms").get().addOnSuccessListener { snapshot ->
            for (child in snapshot.children) {
                val caseType = child.child("caseType").value?.toString()
                if (keepSexualViolence) {
                    if (caseType != "Violencia sexual") {
                        child.ref.removeValue()
                    }
                } else {
                    child.ref.removeValue()
                }
            }

            showToast(getString(R.string.profile_new_period_success))
            DashboardWidget.notifyUpdate(requireContext())
        }
    }

    private fun validateAndUpdateProfile() {
        val fullName = AppUtils.sanitizeInput(binding.editFullName.text.toString())
        val idCard = AppUtils.sanitizeInput(binding.editIdCard.text.toString())
        val institution = AppUtils.sanitizeInput(binding.editInstitution.text.toString())
        val canton = AppUtils.sanitizeInput(binding.editCanton.text.toString())

        if (fullName.isNotEmpty() && idCard.isNotEmpty() && institution.isNotEmpty() && canton.isNotEmpty()) {
            val words = fullName.split("\\s+".toRegex())
            val initial1 = words.getOrNull(0)?.firstOrNull()?.toString() ?: ""
            val initial2 = words.getOrNull(1)?.firstOrNull()?.toString() ?: ""
            val customInitials = (initial1 + initial2).uppercase().ifEmpty { "U" }

            val updates = mapOf(
                "fullName" to fullName,
                "idCard" to idCard,
                "institution" to institution,
                "institutionKey" to AppUtils.getSafeKey(institution),
                "canton" to canton,
                "profileColor" to selectedColorHex,
                "initials" to customInitials
            )

            val uid = auth.currentUser?.uid ?: return
            database.reference.child("users").child(uid).updateChildren(updates)
                .addOnSuccessListener {
                    showToast(getString(R.string.profile_updated))
                    DashboardWidget.notifyUpdate(requireContext())
                }
                .addOnFailureListener {
                    showToast(getString(R.string.profile_update_error))
                }
        } else {
            showToast(getString(R.string.login_error_fields))
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
