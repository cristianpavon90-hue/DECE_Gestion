package com.example.decegestin

import android.content.Context
import android.graphics.Color
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.LinearLayout
import androidx.core.content.edit
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import com.example.decegestin.databinding.FragmentDocsBinding
import com.example.decegestin.databinding.ItemPdfBinding
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.*
import com.skydoves.balloon.*

/**
 * Fragmento de Documentación de Apoyo gestionado mediante RBAC y permisos en la nube.
 */
class DocsFragment : Fragment() {

    private var _binding: FragmentDocsBinding? = null
    private val binding get() = _binding!!

    private val database = FirebaseDatabase.getInstance().reference
    private val auth = FirebaseAuth.getInstance()

    private var canManageDocs: Boolean = false
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
        binding.topBar.applyTopBarPadding()

        binding.menuIcon.setOnClickListener { (activity as? MainActivity)?.openDrawer() }
        setupUserAvatar()
        loadThumbnails()

        binding.btnOpenRutas.setOnClickListener { navigateToRepo("Rutas y Protocolos") }
        binding.btnOpenAcuerdos.setOnClickListener { navigateToRepo("Acuerdos") }
        binding.btnOpenPlantillas.setOnClickListener { navigateToRepo("Plantillas") }

        binding.btnAddFiles.setOnClickListener {
            if (canManageDocs) {
                showRepoSelectionDialog()
            } else {
                showToast(getString(R.string.permission_denied))
            }
        }

        fetchUserPermissions()

        binding.btnTutorial.setOnClickListener { startTutorial() }
        checkFirstTimeTutorial()
    }

    private fun checkFirstTimeTutorial() {
        val uid = auth.currentUser?.uid ?: return
        val prefs = requireContext().getSharedPreferences("DocsTutorialPrefs_$uid", Context.MODE_PRIVATE)
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
            setText(getString(R.string.tut_docs_step1))
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
            setText(getString(R.string.tut_docs_step2))
            setTextColorResource(R.color.white)
            setTextSize(14f)
            setBackgroundColorResource(R.color.md_theme_primary)
            setBalloonAnimation(BalloonAnimation.ELASTIC)
            setLifecycleOwner(viewLifecycleOwner)
            setDismissWhenClicked(true)
        }

        val b3 = createBalloon(requireContext()) {
            setArrowSize(10)
            setArrowOrientation(ArrowOrientation.BOTTOM)
            setArrowPosition(0.5f)
            setWidthRatio(0.8f)
            setHeight(BalloonSizeSpec.WRAP)
            setText(getString(R.string.tut_docs_step3))
            setTextColorResource(R.color.white)
            setTextSize(14f)
            setBackgroundColorResource(R.color.md_theme_primary)
            setBalloonAnimation(BalloonAnimation.ELASTIC)
            setLifecycleOwner(viewLifecycleOwner)
            setDismissWhenClicked(true)
        }

        b1.showAlignBottom(binding.btnOpenRutas)
        b1.setOnBalloonDismissListener {
            if (_binding != null) b2.showAlignBottom(binding.containerRutas)
        }
        b2.setOnBalloonDismissListener {
            if (_binding != null) {
                if (binding.btnAddFiles.visibility == View.VISIBLE) {
                    b3.showAlignTop(binding.btnAddFiles)
                }
            }
        }
    }

    private fun fetchUserPermissions() {
        val user = auth.currentUser ?: return
        database.child("users").child(user.uid).get().addOnSuccessListener { snapshot ->
            if (_binding == null) return@addOnSuccessListener
            val role = snapshot.child("role").value?.toString() ?: ""
            val cargo = snapshot.child("cargo").value?.toString() ?: ""
            canManageDocs = role == "admin" || role == "distrital" || role == "institucional" || cargo.contains("Coordinador", ignoreCase = true)
            binding.btnAddFiles.visibility = if (canManageDocs) View.VISIBLE else View.GONE
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
                } catch (_: Exception) {}
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
                        val docName = doc.child("name").value?.toString() ?: getString(R.string.docs_unnamed)
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
            .setNegativeButton(R.string.cancel, null)
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
                val name = AppUtils.sanitizeInput(inputName.text.toString())
                val url = AppUtils.sanitizeInput(inputUrl.text.toString())
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
        val user = auth.currentUser ?: return
        if (!AppUtils.canMakeRequest(user.uid)) {
            showToast(getString(R.string.error_too_many_requests))
            return
        }

        val ref = database.child("documentation").child(repo).push()
        val data = mapOf("name" to name, "url" to url)
        ref.setValue(data).addOnSuccessListener {
            showToast(getString(R.string.docs_save_success))
            loadThumbnails()
        }
    }

    private fun showDeleteConfirmDialog(category: String, docId: String, docName: String) {
        if (!canManageDocs) {
            showToast(getString(R.string.permission_denied))
            return
        }

        MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.docs_delete_title)
            .setMessage("¿Estás seguro de que deseas eliminar el documento \"$docName\"?")
            .setPositiveButton(R.string.delete) { _, _ ->
                database.child("documentation").child(category).child(docId).removeValue()
                    .addOnSuccessListener {
                        showToast(getString(R.string.docs_deleted_success))
                        loadThumbnails()
                    }
                    .addOnFailureListener { e ->
                        showToast("Error: ${e.message}")
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
