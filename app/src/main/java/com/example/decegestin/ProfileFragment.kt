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
import com.google.firebase.database.ServerValue
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

        binding.buttonApproveUsers.setOnClickListener { showApproveUsersDialog() }
        binding.buttonManageInstitutions.setOnClickListener { showManageInstitutionsDialog() }
        binding.buttonManageCedulas.setOnClickListener { showManageCedulasDialog() }

        binding.buttonSimulateRole.setOnClickListener {
            val user = auth.currentUser ?: return@setOnClickListener
            database.reference.child("users").child(user.uid).get().addOnSuccessListener { snapshot ->
                if (_binding == null) return@addOnSuccessListener
                val realRole = snapshot.child("role").value?.toString() ?: ""
                AppUtils.showRoleSimulationDialog(requireContext(), realRole) {
                    loadCurrentUserData()
                }
            }
        }
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

                val realRole = snapshot.child("role").value?.toString() ?: ""
                val activeRole = AppUtils.getActiveRole(requireContext(), realRole)

                binding.apply {
                    editFullName.setText(snapshot.child("fullName").value?.toString())
                    editIdCard.setText(snapshot.child("idCard").value?.toString())
                    
                    val currentSim = AppUtils.getSimulatedRole(requireContext())
                    val cargoDisplay = if (currentSim != null) "$userCargo [Simulando: $currentSim]" else userCargo
                    editCargo.setText(cargoDisplay)
                    
                    editInstitution.setText(inst, false)
                    editCanton.setText(snapshot.child("canton").value?.toString())

                    val savedColor = snapshot.child("profileColor").value?.toString()
                    if (savedColor != null && savedColor.startsWith("#")) {
                        selectedColorHex = savedColor
                        try {
                            colorPreview.setBackgroundColor(Color.parseColor(savedColor))
                        } catch (_: Exception) {}
                    }

                    if (realRole == "admin") {
                        buttonSimulateRole.visibility = View.VISIBLE
                    } else {
                        buttonSimulateRole.visibility = View.GONE
                    }

                    if (activeRole == "admin" || activeRole == "distrital" || (realRole == "admin" && activeRole == "distrital")) {
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

    private fun showApproveUsersDialog() {
        showToast(getString(R.string.loading))
        database.reference.child("users").get().addOnSuccessListener { snapshot ->
            if (_binding == null) return@addOnSuccessListener

            val pendingUsers = mutableListOf<Map<String, Any>>()
            val approvedUsers = mutableListOf<Map<String, Any>>()

            for (child in snapshot.children) {
                val userMap = child.value as? Map<String, Any> ?: continue
                val isApproved = userMap["isApproved"] as? Boolean ?: false
                val mutable = userMap.toMutableMap()
                mutable["uid"] = child.key ?: ""

                if (!isApproved) {
                    pendingUsers.add(mutable)
                } else {
                    approvedUsers.add(mutable)
                }
            }

            val options = arrayOf(
                "Solicitudes Pendientes por Validar (${pendingUsers.size})",
                "Gestionar Cuentas Ya Aprobadas (${approvedUsers.size})"
            )

            MaterialAlertDialogBuilder(requireContext())
                .setTitle("Validar y Gestionar Cuentas")
                .setItems(options) { _, which ->
                    if (which == 0) {
                        showPendingUsersList(pendingUsers)
                    } else {
                        showApprovedUsersList(approvedUsers)
                    }
                }
                .setNegativeButton(R.string.cancel, null)
                .show()
        }.addOnFailureListener { e ->
            showToast("Error al cargar usuarios: ${e.message}")
        }
    }

    private fun showPendingUsersList(pendingUsers: List<Map<String, Any>>) {
        if (pendingUsers.isEmpty()) {
            showToast("No hay solicitudes pendientes de validación")
            return
        }

        val displayList = pendingUsers.map { u ->
            val name = u["fullName"]?.toString() ?: "Sin nombre"
            val idCard = u["idCard"]?.toString() ?: ""
            val reqCargo = u["requestedCargo"]?.toString() ?: u["cargo"]?.toString() ?: "Analista DECE"
            val inst = u["institution"]?.toString() ?: ""
            "$name ($idCard)\nCargo: $reqCargo | Inst: $inst"
        }.toTypedArray()

        MaterialAlertDialogBuilder(requireContext())
            .setTitle("Solicitudes Pendientes (${pendingUsers.size})")
            .setItems(displayList) { _, which ->
                val selectedUser = pendingUsers[which]
                showValidateUserActionDialog(selectedUser)
            }
            .setNegativeButton(R.string.back, null)
            .show()
    }

    private fun showApprovedUsersList(approvedUsers: List<Map<String, Any>>) {
        if (approvedUsers.isEmpty()) {
            showToast("No hay cuentas aprobadas")
            return
        }

        val displayList = approvedUsers.map { u ->
            val name = u["fullName"]?.toString() ?: "Sin nombre"
            val idCard = u["idCard"]?.toString() ?: ""
            val cargo = u["cargo"]?.toString() ?: "Analista DECE"
            val inst = u["institution"]?.toString() ?: ""
            "$name ($idCard)\nRol Actual: $cargo | Inst: $inst"
        }.toTypedArray()

        MaterialAlertDialogBuilder(requireContext())
            .setTitle("Cuentas Aprobadas (${approvedUsers.size})")
            .setItems(displayList) { _, which ->
                val selectedUser = approvedUsers[which]
                showEditApprovedUserDialog(selectedUser)
            }
            .setNegativeButton(R.string.back, null)
            .show()
    }

    private fun showValidateUserActionDialog(userMap: Map<String, Any>) {
        val targetUid = userMap["uid"]?.toString() ?: return
        val name = userMap["fullName"]?.toString() ?: "Usuario"
        val idCard = userMap["idCard"]?.toString() ?: ""
        val reqCargo = userMap["requestedCargo"]?.toString() ?: userMap["cargo"]?.toString() ?: "Analista DECE"
        val inst = userMap["institution"]?.toString() ?: ""

        val dialogView = LayoutInflater.from(requireContext()).inflate(R.layout.dialog_validate_user, null)
        val textTitle = dialogView.findViewById<android.widget.TextView>(R.id.text_dialog_title)
        val textDetails = dialogView.findViewById<android.widget.TextView>(R.id.text_user_details)
        val editSelectCargo = dialogView.findViewById<AutoCompleteTextView>(R.id.edit_select_cargo)
        val btnApprove = dialogView.findViewById<com.google.android.material.button.MaterialButton>(R.id.btn_approve_account)
        val btnReject = dialogView.findViewById<com.google.android.material.button.MaterialButton>(R.id.btn_reject_account)

        textTitle.text = "Validar Cuenta de Usuario"
        textDetails.text = "Nombre: $name\nCédula: $idCard\nInstitución: $inst\nCargo Solicitado: $reqCargo"

        val adapter = ArrayAdapter(requireContext(), android.R.layout.simple_dropdown_item_1line, AppUtils.cargos)
        editSelectCargo.setAdapter(adapter)
        editSelectCargo.setText(reqCargo, false)
        editSelectCargo.setOnClickListener { editSelectCargo.showDropDown() }

        var dialog: androidx.appcompat.app.AlertDialog? = null

        btnApprove.setOnClickListener {
            val selectedCargo = editSelectCargo.text.toString()
            val selectedRoleCode = AppUtils.getRoleCode(selectedCargo)
            validateAccountInFirebase(targetUid, idCard, selectedCargo, selectedRoleCode, inst)
            dialog?.dismiss()
        }

        btnReject.setOnClickListener {
            rejectUser(targetUid, idCard)
            dialog?.dismiss()
        }

        dialog = MaterialAlertDialogBuilder(requireContext())
            .setView(dialogView)
            .setNegativeButton(R.string.cancel, null)
            .create()

        dialog.show()
    }

    private fun showEditApprovedUserDialog(userMap: Map<String, Any>) {
        val targetUid = userMap["uid"]?.toString() ?: return
        val name = userMap["fullName"]?.toString() ?: "Usuario"
        val idCard = userMap["idCard"]?.toString() ?: ""
        val currentCargo = userMap["cargo"]?.toString() ?: "Analista DECE"
        val inst = userMap["institution"]?.toString() ?: ""

        val dialogView = LayoutInflater.from(requireContext()).inflate(R.layout.dialog_validate_user, null)
        val textTitle = dialogView.findViewById<android.widget.TextView>(R.id.text_dialog_title)
        val textDetails = dialogView.findViewById<android.widget.TextView>(R.id.text_user_details)
        val editSelectCargo = dialogView.findViewById<AutoCompleteTextView>(R.id.edit_select_cargo)
        val btnApprove = dialogView.findViewById<com.google.android.material.button.MaterialButton>(R.id.btn_approve_account)
        val btnRevoke = dialogView.findViewById<com.google.android.material.button.MaterialButton>(R.id.btn_revoke_account)
        val btnReject = dialogView.findViewById<com.google.android.material.button.MaterialButton>(R.id.btn_reject_account)

        textTitle.text = "Gestionar Cuenta Aprobada"
        textDetails.text = "Nombre: $name\nCédula: $idCard\nInstitución: $inst\nCargo Actual: $currentCargo"

        val adapter = ArrayAdapter(requireContext(), android.R.layout.simple_dropdown_item_1line, AppUtils.cargos)
        editSelectCargo.setAdapter(adapter)
        editSelectCargo.setText(currentCargo, false)
        editSelectCargo.setOnClickListener { editSelectCargo.showDropDown() }

        btnApprove.text = "Actualizar Cargo / Rol"
        btnRevoke.visibility = View.VISIBLE

        var dialog: androidx.appcompat.app.AlertDialog? = null

        btnApprove.setOnClickListener {
            val selectedCargo = editSelectCargo.text.toString()
            val selectedRoleCode = AppUtils.getRoleCode(selectedCargo)
            validateAccountInFirebase(targetUid, idCard, selectedCargo, selectedRoleCode, inst)
            dialog?.dismiss()
        }

        btnRevoke.setOnClickListener {
            revokeAccountApprovalInFirebase(targetUid, idCard)
            dialog?.dismiss()
        }

        btnReject.setOnClickListener {
            rejectUser(targetUid, idCard)
            dialog?.dismiss()
        }

        dialog = MaterialAlertDialogBuilder(requireContext())
            .setView(dialogView)
            .setNegativeButton(R.string.cancel, null)
            .create()

        dialog.show()
    }

    private fun validateAccountInFirebase(
        targetUid: String,
        idCard: String,
        cargo: String,
        role: String,
        institution: String
    ) {
        val adminUid = auth.currentUser?.uid ?: ""
        val updates = mapOf<String, Any>(
            "isApproved" to true,
            "cargo" to cargo,
            "role" to role,
            "approvedBy" to adminUid,
            "approvedAt" to ServerValue.TIMESTAMP
        )

        database.reference.child("users").child(targetUid).updateChildren(updates).addOnSuccessListener {
            AppUtils.authorizeCedula(database, idCard, cargo, institution, adminUid) { _, _ -> }
            showToast("Cuenta validada y actualizada exitosamente como $cargo")
        }.addOnFailureListener { e ->
            showToast("Error al validar cuenta: ${e.message}")
        }
    }

    private fun revokeAccountApprovalInFirebase(targetUid: String, idCard: String) {
        val updates = mapOf<String, Any>(
            "isApproved" to false
        )
        database.reference.child("users").child(targetUid).updateChildren(updates).addOnSuccessListener {
            if (idCard.isNotEmpty()) {
                AppUtils.revokeCedula(database, idCard) { _, _ -> }
            }
            showToast("Aprobación revocada. La cuenta ha vuelto al estado pendiente.")
        }.addOnFailureListener { e ->
            showToast("Error al revocar aprobación: ${e.message}")
        }
    }

    private fun rejectUser(targetUid: String, idCard: String) {
        database.reference.child("users").child(targetUid).removeValue().addOnSuccessListener {
            if (idCard.isNotEmpty()) {
                AppUtils.revokeCedula(database, idCard) { _, _ -> }
            }
            showToast("Cuenta eliminada de la base de datos")
        }.addOnFailureListener { e ->
            showToast("Error al eliminar cuenta: ${e.message}")
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
