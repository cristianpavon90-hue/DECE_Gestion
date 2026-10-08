package com.example.decegestin

import android.graphics.Color
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.decegestin.databinding.FragmentDistritalRiskDetailBinding
import com.example.decegestin.databinding.ItemDistritalStatBinding
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.*

/**
 * Fragmento de Desglose Institucional: Muestra el conteo de un Riesgo Psicosocial o Socialización por cada institución (solo >= 1 caso).
 */
class DistritalRiskDetailFragment : Fragment() {

    private var _binding: FragmentDistritalRiskDetailBinding? = null
    private val binding get() = _binding!!

    private val database = FirebaseDatabase.getInstance().reference
    private val auth = FirebaseAuth.getInstance()

    private var riskTitle: String = ""
    private var filterType: String = "riesgos"
    private val instBreakdownList = mutableListOf<Pair<String, Int>>()
    private lateinit var adapter: BreakdownAdapter

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentDistritalRiskDetailBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        binding.topBar.applyTopBarPadding()

        riskTitle = arguments?.getString("riskTitle") ?: "Riesgo Psicosocial"
        filterType = arguments?.getString("filterType") ?: "riesgos"

        binding.textRiskTitleTop.text = riskTitle
        binding.backIcon.setOnClickListener { findNavController().navigateUp() }

        setupRecyclerView()
        setupUserAvatar()
        loadBreakdownData()

        binding.avatarCard.setOnClickListener { startTutorial() }
    }

    private fun startTutorial() {
        val b1 = com.skydoves.balloon.createBalloon(requireContext()) {
            setArrowSize(10)
            setArrowOrientation(com.skydoves.balloon.ArrowOrientation.TOP)
            setArrowPosition(0.5f)
            setWidthRatio(0.85f)
            setHeight(com.skydoves.balloon.BalloonSizeSpec.WRAP)
            setText("Esta pantalla desglosa el número de casos en cada escuela. Se muestran únicamente las escuelas con 1 o más registros.")
            setTextColorResource(R.color.white)
            setTextSize(14f)
            setBackgroundColorResource(R.color.md_theme_primary)
            setBalloonAnimation(com.skydoves.balloon.BalloonAnimation.ELASTIC)
            setLifecycleOwner(viewLifecycleOwner)
            setDismissWhenClicked(true)
        }
        b1.showAlignBottom(binding.rvInstBreakdown)
    }

    private fun setupRecyclerView() {
        adapter = BreakdownAdapter()
        binding.rvInstBreakdown.layoutManager = GridLayoutManager(requireContext(), 2)
        binding.rvInstBreakdown.adapter = adapter
    }

    private fun setupUserAvatar() {
        val user = auth.currentUser ?: return
        database.child("users").child(user.uid).get().addOnSuccessListener { snapshot ->
            if (_binding == null || !snapshot.exists()) return@addOnSuccessListener
            binding.avatarText.text = snapshot.child("initials").value?.toString() ?: "U"
            val colorHex = snapshot.child("profileColor").value?.toString() ?: "#0097A7"
            try {
                binding.avatarCard.setCardBackgroundColor(Color.parseColor(colorHex))
            } catch (_: Exception) {}
        }
    }

    private fun loadBreakdownData() {
        binding.progressBreakdown.visibility = View.VISIBLE

        database.child("institutions").addValueEventListener(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                if (_binding == null) return
                binding.progressBreakdown.visibility = View.GONE
                instBreakdownList.clear()

                val allowedIndividualRisks = setOf(
                    "violencia sexual", "violencia física", "violencia psicológica",
                    "acoso escolar", "negligencia", "consumo de sustancias licitas",
                    "consumo de sustancias ilícitas", "suicidio", "desapariciones",
                    "embarazo", "maternidad", "paternidad", "trabajo infantil",
                    "tráfico ilícito", "trata de personas"
                )

                val excludedRisks = setOf("necesidades educativas", "en proceso")

                val allowedSocializations = setOf(
                    "rutas y protocolos de violencia", "rutas violencia física",
                    "rutas violencia psicológica", "socialización violencia sexual",
                    "recorrido participativo", "rutas acoso escolar",
                    "rutas de uso y consumo de drogas", "rutas trabajo infantil",
                    "rutas de trabajo infantil", "rutas desapariciones",
                    "rutas de desapariciones", "rutas y protocolos de embarazo, maternidad paternidad",
                    "rutas suicidio e intentos autolíticos", "rutas maternidad o paternidad temprana",
                    "rutas trata de personas", "rutas tráfico ilícito de migrantes",
                    "rutas violencia digital", "socialización eneis"
                )

                for (instChild in snapshot.children) {
                    val rawInstKey = instChild.key ?: continue
                    val displayName = rawInstKey.replace("_", ".")

                    var count = 0

                    if (filterType == "riesgos") {
                        val formsSnapshot = instChild.child("forms")
                        for (formChild in formsSnapshot.children) {
                            val caseType = formChild.child("caseType").value?.toString()?.trim() ?: continue
                            val lowerType = caseType.lowercase()

                            if (excludedRisks.contains(lowerType)) continue

                            if (riskTitle == "Otras atenciones") {
                                if (!allowedIndividualRisks.contains(lowerType)) {
                                    count++
                                }
                            } else {
                                if (lowerType == riskTitle.trim().lowercase()) {
                                    count++
                                }
                            }
                        }
                    } else {
                        // Socializaciones
                        val informesSnapshot = instChild.child("categories").child("Informes Técnicos")
                        for (informeChild in informesSnapshot.children) {
                            val tipoInforme = informeChild.child("tipoInforme").value?.toString()?.trim() ?: continue
                            val lowerTipo = tipoInforme.lowercase()

                            if (riskTitle == "Otros") {
                                if (!allowedSocializations.contains(lowerTipo)) {
                                    count++
                                }
                            } else {
                                if (lowerTipo == riskTitle.trim().lowercase() || lowerTipo.contains(riskTitle.trim().lowercase())) {
                                    count++
                                }
                            }
                        }
                    }

                    // Regla clave: La institución APARECE SOLO SI REGISTRA 1 O MÁS CASOS
                    if (count >= 1) {
                        instBreakdownList.add(Pair(displayName, count))
                    }
                }

                binding.textEmptyBreakdown.visibility = if (instBreakdownList.isEmpty()) View.VISIBLE else View.GONE
                adapter.notifyDataSetChanged()
            }

            override fun onCancelled(error: DatabaseError) {
                if (_binding == null) return
                binding.progressBreakdown.visibility = View.GONE
            }
        })
    }

    inner class BreakdownAdapter : RecyclerView.Adapter<BreakdownAdapter.ViewHolder>() {
        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val b = ItemDistritalStatBinding.inflate(LayoutInflater.from(parent.context), parent, false)
            return ViewHolder(b)
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            val (instName, count) = instBreakdownList[position]
            holder.b.textStatTitle.text = instName
            holder.b.textStatCount.text = String.format("%04d", count)

            holder.b.root.setOnClickListener {
                val bundle = Bundle().apply {
                    putString("title", "Informes Técnicos")
                    putString("institution", instName)
                }
                try {
                    findNavController().navigate(R.id.DetailFragment, bundle)
                } catch (_: Exception) {}
            }
        }

        override fun getItemCount() = instBreakdownList.size

        inner class ViewHolder(val b: ItemDistritalStatBinding) : RecyclerView.ViewHolder(b.root)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
