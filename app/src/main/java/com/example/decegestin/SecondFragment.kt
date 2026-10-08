package com.example.decegestin

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.fragment.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.TextView
import android.widget.PopupMenu
import androidx.core.content.edit
import androidx.fragment.app.viewModels
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.decegestin.databinding.FragmentSecondBinding
import com.example.decegestin.databinding.ItemDistritalStatBinding
import com.google.firebase.auth.FirebaseAuth
import com.skydoves.balloon.*

/**
 * Dashboard principal de la aplicación con soporte para usuarios de Institución y Coordinador Distrital (RBAC).
 */
class SecondFragment : Fragment() {

    private var _binding: FragmentSecondBinding? = null
    private val binding get() = _binding!!

    private val viewModel: DashboardViewModel by viewModels()
    private val auth: FirebaseAuth by lazy { FirebaseAuth.getInstance() }

    private var currentFilterPosition = 0
    private lateinit var distritalAdapter: DistritalStatAdapter

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentSecondBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        binding.topBar.applyTopBarPadding()

        setItemsEnabled(false)
        setupClickListeners()
        observeViewModel()

        viewModel.startListening(requireContext())

        binding.btnTutorial.setOnClickListener { startTutorial() }
        checkFirstTimeTutorial()
    }

    private fun checkFirstTimeTutorial() {
        val uid = auth.currentUser?.uid ?: return
        val prefs = requireContext().getSharedPreferences("DashboardTutorialPrefs_$uid", Context.MODE_PRIVATE)
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
            setArrowPosition(0.2f)
            setWidthRatio(0.75f)
            setHeight(BalloonSizeSpec.WRAP)
            setText(getString(R.string.tut_dashboard_step1))
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
            setArrowPosition(0.7f)
            setWidthRatio(0.75f)
            setHeight(BalloonSizeSpec.WRAP)
            setText(getString(R.string.tut_dashboard_step2))
            setTextColorResource(R.color.white)
            setTextSize(14f)
            setBackgroundColorResource(R.color.md_theme_primary)
            setBalloonAnimation(BalloonAnimation.ELASTIC)
            setLifecycleOwner(viewLifecycleOwner)
            setDismissWhenClicked(true)
        }

        val b3 = createBalloon(requireContext()) {
            setArrowSize(10)
            setArrowOrientation(ArrowOrientation.TOP)
            setArrowPosition(0.85f)
            setWidthRatio(0.75f)
            setHeight(BalloonSizeSpec.WRAP)
            setText(getString(R.string.tut_dashboard_step3))
            setTextColorResource(R.color.white)
            setTextSize(14f)
            setBackgroundColorResource(R.color.md_theme_primary)
            setBalloonAnimation(BalloonAnimation.ELASTIC)
            setLifecycleOwner(viewLifecycleOwner)
            setDismissWhenClicked(true)
        }

        val b4 = createBalloon(requireContext()) {
            setArrowSize(10)
            setArrowOrientation(ArrowOrientation.TOP)
            setArrowPosition(0.5f)
            setWidthRatio(0.8f)
            setHeight(BalloonSizeSpec.WRAP)
            setText(getString(R.string.tut_dashboard_step4))
            setTextColorResource(R.color.white)
            setTextSize(14f)
            setBackgroundColorResource(R.color.md_theme_primary)
            setBalloonAnimation(BalloonAnimation.ELASTIC)
            setLifecycleOwner(viewLifecycleOwner)
            setDismissWhenClicked(true)
        }

        b1.showAlignBottom(binding.menuIcon)
        b1.setOnBalloonDismissListener {
            if (_binding != null) b2.showAlignBottom(binding.btnQuickAdd)
        }
        b2.setOnBalloonDismissListener {
            if (_binding != null) b3.showAlignBottom(binding.avatarCard)
        }
        b3.setOnBalloonDismissListener {
            if (_binding != null) {
                if (binding.layoutStandardDashboard.visibility == View.VISIBLE) {
                    b4.showAlignBottom(binding.itemOficios)
                } else {
                    b4.showAlignBottom(binding.layoutDistritalFilter)
                }
            }
        }
    }

    private fun observeViewModel() {
        viewModel.userInitials.observe(viewLifecycleOwner) { initials ->
            binding.avatarText.text = initials
        }

        viewModel.userColor.observe(viewLifecycleOwner) { color ->
            binding.avatarCard.setCardBackgroundColor(color)
        }

        viewModel.institution.observe(viewLifecycleOwner) { inst ->
            setItemsEnabled(inst != null)
        }

        viewModel.categoryStats.observe(viewLifecycleOwner) { stats ->
            updateCategoryView(stats["Oficios"], binding.lastMarkedOficios, binding.totalMarkedOficios)
            updateCategoryView(stats["Informes Técnicos"], binding.lastMarkedInformes, binding.totalMarkedInformes)
            updateCategoryView(stats["Reporte de Violencia"], binding.lastMarkedReporte, binding.totalMarkedReporte)
            updateCategoryView(stats["Derivación MSP"], binding.lastMarkedMsp, binding.totalMarkedMsp)
            updateCategoryView(stats["Derivación UDAI"], binding.lastMarkedUdai, binding.totalMarkedUdai)
        }

        viewModel.isDistrital.observe(viewLifecycleOwner) { isDistrital ->
            if (isDistrital) {
                binding.layoutStandardDashboard.visibility = View.GONE
                binding.layoutDistritalDashboard.visibility = View.VISIBLE
                setupDistritalUI()
            } else {
                binding.layoutStandardDashboard.visibility = View.VISIBLE
                binding.layoutDistritalDashboard.visibility = View.GONE
            }
        }

        viewModel.distritalRiesgos.observe(viewLifecycleOwner) { list ->
            if (viewModel.isDistrital.value == true && currentFilterPosition == 0) {
                distritalAdapter.updateData(list)
                binding.textDistritalEmpty.visibility = if (list.isEmpty()) View.VISIBLE else View.GONE
            }
        }

        viewModel.distritalSocializaciones.observe(viewLifecycleOwner) { list ->
            if (viewModel.isDistrital.value == true && currentFilterPosition == 1) {
                distritalAdapter.updateData(list)
                binding.textDistritalEmpty.visibility = if (list.isEmpty()) View.VISIBLE else View.GONE
            }
        }
    }

    private fun setupDistritalUI() {
        val filterOptions = arrayOf(
            getString(R.string.distrital_filter_riesgos),
            getString(R.string.distrital_filter_socializaciones)
        )

        val adapter = ArrayAdapter(requireContext(), android.R.layout.simple_dropdown_item_1line, filterOptions)
        binding.editDistritalFilter.setAdapter(adapter)
        binding.editDistritalFilter.setText(filterOptions[currentFilterPosition], false)
        binding.editDistritalFilter.setOnClickListener { binding.editDistritalFilter.showDropDown() }

        binding.editDistritalFilter.setOnItemClickListener { _, _, position, _ ->
            currentFilterPosition = position
            refreshDistritalStats()
        }

        distritalAdapter = DistritalStatAdapter()
        binding.rvDistritalStats.layoutManager = GridLayoutManager(requireContext(), 2)
        binding.rvDistritalStats.adapter = distritalAdapter

        refreshDistritalStats()
    }

    private fun refreshDistritalStats() {
        val list = if (currentFilterPosition == 0) {
            viewModel.distritalRiesgos.value ?: emptyList()
        } else {
            viewModel.distritalSocializaciones.value ?: emptyList()
        }
        distritalAdapter.updateData(list)
        binding.textDistritalEmpty.visibility = if (list.isEmpty()) View.VISIBLE else View.GONE
    }

    private fun setupClickListeners() {
        binding.itemOficios.setOnClickListener { navigateToDetail("Oficios") }
        binding.itemInformes.setOnClickListener { navigateToDetail("Informes Técnicos") }
        binding.itemReporte.setOnClickListener { navigateToDetail("Reporte de Violencia") }
        binding.itemDerivacionMsp.setOnClickListener { navigateToDetail("Derivación MSP") }
        binding.itemDerivacionUdai.setOnClickListener { navigateToDetail("Derivación UDAI") }

        binding.menuIcon.setOnClickListener {
            (activity as? MainActivity)?.openDrawer()
        }

        binding.btnQuickAdd.setOnClickListener {
            try {
                findNavController().navigate(R.id.action_SecondFragment_to_NewFormFragment)
            } catch (_: Exception) {}
        }

        binding.avatarText.setOnClickListener { view -> showAvatarMenu(view) }
        binding.avatarCard.setOnClickListener { view -> showAvatarMenu(view) }

        binding.btnFeedback.setOnClickListener {
            val intent = Intent(Intent.ACTION_SENDTO).apply {
                data = Uri.parse("mailto:cristian.pavon90@gmail.com")
                putExtra(Intent.EXTRA_SUBJECT, getString(R.string.feedback_subject))
                putExtra(Intent.EXTRA_TEXT, "Escriba aquí su sugerencia o detalle del fallo encontrado:\n\n")
            }
            try {
                startActivity(Intent.createChooser(intent, "Enviar correo a Soporte..."))
            } catch (e: Exception) {
                showToast("No se encontró una aplicación de correo instalada")
            }
        }
    }

    private fun setItemsEnabled(enabled: Boolean) {
        val views = listOf(
            binding.itemOficios, binding.itemInformes, binding.itemReporte,
            binding.itemDerivacionMsp, binding.itemDerivacionUdai
        )
        val alpha = if (enabled) 1.0f else 0.5f
        views.forEach {
            it.isEnabled = enabled
            it.alpha = alpha
        }
    }

    private fun showAvatarMenu(view: View) {
        val popup = PopupMenu(requireContext(), view)
        val user = auth.currentUser ?: return

        val institution = viewModel.institution.value ?: getString(R.string.profile_not_assigned)

        com.google.firebase.database.FirebaseDatabase.getInstance().reference.child("users").child(user.uid).get().addOnSuccessListener { snapshot ->
            if (_binding == null) return@addOnSuccessListener
            val realRole = snapshot.child("role").value?.toString() ?: ""

            popup.menu.apply {
                add(getString(R.string.profile_accounts)).isEnabled = false
                add("  ${user.email}")
                add(getString(R.string.profile_team)).isEnabled = false
                add("  $institution")

                if (realRole == "admin") {
                    val currentSim = AppUtils.getSimulatedRole(requireContext())
                    val simLabel = if (currentSim != null) " [$currentSim]" else ""
                    add("${getString(R.string.role_simulator_title)}$simLabel").setOnMenuItemClickListener {
                        AppUtils.showRoleSimulationDialog(requireContext(), realRole) {
                            viewModel.startListening(requireContext())
                        }
                        true
                    }
                }

                add(getString(R.string.profile_title)).setOnMenuItemClickListener {
                    findNavController().navigate(R.id.action_SecondFragment_to_ProfileFragment)
                    true
                }
                add(getString(R.string.change_password_title)).setOnMenuItemClickListener {
                    findNavController().navigate(R.id.action_SecondFragment_to_ChangePasswordFragment)
                    true
                }
                add(getString(R.string.profile_logout)).setOnMenuItemClickListener {
                    auth.signOut()
                    findNavController().navigate(R.id.action_SecondFragment_logout)
                    true
                }
            }
            popup.show()
        }
    }

    private fun updateCategoryView(pair: Pair<Int, Int>?, lastText: TextView, totalText: TextView) {
        val (lastMarked, total) = pair ?: Pair(0, 0)
        lastText.text = String.format("%03d", lastMarked)
        totalText.text = getString(R.string.marked_label, total)
    }

    private fun navigateToDetail(title: String) {
        val inst = viewModel.institution.value
        if (inst == null) {
            showToast(getString(R.string.loading_inst))
            return
        }
        val bundle = Bundle().apply {
            putString("title", title)
            putString("institution", inst)
        }
        findNavController().navigate(R.id.action_SecondFragment_to_DetailFragment, bundle)
    }

    inner class DistritalStatAdapter(
        private var items: List<Pair<String, Int>> = emptyList()
    ) : RecyclerView.Adapter<DistritalStatAdapter.ViewHolder>() {

        fun updateData(newItems: List<Pair<String, Int>>) {
            items = newItems
            notifyDataSetChanged()
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val b = ItemDistritalStatBinding.inflate(LayoutInflater.from(parent.context), parent, false)
            return ViewHolder(b)
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            val (title, count) = items[position]
            holder.b.textStatTitle.text = title
            holder.b.textStatCount.text = String.format("%04d", count)

            holder.b.root.setOnClickListener {
                val bundle = Bundle().apply {
                    putString("riskTitle", title)
                    putString("filterType", if (currentFilterPosition == 0) "riesgos" else "socializaciones")
                }
                try {
                    findNavController().navigate(R.id.action_SecondFragment_to_DistritalRiskDetailFragment, bundle)
                } catch (_: Exception) {}
            }
        }

        override fun getItemCount(): Int = items.size

        inner class ViewHolder(val b: ItemDistritalStatBinding) : RecyclerView.ViewHolder(b.root)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
