package com.example.decegestin

import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.AutoCompleteTextView
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.decegestin.databinding.FragmentProfesionalesApprovalBinding
import com.example.decegestin.databinding.ItemProfesionalCardBinding
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.tabs.TabLayout
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.*
import com.skydoves.balloon.*

/**
 * Pantalla dedicada para la Aprobación y Gestión de Profesionales por el Coordinador Distrital.
 */
class ProfesionalesApprovalFragment : Fragment() {

    private var _binding: FragmentProfesionalesApprovalBinding? = null
    private val binding get() = _binding!!

    private val database = FirebaseDatabase.getInstance().reference
    private val auth = FirebaseAuth.getInstance()

    private val allPendingUsers = mutableListOf<Map<String, Any>>()
    private val allApprovedUsers = mutableListOf<Map<String, Any>>()
    private val displayUsers = mutableListOf<Map<String, Any>>()

    private lateinit var adapter: ProfesionalesAdapter
    private var currentTab = 0 // 0: Pendientes, 1: Aprobados

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentProfesionalesApprovalBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        binding.topBar.applyTopBarPadding()

        binding.backIcon.setOnClickListener { findNavController().navigateUp() }

        setupRecyclerView()
        loadUsersData()

        binding.tabLayoutProfesionales.addOnTabSelectedListener(object : TabLayout.OnTabSelectedListener {
            override fun onTabSelected(tab: TabLayout.Tab?) {
                currentTab = tab?.position ?: 0
                filterData(binding.editSearchProfesionales.text?.toString() ?: "")
            }
            override fun onTabUnselected(tab: TabLayout.Tab?) {}
            override fun onTabReselected(tab: TabLayout.Tab?) {}
        })

        binding.editSearchProfesionales.addTextChangedListener(object : TextWatcher {
            override fun afterTextChanged(s: Editable?) {
                filterData(s?.toString() ?: "")
            }
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
        })

