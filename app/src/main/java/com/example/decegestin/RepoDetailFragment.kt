package com.example.decegestin

import android.os.Bundle
import androidx.fragment.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.decegestin.databinding.FragmentRepoDetailBinding
import com.example.decegestin.databinding.ItemPdfBinding
import com.google.firebase.database.*

class RepoDetailFragment : Fragment() {

    private var _binding: FragmentRepoDetailBinding? = null
    private val binding get() = _binding!!

    private var repoName = ""
    private val database = FirebaseDatabase.getInstance().reference
    private val docsList = mutableListOf<Map<String, String>>()
    private var docsListener: ValueEventListener? = null

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentRepoDetailBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        binding.topBar.applyTopBarPadding()

        repoName = arguments?.getString("repoName") ?: "Repositorio"
        binding.repoTitleTop.text = repoName

        binding.backIcon.setOnClickListener { findNavController().navigateUp() }

        setupRecyclerView()
        loadDocs()
    }

    private fun setupRecyclerView() {
        binding.recyclerRepoDocs.layoutManager = GridLayoutManager(context, 3)
        binding.recyclerRepoDocs.adapter = object : RecyclerView.Adapter<RepoViewHolder>() {
            override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RepoViewHolder {
                val b = ItemPdfBinding.inflate(LayoutInflater.from(parent.context), parent, false)
                return RepoViewHolder(b)
            }

            override fun onBindViewHolder(holder: RepoViewHolder, position: Int) {
                val doc = docsList[position]
                holder.bind(doc)
            }

            override fun getItemCount(): Int = docsList.size
        }
    }

    inner class RepoViewHolder(private val b: ItemPdfBinding) : RecyclerView.ViewHolder(b.root) {
        fun bind(doc: Map<String, String>) {
            b.pdfName.text = doc["name"]
            b.root.setOnClickListener {
                val bundle = Bundle().apply {
                    putString("pdfUrl", doc["url"])
                    putString("pdfName", doc["name"])
                }
                findNavController().navigate(R.id.action_RepoDetailFragment_to_PdfViewerFragment, bundle)
            }
            b.root.setOnLongClickListener {
                showDeleteConfirmDialog(doc["id"] ?: "", doc["name"] ?: "")
                true
            }
        }
    }

    private fun showDeleteConfirmDialog(docId: String, docName: String) {
        val input = android.widget.EditText(requireContext())
        input.inputType = android.text.InputType.TYPE_CLASS_TEXT or android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD
        
        com.google.android.material.dialog.MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.docs_delete_title)
            .setMessage(getString(R.string.docs_delete_msg, docName))
            .setView(input)
            .setPositiveButton(R.string.delete) { _, _ ->
                if (input.text.toString() == "Whx11rqq56") {
                    database.child("documentation").child(repoName).child(docId).removeValue()
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

    private fun loadDocs() {
        docsListener = database.child("documentation").child(repoName).addValueEventListener(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                docsList.clear()
                for (doc in snapshot.children) {
                    val id = doc.key ?: ""
                    val name = doc.child("name").value?.toString() ?: "Sin nombre"
                    val url = doc.child("url").value?.toString() ?: ""
                    docsList.add(mapOf("id" to id, "name" to name, "url" to url))
                }
                binding.recyclerRepoDocs.adapter?.notifyDataSetChanged()
            }
            override fun onCancelled(error: DatabaseError) {}
        })
    }

    override fun onDestroyView() {
        super.onDestroyView()
        docsListener?.let { database.child("documentation").child(repoName).removeEventListener(it) }
        _binding = null
    }
}
