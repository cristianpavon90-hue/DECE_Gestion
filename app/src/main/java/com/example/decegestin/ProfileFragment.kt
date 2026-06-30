package com.example.decegestin

import android.os.Bundle
import androidx.fragment.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.graphics.Color
import android.widget.ArrayAdapter
import androidx.navigation.fragment.findNavController
import com.example.decegestin.databinding.FragmentProfileBinding
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.*
import com.skydoves.colorpickerview.listeners.ColorEnvelopeListener

/**
 * Pantalla de perfil de usuario para gestionar datos personales y personalización.
 */
class ProfileFragment : Fragment() {

    private var _binding: FragmentProfileBinding? = null
    private val binding get() = _binding!!
    
    private val auth by lazy { FirebaseAuth.getInstance() }
    private val database by lazy { FirebaseDatabase.getInstance().reference }
    private var selectedColorHex: String = "#0097A7"

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentProfileBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        
        setupUI()
        loadCurrentUserData()
    }

    private fun setupUI() {
        binding.backIcon.setOnClickListener { findNavController().navigateUp() }
        
        val user = auth.currentUser
        binding.editContactEmail.setText(user?.email)

        // Configurar Dropdown de Instituciones
        val adapter = ArrayAdapter(requireContext(), android.R.layout.simple_dropdown_item_1line, AppUtils.institutions)
        binding.editInstitution.setAdapter(adapter)

        // Configurar ColorPicker
        binding.colorPickerView.attachAlphaSlider(binding.alphaSlideBar)
        binding.colorPickerView.attachBrightnessSlider(binding.brightnessSlideBar)
        binding.colorPickerView.setColorListener(ColorEnvelopeListener { envelope, _ ->
            selectedColorHex = "#" + envelope.hexCode
            binding.colorPreview.setBackgroundColor(envelope.color)
        })

        binding.buttonUpdateProfile.setOnClickListener { validateAndUpdateProfile() }
    }

    private fun loadCurrentUserData() {
        val user = auth.currentUser ?: return
        database.child("users").child(user.uid).get().addOnSuccessListener { snapshot ->
            if (_binding != null && snapshot.exists()) {
                binding.apply {
                    editFullName.setText(snapshot.child("fullName").value?.toString())
                    editIdCard.setText(snapshot.child("idCard").value?.toString())
                    editInstitution.setText(snapshot.child("institution").value?.toString(), false)
                    editCanton.setText(snapshot.child("canton").value?.toString())
                    
                    val savedColor = snapshot.child("profileColor").value?.toString()
                    if (savedColor != null && savedColor.startsWith("#")) {
                        selectedColorHex = savedColor
                        try {
                            colorPreview.setBackgroundColor(Color.parseColor(savedColor))
                        } catch (e: Exception) {}
                    }
                }
            }
        }
    }

    private fun validateAndUpdateProfile() {
        val fullName = binding.editFullName.text.toString().trim()
        val idCard = binding.editIdCard.text.toString().trim()
        val institution = binding.editInstitution.text.toString()
        val canton = binding.editCanton.text.toString().trim()
        
        if (fullName.isNotEmpty() && idCard.isNotEmpty() && institution.isNotEmpty() && canton.isNotEmpty()) {
            val words = fullName.split("\\s+".toRegex())
            val initial1 = words.getOrNull(0)?.firstOrNull()?.toString() ?: ""
            val initial3 = words.getOrNull(2)?.firstOrNull()?.toString() ?: ""
            val customInitials = (initial1 + initial3).uppercase()

            val updates = mapOf(
                "fullName" to fullName,
                "idCard" to idCard,
                "institution" to institution,
                "canton" to canton,
                "profileColor" to selectedColorHex,
                "initials" to customInitials
            )
            
            val uid = auth.currentUser?.uid ?: return
            database.child("users").child(uid).updateChildren(updates)
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
