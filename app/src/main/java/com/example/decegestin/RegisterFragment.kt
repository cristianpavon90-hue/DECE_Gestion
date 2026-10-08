package com.example.decegestin

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import com.example.decegestin.databinding.FragmentRegisterBinding
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseAuthUserCollisionException
import com.google.firebase.database.FirebaseDatabase

/**
 * Registro de usuarios blindado:
 * - Autenticación Auth primero para respetar reglas auth != null.
 * - Validación de cédula autorizada desde la base de datos.
 * - Forzado de estado pendiente (isApproved = false) y rol base analista.
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

        // 1. Selector de Cargos
        val cargoAdapter = ArrayAdapter(requireContext(), android.R.layout.simple_dropdown_item_1line, AppUtils.cargos)
        binding.editCargo.setAdapter(cargoAdapter)
        binding.editCargo.setOnClickListener { binding.editCargo.showDropDown() }

        // 2. Selector de Instituciones
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

                iniciarProcesoDeRegistro(email, password, fullName, idCard, cargo, institution)
            } else {
                Toast.makeText(requireContext(), getString(R.string.login_error_fields), Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun iniciarProcesoDeRegistro(
        email: String,
        password: String,
        fullName: String,
        idCard: String,
        cargo: String,
        institution: String
    ) {
        Toast.makeText(requireContext(), getString(R.string.loading), Toast.LENGTH_SHORT).show()

        // Creamos primero las credenciales en Firebase Auth para poseer token válido (auth != null)
        auth.createUserWithEmailAndPassword(email, password)
            .addOnCompleteListener(requireActivity()) { task ->
                if (task.isSuccessful) {
                    val uid = auth.currentUser?.uid ?: return@addOnCompleteListener
                    validarCedulaYGuardar(uid, email, fullName, idCard, cargo, institution)
                } else {
                    val exception = task.exception
                    if (exception is FirebaseAuthUserCollisionException) {
                        // Si ya existía la cuenta de Auth, autenticamos para verificar estado en DB
                        auth.signInWithEmailAndPassword(email, password).addOnCompleteListener { loginTask ->
                            if (loginTask.isSuccessful) {
                                val uid = auth.currentUser?.uid ?: return@addOnCompleteListener
                                validarCedulaYGuardar(uid, email, fullName, idCard, cargo, institution)
                            } else {
                                Toast.makeText(requireContext(), loginTask.exception?.message ?: "Error de autenticación", Toast.LENGTH_LONG).show()
                            }
                        }
                    } else {
                        Toast.makeText(requireContext(), exception?.message ?: "Error al registrar", Toast.LENGTH_LONG).show()
                    }
                }
            }
    }

    private fun validarCedulaYGuardar(
        uid: String,
        email: String,
        fullName: String,
        idCard: String,
        cargo: String,
        institution: String
    ) {
        // Con usuario autenticado, verificamos si está en authorized_cedulas
        database.reference.child("authorized_cedulas").get().addOnSuccessListener { snapshot ->
            if (_binding == null) return@addOnSuccessListener

            val hayPadron = snapshot.exists() && snapshot.childrenCount > 0L
            val estaAutorizada = if (hayPadron) {
                snapshot.hasChild(idCard) && (snapshot.child(idCard).child("active").value != false)
            } else {
                // Primer inicio del sistema sin cédulas registradas
                cargo == "Coordinador Distrital"
            }

            if (!estaAutorizada) {
                // Revertir registro si la cédula no consta en la lista distrital
                auth.currentUser?.delete()
                auth.signOut()
                Toast.makeText(requireContext(), getString(R.string.register_cedula_unauthorized), Toast.LENGTH_LONG).show()
                return@addOnSuccessListener
            }

            // Proceder a guardar el perfil en el nodo users
            guardarUsuarioEnDB(uid, email, fullName, idCard, cargo, institution)

        }.addOnFailureListener { e ->
            if (_binding == null) return@addOnFailureListener
            auth.signOut()
            Toast.makeText(requireContext(), "Error al validar autorización: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    private fun guardarUsuarioEnDB(
        uid: String,
        email: String,
        fullName: String,
        idCard: String,
        requestedCargo: String,
        institution: String
    ) {
        database.reference.child("users").get().addOnSuccessListener { usersSnapshot ->
            if (_binding == null) return@addOnSuccessListener

            // Es bootstrap únicamente si el nodo users no tiene registros
            val isBootstrap = !usersSnapshot.exists() || usersSnapshot.childrenCount == 0L

            val words = fullName.split("\\s+".toRegex())
            val initial1 = words.getOrNull(0)?.firstOrNull()?.toString() ?: ""
            val initial2 = words.getOrNull(1)?.firstOrNull()?.toString() ?: ""
            val customInitials = (initial1 + initial2).uppercase().ifEmpty { "U" }

            val requestedRoleCode = AppUtils.getRoleCode(requestedCargo)

            // Reglas de integridad forzadas:
            // Todo usuario nuevo es analista y no aprobado (false), a menos que sea el bootstrap inicial
            val isApproved = isBootstrap
            val finalRole = if (isBootstrap) "distrital" else "analista"
            val finalCargo = if (isBootstrap) "Coordinador Distrital" else "Analista DECE"

            val userMap = mapOf(
                "uid" to uid,
                "email" to email,
                "fullName" to fullName,
                "idCard" to idCard,
                "cargo" to finalCargo,
                "role" to finalRole,
                "requestedCargo" to requestedCargo,
                "requestedRole" to requestedRoleCode,
                "institution" to institution,
                "institutionKey" to AppUtils.getSafeKey(institution),
                "initials" to customInitials,
                "isRegistered" to true,
                "isApproved" to isApproved
            )

            database.reference.child("users").child(uid).setValue(userMap)
                .addOnCompleteListener { task ->
                    if (_binding == null) return@addOnCompleteListener

                    if (task.isSuccessful) {
                        if (isApproved) {
                            findNavController().navigate(R.id.action_RegisterFragment_to_SecondFragment)
                        } else {
                            findNavController().navigate(R.id.action_RegisterFragment_to_PendingApprovalFragment)
                        }
                    } else {
                        auth.signOut()
                        val error = task.exception?.message ?: getString(R.string.error_occurred)
                        Toast.makeText(requireContext(), getString(R.string.register_db_error, error), Toast.LENGTH_LONG).show()
                    }
                }
        }.addOnFailureListener {
            if (_binding == null) return@addOnFailureListener
            auth.signOut()
            Toast.makeText(requireContext(), "Error al verificar estructura de usuarios", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
