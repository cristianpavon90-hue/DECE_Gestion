package com.example.decegestin

import android.app.Activity
import android.os.Bundle
import androidx.fragment.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import androidx.activity.result.contract.ActivityResultContracts
import androidx.navigation.fragment.findNavController
import com.example.decegestin.databinding.FragmentRegisterBinding
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInClient
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.ApiException
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.database.FirebaseDatabase

/**
 * Pantalla de registro de nuevos usuarios.
 */
class RegisterFragment : Fragment() {

    private var _binding: FragmentRegisterBinding? = null
    private val binding get() = _binding!!

    private val auth by lazy { FirebaseAuth.getInstance() }
    private val database by lazy { FirebaseDatabase.getInstance() }
    private lateinit var googleSignInClient: GoogleSignInClient

    private val googleSignInLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val task = GoogleSignIn.getSignedInAccountFromIntent(result.data)
            try {
                val account = task.getResult(ApiException::class.java)!!
                firebaseAuthWithGoogle(account.idToken!!)
            } catch (e: ApiException) {
                showToast(getString(R.string.login_error_auth) + ": ${e.message}")
            }
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentRegisterBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        setupUI()
        setupGoogleSignIn()
    }

    private fun setupUI() {
        binding.backIcon.setOnClickListener { findNavController().navigateUp() }

        // Configurar Dropdown de Instituciones
        val adapter = ArrayAdapter(requireContext(), android.R.layout.simple_dropdown_item_1line, AppUtils.institutions)
        binding.editInstitution.setAdapter(adapter)

        binding.buttonRegisterEmail.setOnClickListener {
            val email = binding.editEmail.text.toString().trim()
            val password = binding.editPassword.text.toString().trim()
            val institution = binding.editInstitution.text.toString()

            if (email.isNotEmpty() && password.isNotEmpty() && institution.isNotEmpty()) {
                registerWithEmail(email, password, institution)
            } else {
                showToast(getString(R.string.login_error_fields))
            }
        }

        binding.buttonRegisterGoogle.setOnClickListener {
            val institution = binding.editInstitution.text.toString()
            if (institution.isNotEmpty()) {
                val signInIntent = googleSignInClient.signInIntent
                googleSignInLauncher.launch(signInIntent)
            } else {
                showToast(getString(R.string.register_error_inst))
            }
        }
    }

    private fun setupGoogleSignIn() {
        val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestIdToken(getString(R.string.default_web_client_id))
            .requestEmail()
            .build()
        googleSignInClient = GoogleSignIn.getClient(requireActivity(), gso)
    }

    private fun registerWithEmail(email: String, password: String, institution: String) {
        auth.createUserWithEmailAndPassword(email, password)
            .addOnCompleteListener(requireActivity()) { task ->
                if (task.isSuccessful) {
                    saveUserToDatabase(auth.currentUser?.uid ?: "", email, institution)
                } else {
                    val exception = task.exception
                    if (exception is com.google.firebase.auth.FirebaseAuthUserCollisionException) {
                        // El usuario ya existe en Auth, pero no en la base de datos (por eso llegó aquí)
                        // Intentamos loguear para obtener el UID y guardar en DB
                        auth.signInWithEmailAndPassword(email, password).addOnCompleteListener { loginTask ->
                            if (loginTask.isSuccessful) {
                                saveUserToDatabase(auth.currentUser?.uid ?: "", email, institution)
                            } else {
                                showToast("${getString(R.string.error_occurred)}: ${loginTask.exception?.message}")
                            }
                        }
                    } else {
                        showToast("${getString(R.string.error_occurred)}: ${exception?.message}")
                    }
                }
            }
    }

    private fun firebaseAuthWithGoogle(idToken: String) {
        val credential = GoogleAuthProvider.getCredential(idToken, null)
        auth.signInWithCredential(credential)
            .addOnCompleteListener(requireActivity()) { task ->
                if (task.isSuccessful) {
                    val user = auth.currentUser
                    val institution = binding.editInstitution.text.toString()
                    saveUserToDatabase(user?.uid ?: "", user?.email ?: "", institution)
                } else {
                    showToast(getString(R.string.login_error_auth))
                }
            }
    }

    private fun saveUserToDatabase(uid: String, email: String, institution: String) {
        val userMap = mapOf(
            "uid" to uid,
            "email" to email,
            "institution" to institution,
            "isRegistered" to true
        )
        database.reference.child("users").child(uid).setValue(userMap)
            .addOnCompleteListener { task ->
                if (task.isSuccessful) {
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
