package com.example.decegestin

import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.core.content.edit
import androidx.fragment.app.Fragment
import com.example.decegestin.databinding.FragmentEducandoBinding
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.*
import com.skydoves.balloon.*

class EducandoFragment : Fragment() {

    private var _binding: FragmentEducandoBinding? = null
    private val binding get() = _binding!!

    private val auth by lazy { FirebaseAuth.getInstance() }
    private val database by lazy { FirebaseDatabase.getInstance().reference }
    private var currentInstitutionKey: String? = null

    private lateinit var adapter: EducandoSummaryAdapter
    private val records = mutableListOf<EducandoRecord>()

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentEducandoBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        binding.topBar.applyTopBarPadding()

        setupRecyclerView()
        loadInstitutionAndData()

        binding.menuIcon.setOnClickListener {
            (activity as? MainActivity)?.openDrawer()
        }

        binding.fabAddEducando.setOnClickListener {
            showAddRecordDialog()
        }

        binding.btnTutorial.setOnClickListener { startTutorial() }
        checkFirstTimeTutorial()
    }

    private fun checkFirstTimeTutorial() {
        val uid = auth.currentUser?.uid ?: return
        val prefs = requireContext().getSharedPreferences("EducandoTutorialPrefs_$uid", Context.MODE_PRIVATE)
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
            setText(getString(R.string.tut_educando_step1))
            setTextColorResource(R.color.white)
            setTextSize(14f)
            setBackgroundColorResource(R.color.md_theme_primary)
            setBalloonAnimation(BalloonAnimation.ELASTIC)
            setLifecycleOwner(viewLifecycleOwner)
            setDismissWhenClicked(true)
        }

        val b2 = createBalloon(requireContext()) {
            setArrowSize(10)
            setArrowOrientation(ArrowOrientation.BOTTOM)
            setArrowPosition(0.85f)
            setWidthRatio(0.75f)
            setHeight(BalloonSizeSpec.WRAP)
            setText(getString(R.string.tut_educando_step2))
            setTextColorResource(R.color.white)
            setTextSize(14f)
            setBackgroundColorResource(R.color.md_theme_primary)
            setBalloonAnimation(BalloonAnimation.ELASTIC)
            setLifecycleOwner(viewLifecycleOwner)
            setDismissWhenClicked(true)
        }

        b1.showAlignBottom(binding.rvEducandoSummary)
        b1.setOnBalloonDismissListener {
            if (_binding != null) b2.showAlignTop(binding.fabAddEducando)
        }
    }

    private fun setupRecyclerView() {
        adapter = EducandoSummaryAdapter(emptyList())
        binding.rvEducandoSummary.adapter = adapter
    }

    private fun loadInstitutionAndData() {
        val user = auth.currentUser ?: return
        database.child("users").child(user.uid).child("institution").get().addOnSuccessListener { snapshot ->
            if (_binding == null) return@addOnSuccessListener
            val instName = snapshot.value?.toString() ?: "General"
            currentInstitutionKey = AppUtils.getSafeKey(instName)
            listenForData()
        }
    }

    private fun listenForData() {
        val key = currentInstitutionKey ?: return
        database.child("institutions").child(key).child("educando")
            .addValueEventListener(object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    if (_binding == null) return
                    records.clear()
                    for (child in snapshot.children) {
                        val record = child.getValue(EducandoRecord::class.java)
                        if (record != null) records.add(record)
                    }
                    updateSummary()
                }

                override fun onCancelled(error: DatabaseError) {
                    Toast.makeText(context, getString(R.string.error_firebase, error.message), Toast.LENGTH_SHORT).show()
                }
            })
    }

    private fun updateSummary() {
        // Agrupar por nombre completo y año
        val grouped = records.groupBy { it.nombreCompleto to it.anio }
        val summaryList = grouped.map { (key, group) ->
            EducandoSummary(
                nombreCompleto = key.first,
                anio = key.second,
                calificacionTotal = group.sumOf { it.calificacion },
                count = group.size
            )
        }.sortedByDescending { it.anio }

        adapter.updateData(summaryList)
    }

    private fun showAddRecordDialog() {
        val dialogView = LayoutInflater.from(requireContext()).inflate(R.layout.dialog_add_educando, null)
        val dialog = MaterialAlertDialogBuilder(requireContext())
            .setView(dialogView)
            .create()

        val etPrimerApellido = dialogView.findViewById<EditText>(R.id.et_primer_apellido)
        val etSegundoApellido = dialogView.findViewById<EditText>(R.id.et_segundo_apellido)
        val etPrimerNombre = dialogView.findViewById<EditText>(R.id.et_primer_nombre)
        val etSegundoNombre = dialogView.findViewById<EditText>(R.id.et_segundo_nombre)
        val etAnio = dialogView.findViewById<EditText>(R.id.et_anio)
        val etCalificacion = dialogView.findViewById<EditText>(R.id.et_calificacion)
        val btnSave = dialogView.findViewById<Button>(R.id.btn_save_educando)

        btnSave.setOnClickListener {
            val pApe = AppUtils.sanitizeInput(etPrimerApellido.text.toString())
            val sApe = AppUtils.sanitizeInput(etSegundoApellido.text.toString())
            val pNom = AppUtils.sanitizeInput(etPrimerNombre.text.toString())
            val sNom = AppUtils.sanitizeInput(etSegundoNombre.text.toString())
            val anioStr = AppUtils.sanitizeInput(etAnio.text.toString())
            val califStr = AppUtils.sanitizeInput(etCalificacion.text.toString())

            if (pApe.isEmpty() || pNom.isEmpty() || anioStr.isEmpty() || califStr.isEmpty()) {
                Toast.makeText(context, "Por favor completa los campos principales", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            val record = EducandoRecord(
                id = database.push().key ?: "",
                primerApellido = pApe,
                segundoApellido = sApe,
                primerNombre = pNom,
                segundoNombre = sNom,
                anio = anioStr.toIntOrNull() ?: 0,
                calificacion = califStr.toIntOrNull() ?: 0,
                createdBy = auth.currentUser?.uid ?: ""
            )

            saveRecord(record, dialog)
        }

        dialog.show()
    }

    private fun saveRecord(record: EducandoRecord, dialog: AlertDialog) {
        val user = auth.currentUser ?: return
        if (!AppUtils.canMakeRequest(user.uid)) {
            Toast.makeText(context, getString(R.string.error_too_many_requests), Toast.LENGTH_SHORT).show()
            return
        }

        val key = currentInstitutionKey ?: return
        database.child("institutions").child(key).child("educando")
            .child(record.id)
            .setValue(record)
            .addOnSuccessListener {
                Toast.makeText(context, getString(R.string.success_record_saved), Toast.LENGTH_SHORT).show()
                dialog.dismiss()
            }
            .addOnFailureListener { e ->
                Toast.makeText(context, "Error: ${e.message}", Toast.LENGTH_SHORT).show()
            }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
