package com.example.decegestin

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import com.example.decegestin.databinding.FragmentGeneracionReportesBinding
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener

/**
 * Fragmento para la Generación y Exportación de Reportes Consolidados del Coordinador Distrital.
 */
class GeneracionReportesFragment : Fragment() {

    private var _binding: FragmentGeneracionReportesBinding? = null
    private val binding get() = _binding!!

    private val firebaseDb by lazy { FirebaseDatabase.getInstance() }
    private val reportTypes = arrayOf(
        "Consolidado de Riesgos Psicosociales",
        "Casos de Violencia Sexual",
        "Socializaciones e Informes",
        "Derivaciones MSP y UDAI"
    )

    private var currentReportText = "Selecciona los parámetros y presiona 'Generar Consolidado' para obtener el reporte."

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentGeneracionReportesBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        binding.topBar.applyTopBarPadding()

        binding.backIcon.setOnClickListener { findNavController().navigateUp() }

        setupDropdowns()

        binding.btnGeneratePreview.setOnClickListener { generateReport() }
        binding.btnShareReport.setOnClickListener { shareReport() }

        binding.textReportPreview.text = currentReportText
        binding.btnTutorial.setOnClickListener { startTutorial() }
    }

    private fun startTutorial() {
        val b1 = com.skydoves.balloon.createBalloon(requireContext()) {
            setArrowSize(10)
            setArrowOrientation(com.skydoves.balloon.ArrowOrientation.TOP)
            setArrowPosition(0.5f)
            setWidthRatio(0.85f)
            setHeight(com.skydoves.balloon.BalloonSizeSpec.WRAP)
            setText("Selecciona la institución y el tipo de reporte, luego presiona 'Generar Consolidado' para procesar los datos.")
            setTextColorResource(R.color.white)
            setTextSize(14f)
            setBackgroundColorResource(R.color.md_theme_primary)
            setBalloonAnimation(com.skydoves.balloon.BalloonAnimation.ELASTIC)
            setLifecycleOwner(viewLifecycleOwner)
            setDismissWhenClicked(true)
        }
        b1.showAlignBottom(binding.layoutFilterInst)
    }

    private fun setupDropdowns() {
        // Dropdown de Tipos de Reporte
        val typeAdapter = ArrayAdapter(requireContext(), android.R.layout.simple_dropdown_item_1line, reportTypes)
        binding.editFilterType.setAdapter(typeAdapter)
        binding.editFilterType.setText(reportTypes[0], false)
        binding.editFilterType.setOnClickListener { binding.editFilterType.showDropDown() }

        // Dropdown de Instituciones
        AppUtils.loadInstitutions(firebaseDb) { instList ->
            if (_binding == null) return@loadInstitutions
            val fullList = mutableListOf("Todas las instituciones")
            fullList.addAll(instList)

            val instAdapter = ArrayAdapter(requireContext(), android.R.layout.simple_dropdown_item_1line, fullList)
            binding.editFilterInst.setAdapter(instAdapter)
            binding.editFilterInst.setText(fullList[0], false)
            binding.editFilterInst.setOnClickListener { binding.editFilterInst.showDropDown() }
        }
    }

    private fun generateReport() {
        val selectedInst = binding.editFilterInst.text.toString()
        val selectedType = binding.editFilterType.text.toString()

        Toast.makeText(context, "Generando reporte...", Toast.LENGTH_SHORT).show()

        firebaseDb.reference.child("institutions").addListenerForSingleValueEvent(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                if (_binding == null) return

                val sb = StringBuilder()
                sb.append("=========================================\n")
                sb.append("REPORTE CONSOLIDADO DE GESTIÓN DECE\n")
                sb.append("Tipo: $selectedType\n")
                sb.append("Institución: $selectedInst\n")
                sb.append("=========================================\n\n")

                if (selectedType == "Consolidado de Riesgos Psicosociales") {
                    val caseTypeCounts = mutableMapOf<String, Int>()
                    var totalForms = 0

                    for (instChild in snapshot.children) {
                        val instName = instChild.key ?: ""
                        if (selectedInst != "Todas las instituciones" && AppUtils.getSafeKey(selectedInst) != instName) {
                            continue
                        }

                        val formsSnapshot = instChild.child("forms")
                        for (formChild in formsSnapshot.children) {
                            val caseType = formChild.child("caseType").value?.toString()?.trim() ?: continue
                            caseTypeCounts[caseType] = (caseTypeCounts[caseType] ?: 0) + 1
                            totalForms++
                        }
                    }

                    sb.append("TOTAL CASOS REGISTRADOS: $totalForms\n\n")
                    sb.append("DESGLOSE POR TIPO DE RIESGO:\n")
                    if (caseTypeCounts.isEmpty()) {
                        sb.append("  Sin registros encontrados.\n")
                    } else {
                        caseTypeCounts.forEach { (type, count) ->
                            sb.append("  - $type: $count casos\n")
                        }
                    }

                } else if (selectedType == "Casos de Violencia Sexual") {
                    var countSexual = 0
                    for (instChild in snapshot.children) {
                        val instName = instChild.key ?: ""
                        if (selectedInst != "Todas las instituciones" && AppUtils.getSafeKey(selectedInst) != instName) {
                            continue
                        }

                        val formsSnapshot = instChild.child("forms")
                        for (formChild in formsSnapshot.children) {
                            val caseType = formChild.child("caseType").value?.toString() ?: ""
                            if (caseType == "Violencia sexual") {
                                countSexual++
                                val student = formChild.child("studentName").value?.toString() ?: "Estudiante"
                                val grado = formChild.child("grado").value?.toString() ?: ""
                                val paralelo = formChild.child("paralelo").value?.toString() ?: ""
                                val date = formChild.child("date").value?.toString() ?: ""
                                sb.append("● Estudiante: $student ($grado \"$paralelo\") | Fecha: $date\n")
                            }
                        }
                    }
                    sb.insert(sb.indexOf("\n\n") + 2, "TOTAL CASOS DE VIOLENCIA SEXUAL: $countSexual\n\n")

                } else {
                    sb.append("Resumen consolidado generado con éxito para $selectedType.\n")
                    sb.append("Instancias procesadas: ${snapshot.childrenCount} instituciones.\n")
                }

                currentReportText = sb.toString()
                binding.textReportPreview.text = currentReportText
            }

            override fun onCancelled(error: DatabaseError) {
                if (_binding == null) return
                Toast.makeText(context, "Error al consultar datos: ${error.message}", Toast.LENGTH_SHORT).show()
            }
        })
    }

    private fun shareReport() {
        val shareIntent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_SUBJECT, "Reporte Consolidado DECE")
            putExtra(Intent.EXTRA_TEXT, currentReportText)
        }
        startActivity(Intent.createChooser(shareIntent, "Compartir Reporte Consolidado"))
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
