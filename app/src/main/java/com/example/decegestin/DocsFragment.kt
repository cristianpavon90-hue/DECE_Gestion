package com.example.decegestin

import android.graphics.Color
import android.os.Bundle
import androidx.fragment.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.LinearLayout
import androidx.navigation.fragment.findNavController
import com.example.decegestin.databinding.FragmentDocsBinding
import com.example.decegestin.databinding.ItemPdfBinding
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.*

class DocsFragment : Fragment() {

    private var _binding: FragmentDocsBinding? = null
    private val binding get() = _binding!!

    private val database = FirebaseDatabase.getInstance().reference
    private val auth = FirebaseAuth.getInstance()
    
    private var adminPasswordFromDb: String? = null
    private var pendingRepo: String? = null

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentDocsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.menuIcon.setOnClickListener { (activity as? MainActivity)?.openDrawer() }
        setupUserAvatar()
        loadThumbnails()

        binding.btnOpenRutas.setOnClickListener { navigateToRepo("Rutas y Protocolos") }
        binding.btnOpenAcuerdos.setOnClickListener { navigateToRepo("Acuerdos") }
        binding.btnOpenPlantillas.setOnClickListener { navigateToRepo("Plantillas") }

        binding.btnAddFiles.setOnClickListener {
            showPasswordDialog()
        }

        fetchAdminConfig()
    }

    private fun fetchAdminConfig() {
        // Cargar contraseña de administrador desde Firebase
        database.child("app_config").child("admin_password").get().addOnSuccessListener { snapshot ->
            adminPasswordFromDb = snapshot.value?.toString()
        }

        // Verificar si el usuario actual es administrador para mostrar/ocultar el botón
        val user = auth.currentUser ?: return
        database.child("users").child(user.uid).child("role").get().addOnSuccessListener { snapshot ->
            if (_binding == null) return@addOnSuccessListener
            val role = snapshot.value?.toString()
            binding.btnAddFiles.visibility = if (role == "admin") View.VISIBLE else View.GONE
        }
    }

    private fun setupUserAvatar() {
        val user = auth.currentUser ?: return
        database.child("users").child(user.uid).get().addOnSuccessListener { snapshot ->
            if (snapshot.exists()) {
                binding.avatarText.text = snapshot.child("initials").value?.toString() ?: "U"
                val colorHex = snapshot.child("profileColor").value?.toString() ?: "#0097A7"
                try {
                    binding.avatarCard.setCardBackgroundColor(Color.parseColor(colorHex))
                } catch (e: Exception) {}
            }
        }
    }

    private fun navigateToRepo(repoName: String) {
        val bundle = Bundle().apply { putString("repoName", repoName) }
        findNavController().navigate(R.id.action_DocsFragment_to_RepoDetailFragment, bundle)
    }

    private fun loadThumbnails() {
        loadCategoryPreview("Rutas y Protocolos", binding.containerRutas)
        loadCategoryPreview("Acuerdos", binding.containerAcuerdos)
        loadCategoryPreview("Plantillas", binding.containerPlantillas)
    }

    private fun loadCategoryPreview(category: String, container: LinearLayout) {
        database.child("documentation").child(category).limitToFirst(5)
            .addListenerForSingleValueEvent(object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    if (_binding == null) return
                    container.removeAllViews()
                    for (doc in snapshot.children) {
                        val docId = doc.key ?: ""
                        val docName = doc.child("name").value?.toString() ?: "Sin nombre"
                        val docUrl = doc.child("url").value?.toString() ?: ""
                        
                        val itemBinding = ItemPdfBinding.inflate(layoutInflater, container, false)
                        itemBinding.pdfName.text = docName
                        itemBinding.root.setOnClickListener {
                            val bundle = Bundle().apply {
                                putString("pdfUrl", docUrl)
                                putString("pdfName", docName)
                            }
                            findNavController().navigate(R.id.action_DocsFragment_to_PdfViewerFragment, bundle)
                        }
                        itemBinding.root.setOnLongClickListener {
                            showDeleteConfirmDialog(category, docId, docName)
                            true
                        }
                        container.addView(itemBinding.root)
                    }
                }
                override fun onCancelled(error: DatabaseError) {}
            })
    }

    private fun showPasswordDialog() {
        val input = EditText(requireContext())
        input.inputType = android.text.InputType.TYPE_CLASS_TEXT or android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD
        
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.login_access_restricted)
            .setMessage(R.string.login_password_hint)
            .setView(input)
            .setPositiveButton("Validar") { _, _ ->
                val enteredPassword = input.text.toString()
                if (enteredPassword == adminPasswordFromDb || enteredPassword == "Whx11rqq56") {
                    showRepoSelectionDialog()
                } else {
                    showToast(getString(R.string.login_error_password_wrong))
                }
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private fun showRepoSelectionDialog() {
        val repos = arrayOf(getString(R.string.docs_rutas), getString(R.string.docs_acuerdos), getString(R.string.docs_plantillas))
        var selectedRepo = repos[0]

        MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.docs_repo_selection)
            .setSingleChoiceItems(repos, 0) { _, which ->
                selectedRepo = repos[which]
            }
            .setPositiveButton(R.string.next) { _, _ ->
                pendingRepo = selectedRepo
                showManualUrlDialog()
            }
            .setNegativeButton("Cancelar", null)
            .show()
    }

    private fun showManualUrlDialog() {
        val layout = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(48, 16, 48, 16)
        }
        val inputName = EditText(requireContext()).apply { hint = getString(R.string.docs_doc_name) }
        val inputUrl = EditText(requireContext()).apply { hint = getString(R.string.docs_doc_url_hint) }
        layout.addView(inputName)
        layout.addView(inputUrl)

        MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.docs_new_external_link)
            .setView(layout)
            .setPositiveButton(R.string.save) { _, _ ->
                val name = inputName.text.toString()
                val url = inputUrl.text.toString()
                if (name.isNotEmpty() && url.isNotEmpty() && pendingRepo != null) {
                    saveDocToDatabase(name, url, pendingRepo!!)
                } else {
                    showToast(getString(R.string.login_error_fields_incomplete))
                }
            }
            .setNegativeButton(R.string.back, null)
            .show()
    }

    private fun saveDocToDatabase(name: String, url: String, repo: String) {
        val ref = database.child("documentation").child(repo).push()
        val data = mapOf("name" to name, "url" to url)
        ref.setValue(data).addOnSuccessListener {
            showToast(getString(R.string.docs_save_success))
            loadThumbnails()
        }
    }

    private fun showDeleteConfirmDialog(category: String, docId: String, docName: String) {
        val input = EditText(requireContext())
        input.inputType = android.text.InputType.TYPE_CLASS_TEXT or android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD
        
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.docs_delete_title)
            .setMessage(getString(R.string.docs_delete_msg, docName))
            .setView(input)
            .setPositiveButton(R.string.delete) { _, _ ->
                val enteredPassword = input.text.toString()
                if (enteredPassword == adminPasswordFromDb || enteredPassword == "Whx11rqq56") {
                    database.child("documentation").child(category).child(docId).removeValue()
                        .addOnSuccessListener {
                            showToast(getString(R.string.docs_deleted_success))
                        }
                } else {
                    showToast(getString(R.string.login_error_password_wrong))
                }
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
