package com.example.decegestin

import android.os.Bundle
import androidx.fragment.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import androidx.navigation.fragment.findNavController
import com.example.decegestin.databinding.FragmentRegisterBinding
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.FirebaseDatabase

/**
 * Pantalla de registro de nuevos usuarios con verificación de Cédula y RBAC.
 */
class RegisterFragment : Fragment() {

    private var _binding: FragmentRegisterBinding? = null
    private val binding get() = _binding!!

    private val auth by lazy { FirebaseAuth.getInstance() }
    private val database by lazy { FirebaseDatabase.getInstance() }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentRegisterBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        binding.topBar.applyTopBarPadding()

        setupUI()
    }

    private fun setupUI() {
        binding.backIcon.setOnClickListener { findNavController().navigateUp() }

        // 1. Dropdown de Cargos / Perfiles (RBAC)
        val cargoAdapter = ArrayAdapter(requireContext(), android.R.layout.simple_dropdown_item_1line, AppUtils.cargos)
        binding.editCargo.setAdapter(cargoAdapter)
        binding.editCargo.setOnClickListener { binding.editCargo.showDropDown() }

        // 2. Dropdown de Instituciones (Cargado dinámicamente desde DB)
        AppUtils.loadInstitutions(database) { instList ->
            if (_binding == null) return@loadInstitutions
            val instAdapter = ArrayAdapter(requireContext(), android.R.layout.simple_dropdown_item_1line, instList)
            binding.editInstitution.setAdapter(instAdapter)
            binding.editInstitution.setOnClickListener { binding.editInstitution.showDropDown() }
        }

        binding.buttonRegisterEmail.setOnClickListener {
            val email = binding.editEmail.text.toString().trim()
            val password = binding.editPassword.text.toString().trim()
            val fullName = AppUtils.sanitizeInput(binding.editFullName.text.toString())
            val idCard = AppUtils.sanitizeInput(binding.editIdCard.text.toString())
            val cargo = binding.editCargo.text.toString()
            val institution = binding.editInstitution.text.toString()

            if (email.isNotEmpty() && password.isNotEmpty() && fullName.isNotEmpty() 
                && idCard.isNotEmpty() && cargo.isNotEmpty() && institution.isNotEmpty()) {
                
                verifyCedulaAndRegister(email, password, fullName, idCard, cargo, institution)
            } else {
                showToast(getString(R.string.login_error_fields))
            }
        }
    }

    private fun verifyCedulaAndRegister(
        email: String,
        password: String,
        fullName: String,
        idCard: String,
        cargo: String,
        institution: String
    ) {
        showToast(getString(R.string.loading))

        // Verificar si la Cédula está autorizada por el Coordinador Distrital en la base de datos
        database.reference.child("authorized_cedulas").get().addOnSuccessListener { snapshot ->
            if (_binding == null) return@addOnSuccessListener

            val isAuthorized = if (snapshot.exists() && snapshot.childrenCount > 0) {
                // Si la cédula existe en la lista de cédulas autorizadas y está activa
                snapshot.hasChild(idCard) && (snapshot.child(idCard).child("active").value != false)
            } else {
                // Si la base de datos de cédulas está vacía, se permite el registro del primer Coordinador Distrital
                cargo == "Coordinador Distrital"
            }

            if (isAuthorized) {
                registerWithEmail(email, password, fullName, idCard, cargo, institution)
            } else {
                showToast(getString(R.string.register_cedula_unauthorized))
            }
        }.addOnFailureListener { e ->
            if (_binding == null) return@addOnFailureListener
            showToast(getString(R.string.error_with_details, getString(R.string.error_occurred), e.message))
        }
    }

    private fun registerWithEmail(
        email: String,
        password: String,
        fullName: String,
        idCard: String,
        cargo: String,
        institution: String
    ) {
        auth.createUserWithEmailAndPassword(email, password)
            .addOnCompleteListener(requireActivity()) { task ->
                if (task.isSuccessful) {
                    saveUserToDatabase(auth.currentUser?.uid ?: "", email, fullName, idCard, cargo, institution)
                } else {
                    val exception = task.exception
                    if (exception is com.google.firebase.auth.FirebaseAuthUserCollisionException) {
                        auth.signInWithEmailAndPassword(email, password).addOnCompleteListener { loginTask ->
                            if (loginTask.isSuccessful) {
                                saveUserToDatabase(auth.currentUser?.uid ?: "", email, fullName, idCard, cargo, institution)
                            } else {
                                showToast(getString(R.string.error_with_details, getString(R.string.error_occurred), loginTask.exception?.message))
                            }
                        }
                    } else {
                        showToast(getString(R.string.error_with_details, getString(R.string.error_occurred), exception?.message))
                    }
                }
            }
    }

    private fun saveUserToDatabase(
        uid: String,
        email: String,
        fullName: String,
        idCard: String,
        cargo: String,
        institution: String
    ) {
        val words = fullName.split("\\s+".toRegex())
        val initial1 = words.getOrNull(0)?.firstOrNull()?.toString() ?: ""
        val initial2 = words.getOrNull(1)?.firstOrNull()?.toString() ?: ""
        val customInitials = (initial1 + initial2).uppercase().ifEmpty { "U" }

        val roleCode = when (cargo) {
            "Coordinador Distrital" -> "distrital"
            "Coordinador Institucional" -> "institucional"
            else -> "analista"
        }

        val userMap = mapOf(
            "uid" to uid,
            "email" to email,
            "fullName" to fullName,
            "idCard" to idCard,
            "cargo" to cargo,
            "role" to roleCode,
            "institution" to institution,
            "institutionKey" to AppUtils.getSafeKey(institution),
            "initials" to customInitials,
            "isRegistered" to true,
            "isApproved" to true
        )

        database.reference.child("users").child(uid).setValue(userMap)
            .addOnCompleteListener { task ->
                if (_binding == null) return@addOnCompleteListener
                if (task.isSuccessful) {
                    // Marcar también la Cédula como registrada/activa en authorized_cedulas
                    AppUtils.authorizeCedula(database, idCard, cargo, institution, uid) { _, _ -> }
                    findNavController().navigate(R.id.action_RegisterFragment_to_SecondFragment)
                } else {
                    val error = task.exception?.message ?: getString(R.string.error_occurred)
                    showToast(getString(R.string.register_db_error, error))
                }
            }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
