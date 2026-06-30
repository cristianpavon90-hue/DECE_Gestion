package com.example.decegestin

import android.os.Bundle
import androidx.fragment.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import android.widget.PopupMenu
import androidx.fragment.app.viewModels
import androidx.navigation.fragment.findNavController
import com.example.decegestin.databinding.FragmentSecondBinding
import com.google.firebase.auth.FirebaseAuth

/**
 * Dashboard principal de la aplicación que muestra contadores y categorías.
 */
class SecondFragment : Fragment() {

    private var _binding: FragmentSecondBinding? = null
    private val binding get() = _binding!!
    
    private val viewModel: DashboardViewModel by viewModels()
    private val auth: FirebaseAuth by lazy { FirebaseAuth.getInstance() }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentSecondBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        setItemsEnabled(false)
        setupClickListeners()
        observeViewModel()
        
        viewModel.startListening(requireContext())
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

        binding.avatarText.setOnClickListener { view -> showAvatarMenu(view) }
        binding.avatarCard.setOnClickListener { view -> showAvatarMenu(view) }
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
        val user = auth.currentUser
        
        val institution = viewModel.institution.value ?: "No asignada"
        
        popup.menu.apply {
            add("Cuentas").isEnabled = false
            add("  ${user?.email}")
            add("Equipo").isEnabled = false
            add("  $institution")
            
            add(getString(R.string.profile_title)).setOnMenuItemClickListener {
                findNavController().navigate(R.id.action_SecondFragment_to_ProfileFragment)
                true
            }
            add(getString(R.string.change_password_title)).setOnMenuItemClickListener {
                findNavController().navigate(R.id.action_SecondFragment_to_ChangePasswordFragment)
                true
            }
            add("Cerrar sesión").setOnMenuItemClickListener {
                auth.signOut()
                findNavController().navigate(R.id.action_SecondFragment_logout)
                true
            }
        }
        popup.show()
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

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
