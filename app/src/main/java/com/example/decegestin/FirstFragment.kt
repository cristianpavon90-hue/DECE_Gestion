package com.example.decegestin

import android.app.Activity
import android.os.Bundle
import androidx.fragment.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.activity.result.contract.ActivityResultContracts
import androidx.navigation.fragment.findNavController
import com.example.decegestin.databinding.FragmentFirstBinding
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInClient
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.ApiException
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.database.FirebaseDatabase
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.example.decegestin.databinding.DialogLoginBinding

/**
 * Fragmento de inicio que maneja la autenticación y el acceso principal.
 */
class FirstFragment : Fragment() {

    private var _binding: FragmentFirstBinding? = null
    private val binding get() = _binding!!
    
    private val auth: FirebaseAuth by lazy { FirebaseAuth.getInstance() }
    private val database: FirebaseDatabase by lazy { FirebaseDatabase.getInstance() }
    private lateinit var googleSignInClient: GoogleSignInClient

    private val signInLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val task = GoogleSignIn.getSignedInAccountFromIntent(result.data)
            try {
                val account = task.getResult(ApiException::class.java)!!
                firebaseAuthWithGoogle(account.idToken!!)
            } catch (e: ApiException) {
                showToast("${getString(R.string.login_error_auth)}: ${e.message}")
                setLoading(false)
            }
        } else {
            setLoading(false)
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentFirstBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        setupGoogleSignIn()

        // Si ya hay un usuario logueado, verificamos su registro
        if (auth.currentUser != null) {
            checkUserRegistration(auth.currentUser?.uid ?: "")
        }

        binding.buttonIngresar.setOnClickListener {
            showLoginDialog()
        }

        binding.textRegistrar.setOnClickListener {
            findNavController().navigate(R.id.action_FirstFragment_to_RegisterFragment)
        }

        binding.helpContainer.setOnClickListener {
            showHelpDialog()
        }
    }

    private fun setupGoogleSignIn() {
        val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestIdToken(getString(R.string.default_web_client_id))
            .requestEmail()
            .build()
        googleSignInClient = GoogleSignIn.getClient(requireActivity(), gso)
    }

    private fun setLoading(isLoading: Boolean) {
        binding.progressBar.visibility = if (isLoading) View.VISIBLE else View.GONE
        binding.buttonIngresar.isEnabled = !isLoading
        binding.textRegistrar.isEnabled = !isLoading
    }

    private fun showHelpDialog() {
        val dialogView = layoutInflater.inflate(R.layout.dialog_help, null)
        val dialog = MaterialAlertDialogBuilder(requireContext())
            .setView(dialogView)
            .create()

        dialogView.findViewById<View>(R.id.button_close_help).setOnClickListener {
            dialog.dismiss()
        }

        dialog.show()
    }

    private fun showLoginDialog() {
        val dialogBinding = DialogLoginBinding.inflate(layoutInflater)
        val dialog = MaterialAlertDialogBuilder(requireContext())
            .setView(dialogBinding.root)
            .create()

        dialogBinding.buttonLoginEmail.setOnClickListener {
            val email = dialogBinding.editLoginEmail.text.toString().trim()
            val password = dialogBinding.editLoginPassword.text.toString().trim()

            if (email.isNotEmpty() && password.isNotEmpty()) {
                setLoading(true)
                auth.signInWithEmailAndPassword(email, password)
                    .addOnCompleteListener { task ->
                        if (task.isSuccessful) {
                            dialog.dismiss()
                            checkUserRegistration(auth.currentUser?.uid ?: "")
                        } else {
                            setLoading(false)
                            showToast("${getString(R.string.login_error_auth)}: ${task.exception?.message}")
                        }
                    }
            } else {
                showToast(getString(R.string.login_error_fields))
            }
        }

        dialogBinding.buttonLoginGoogle.setOnClickListener {
            dialog.dismiss()
            setLoading(true)
            val signInIntent = googleSignInClient.signInIntent
            signInLauncher.launch(signInIntent)
        }

        dialog.show()
    }

    private fun firebaseAuthWithGoogle(idToken: String) {
        val credential = GoogleAuthProvider.getCredential(idToken, null)
        auth.signInWithCredential(credential)
            .addOnCompleteListener(requireActivity()) { task ->
                if (task.isSuccessful) {
                    checkUserRegistration(auth.currentUser?.uid ?: "")
                } else {
                    setLoading(false)
                    showToast(getString(R.string.login_error_auth))
                }
            }
    }

    private fun checkUserRegistration(uid: String) {
        setLoading(true)
        database.reference.child("users").child(uid).get()
            .addOnSuccessListener { snapshot ->
                if (snapshot.exists()) {
                    navigateToDashboard()
                } else {
                    setLoading(false)
                    showToast(getString(R.string.user_not_registered))
                    findNavController().navigate(R.id.action_FirstFragment_to_RegisterFragment)
                }
            }
            .addOnFailureListener { e ->
                setLoading(false)
                val errorMsg = e.message ?: ""
                if (errorMsg.contains("Permission denied", ignoreCase = true)) {
                    showToast(getString(R.string.permission_denied))
                } else {
                    showToast("${getString(R.string.error_occurred)}: $errorMsg")
                }
            }
    }

    private fun navigateToDashboard() {
        val mainActivity = activity as? MainActivity
        // Si el MainActivity consume un intent pendiente del Widget, no navegamos al Dashboard normal
        if (mainActivity?.tryConsumePendingWidgetIntent() == true) {
            return
        }
        findNavController().navigate(R.id.action_FirstFragment_to_SecondFragment)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
