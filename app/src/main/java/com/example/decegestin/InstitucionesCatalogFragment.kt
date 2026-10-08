package com.example.decegestin

import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.decegestin.databinding.FragmentInstitucionesCatalogBinding
import com.example.decegestin.databinding.ItemInstitucionCardBinding
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.firebase.database.FirebaseDatabase

/**
 * Fragmento dedicado al Catálogo de Instituciones y Circuitos para el Coordinador Distrital.
 */
class InstitucionesCatalogFragment : Fragment() {

    private var _binding: FragmentInstitucionesCatalogBinding? = null
    private val binding get() = _binding!!

    private val firebaseDb by lazy { FirebaseDatabase.getInstance() }
    private val allInstitutions = mutableListOf<String>()
    private val filteredInstitutions = mutableListOf<String>()
    private lateinit var adapter: InstitucionesAdapter

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentInstitucionesCatalogBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        binding.topBar.applyTopBarPadding()

        binding.backIcon.setOnClickListener { findNavController().navigateUp() }

        setupRecyclerView()
        loadInstitutions()

        binding.editSearchInst.addTextChangedListener(object : TextWatcher {
            override fun afterTextChanged(s: Editable?) {
                filterInstitutions(s?.toString() ?: "")
            }
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
        })

        binding.fabAddInstitution.setOnClickListener {
            showAddInstitutionDialog()
        }

        binding.btnTutorial.setOnClickListener { startTutorial() }
    }

    private fun startTutorial() {
        val b1 = com.skydoves.balloon.createBalloon(requireContext()) {
            setArrowSize(10)
            setArrowOrientation(com.skydoves.balloon.ArrowOrientation.TOP)
            setArrowPosition(0.5f)
            setWidthRatio(0.85f)
            setHeight(com.skydoves.balloon.BalloonSizeSpec.WRAP)
            setText("Busca instituciones o circuitos y usa el botón (+) para agregar nuevas escuelas al catálogo del distrito.")
            setTextColorResource(R.color.white)
            setTextSize(14f)
            setBackgroundColorResource(R.color.md_theme_primary)
            setBalloonAnimation(com.skydoves.balloon.BalloonAnimation.ELASTIC)
            setLifecycleOwner(viewLifecycleOwner)
            setDismissWhenClicked(true)
        }
        b1.showAlignBottom(binding.editSearchInst)
    }

    private fun setupRecyclerView() {
        adapter = InstitucionesAdapter()
        binding.recyclerInstituciones.layoutManager = LinearLayoutManager(requireContext())
        binding.recyclerInstituciones.adapter = adapter
    }

    private fun loadInstitutions() {
        binding.progressInstituciones.visibility = View.VISIBLE
        AppUtils.loadInstitutions(firebaseDb) { list ->
            if (_binding == null) return@loadInstitutions
            binding.progressInstituciones.visibility = View.GONE
            allInstitutions.clear()
            allInstitutions.addAll(list)
            filterInstitutions(binding.editSearchInst.text?.toString() ?: "")
        }
    }

    private fun filterInstitutions(query: String) {
        val q = query.trim().lowercase()
        filteredInstitutions.clear()
        if (q.isEmpty()) {
            filteredInstitutions.addAll(allInstitutions)
        } else {
            filteredInstitutions.addAll(allInstitutions.filter { it.lowercase().contains(q) })
        }
        binding.textEmptyInstituciones.visibility = if (filteredInstitutions.isEmpty()) View.VISIBLE else View.GONE
        adapter.notifyDataSetChanged()
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
                    AppUtils.addInstitution(firebaseDb, name) { success, errorMsg ->
                        if (success) {
                            Toast.makeText(context, "Institución agregada exitosamente", Toast.LENGTH_SHORT).show()
                            loadInstitutions()
                        } else {
                            Toast.makeText(context, "Error: ${errorMsg ?: "No se pudo guardar"}", Toast.LENGTH_SHORT).show()
                        }
                    }
                }
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private fun showDeleteConfirmDialog(instName: String) {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.distrital_delete_institution)
            .setMessage("¿Estás seguro de que deseas eliminar '$instName' del catálogo institucional?")
            .setPositiveButton(R.string.delete) { _, _ ->
                AppUtils.deleteInstitution(firebaseDb, instName) { success, errorMsg ->
                    if (success) {
                        Toast.makeText(context, "Institución eliminada exitosamente", Toast.LENGTH_SHORT).show()
                        loadInstitutions()
                    } else {
                        Toast.makeText(context, "Error: ${errorMsg ?: "No se pudo eliminar"}", Toast.LENGTH_SHORT).show()
                    }
                }
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    inner class InstitucionesAdapter : RecyclerView.Adapter<InstitucionesAdapter.ViewHolder>() {
        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val b = ItemInstitucionCardBinding.inflate(LayoutInflater.from(parent.context), parent, false)
            return ViewHolder(b)
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            val instName = filteredInstitutions[position]
            holder.b.textInstName.text = instName
            val safeKey = AppUtils.getSafeKey(instName)
            holder.b.textInstCircuito.text = "Código/Clave DB: $safeKey"

            holder.b.btnDeleteInst.setOnClickListener {
                showDeleteConfirmDialog(instName)
            }
        }

        override fun getItemCount() = filteredInstitutions.size

        inner class ViewHolder(val b: ItemInstitucionCardBinding) : RecyclerView.ViewHolder(b.root)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