        binding.btnTutorial.setOnClickListener { startTutorial() }
    }

    private fun setupRecyclerView() {
        adapter = ProfesionalesAdapter()
        binding.recyclerProfesionales.layoutManager = LinearLayoutManager(requireContext())
        binding.recyclerProfesionales.adapter = adapter
    }

    private fun loadUsersData() {
        binding.progressProfesionales.visibility = View.VISIBLE
        database.child("users").addValueEventListener(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                if (_binding == null) return
                binding.progressProfesionales.visibility = View.GONE
                allPendingUsers.clear()
                allApprovedUsers.clear()

                for (child in snapshot.children) {
                    val userMap = child.value as? Map<String, Any> ?: continue
                    val mutable = userMap.toMutableMap()
                    mutable["uid"] = child.key ?: ""
                    val isApproved = userMap["isApproved"] as? Boolean ?: false

                    if (!isApproved) {
                        allPendingUsers.add(mutable)
                    } else {
                        allApprovedUsers.add(mutable)
                    }
                }

                filterData(binding.editSearchProfesionales.text?.toString() ?: "")
            }

            override fun onCancelled(error: DatabaseError) {
                if (_binding == null) return
                binding.progressProfesionales.visibility = View.GONE
            }
        })
    }

    private fun filterData(query: String) {
        val rawList = if (currentTab == 0) allPendingUsers else allApprovedUsers
        val q = query.trim().lowercase()

        displayUsers.clear()
        if (q.isEmpty()) {
            displayUsers.addAll(rawList)
        } else {
            displayUsers.addAll(rawList.filter { u ->
                val name = u["fullName"]?.toString()?.lowercase() ?: ""
                val idCard = u["idCard"]?.toString()?.lowercase() ?: ""
                val inst = u["institution"]?.toString()?.lowercase() ?: ""
                name.contains(q) || idCard.contains(q) || inst.contains(q)
            })
        }

        binding.textEmptyProfesionales.visibility = if (displayUsers.isEmpty()) View.VISIBLE else View.GONE
        adapter.notifyDataSetChanged()
    }

    private fun showUserManageDialog(userMap: Map<String, Any>) {
        val targetUid = userMap["uid"]?.toString() ?: return
        val name = userMap["fullName"]?.toString() ?: "Usuario"
        val idCard = userMap["idCard"]?.toString() ?: ""
        val cargo = userMap["requestedCargo"]?.toString() ?: userMap["cargo"]?.toString() ?: "Analista DECE"
        val inst = userMap["institution"]?.toString() ?: ""

        val dialogView = LayoutInflater.from(requireContext()).inflate(R.layout.dialog_validate_user, null)
        val textTitle = dialogView.findViewById<android.widget.TextView>(R.id.text_dialog_title)
        val textDetails = dialogView.findViewById<android.widget.TextView>(R.id.text_user_details)
        val editSelectCargo = dialogView.findViewById<AutoCompleteTextView>(R.id.edit_select_cargo)
        val btnApprove = dialogView.findViewById<com.google.android.material.button.MaterialButton>(R.id.btn_approve_account)
        val btnRevoke = dialogView.findViewById<com.google.android.material.button.MaterialButton>(R.id.btn_revoke_account)
        val btnReject = dialogView.findViewById<com.google.android.material.button.MaterialButton>(R.id.btn_reject_account)

        val isPending = currentTab == 0
        textTitle.text = if (isPending) "Validar Cuenta de Usuario" else "Gestionar Cuenta Aprobada"
        textDetails.text = "Nombre: $name\nCédula: $idCard\nInstitución: $inst\nCargo Solicitado/Actual: $cargo"

        val cargoAdapter = ArrayAdapter(requireContext(), android.R.layout.simple_dropdown_item_1line, AppUtils.cargos)
        editSelectCargo.setAdapter(cargoAdapter)
        editSelectCargo.setText(cargo, false)
        editSelectCargo.setOnClickListener { editSelectCargo.showDropDown() }

        if (!isPending) {
            btnApprove.text = "Actualizar Cargo / Rol"
            btnRevoke.visibility = View.VISIBLE
        }

        var dialog: androidx.appcompat.app.AlertDialog? = null

        btnApprove.setOnClickListener {
            val selectedCargo = editSelectCargo.text.toString()
            val selectedRoleCode = AppUtils.getRoleCode(selectedCargo)
            approveUserInFirebase(targetUid, idCard, selectedCargo, selectedRoleCode, inst)
            dialog?.dismiss()
        }

        btnRevoke.setOnClickListener {
            database.child("users").child(targetUid).child("isApproved").setValue(false).addOnSuccessListener {
                AppUtils.revokeCedula(FirebaseDatabase.getInstance(), idCard) { _, _ -> }
                Toast.makeText(context, "Aprobación revocada", Toast.LENGTH_SHORT).show()
            }
            dialog?.dismiss()
        }

        btnReject.setOnClickListener {
            database.child("users").child(targetUid).removeValue().addOnSuccessListener {
                if (idCard.isNotEmpty()) AppUtils.revokeCedula(FirebaseDatabase.getInstance(), idCard) { _, _ -> }
                Toast.makeText(context, "Usuario eliminado", Toast.LENGTH_SHORT).show()
            }
            dialog?.dismiss()
        }

        dialog = MaterialAlertDialogBuilder(requireContext())
            .setView(dialogView)
            .setNegativeButton(R.string.cancel, null)
            .create()

        dialog.show()
    }

    private fun approveUserInFirebase(targetUid: String, idCard: String, cargo: String, role: String, inst: String) {
        val adminUid = auth.currentUser?.uid ?: ""
        val updates = mapOf<String, Any>(
            "isApproved" to true,
            "cargo" to cargo,
            "role" to role,
            "approvedBy" to adminUid,
            "approvedAt" to ServerValue.TIMESTAMP
        )

        database.child("users").child(targetUid).updateChildren(updates).addOnSuccessListener {
            AppUtils.authorizeCedula(FirebaseDatabase.getInstance(), idCard, cargo, inst, adminUid) { _, _ -> }
            Toast.makeText(context, "Usuario validado exitosamente como $cargo", Toast.LENGTH_SHORT).show()
        }
    }

    private fun startTutorial() {
        val b1 = createBalloon(requireContext()) {
            setArrowSize(10)
            setArrowOrientation(ArrowOrientation.TOP)
            setArrowPosition(0.5f)
            setWidthRatio(0.8f)
            setHeight(BalloonSizeSpec.WRAP)
            setText("Usa las pestañas para alternar entre solicitudes pendientes de aprobación y cuentas ya activas.")
            setTextColorResource(R.color.white)
            setTextSize(14f)
            setBackgroundColorResource(R.color.md_theme_primary)
            setBalloonAnimation(BalloonAnimation.ELASTIC)
            setLifecycleOwner(viewLifecycleOwner)
            setDismissWhenClicked(true)
        }
        b1.showAlignBottom(binding.tabLayoutProfesionales)
    }

    inner class ProfesionalesAdapter : RecyclerView.Adapter<ProfesionalesAdapter.ViewHolder>() {
        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val b = ItemProfesionalCardBinding.inflate(LayoutInflater.from(parent.context), parent, false)
            return ViewHolder(b)
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            val u = displayUsers[position]
            val name = u["fullName"]?.toString() ?: "Sin nombre"
            val idCard = u["idCard"]?.toString() ?: ""
            val inst = u["institution"]?.toString() ?: ""
            val cargo = u["requestedCargo"]?.toString() ?: u["cargo"]?.toString() ?: "Analista DECE"

            holder.b.textProfName.text = name
            holder.b.textProfIdCard.text = "Cédula: $idCard"
            holder.b.textProfInstitution.text = "Institución: $inst"
            holder.b.textProfCargo.text = "Cargo: $cargo"

            if (currentTab == 0) {
                holder.b.btnProfActionPrimary.text = "Aprobar"
                holder.b.btnProfActionSecondary.text = "Cambiar Rol"
            } else {
                holder.b.btnProfActionPrimary.text = "Gestionar"
                holder.b.btnProfActionSecondary.text = "Revocar"
            }

            holder.b.btnProfActionPrimary.setOnClickListener { showUserManageDialog(u) }
            holder.b.btnProfActionSecondary.setOnClickListener { showUserManageDialog(u) }
            holder.b.root.setOnClickListener { showUserManageDialog(u) }
        }

        override fun getItemCount() = displayUsers.size

        inner class ViewHolder(val b: ItemProfesionalCardBinding) : RecyclerView.ViewHolder(b.root)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
