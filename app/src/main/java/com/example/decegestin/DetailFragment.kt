package com.example.decegestin

import android.graphics.Color
import android.os.Bundle
import androidx.fragment.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.GridLayoutManager
import com.example.decegestin.databinding.FragmentDetailBinding
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.*

/**
 * Fragmento que muestra la cuadrícula de números marcados para una categoría específica.
 */
class DetailFragment : Fragment() {

    private var _binding: FragmentDetailBinding? = null
    private val binding get() = _binding!!
    
    private var currentPage = 0
    private val pageSize = 100
    private var categoryTitle = ""
    private var institution = ""
    
    private val database: DatabaseReference by lazy { FirebaseDatabase.getInstance().reference }
    private val auth: FirebaseAuth by lazy { FirebaseAuth.getInstance() }
    
    private val checkedData = mutableMapOf<Int, String>()
    private var detailListener: ValueEventListener? = null
    private var userListener: ValueEventListener? = null
    
    private var currentUserColor: String = "#0097A7"

    private lateinit var adapter: NumberAdapter

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentDetailBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        categoryTitle = arguments?.getString("title") ?: "Detalle"
        institution = arguments?.getString("institution") ?: ""
        binding.detailTitle.text = categoryTitle

        setupRecyclerView()
        setupUserAvatar()
        loadDataFromFirebase()

        binding.backIcon.setOnClickListener { findNavController().navigateUp() }
        binding.btnNext.setOnClickListener { changePage(1) }
        binding.btnPrev.setOnClickListener { changePage(-1) }
    }

    private fun changePage(delta: Int) {
        currentPage += delta
        updatePage()
    }

    private fun setupUserAvatar() {
        val user = auth.currentUser ?: return
        userListener = database.child("users").child(user.uid).addValueEventListener(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                if (_binding == null || !snapshot.exists()) return
                
                binding.avatarText.text = snapshot.child("initials").value?.toString() ?: "U"
                val colorHex = snapshot.child("profileColor").value?.toString() ?: "#0097A7"
                currentUserColor = colorHex
                
                try {
                    binding.avatarCard.setCardBackgroundColor(Color.parseColor(colorHex))
                } catch (e: Exception) {}
            }
            override fun onCancelled(error: DatabaseError) {}
        })
    }

    private fun setupRecyclerView() {
        adapter = NumberAdapter { number, isChecked ->
            updateNumberStatus(number, isChecked)
        }
        binding.numbersRecycler.apply {
            layoutManager = GridLayoutManager(context, 5)
            adapter = this@DetailFragment.adapter
        }
    }

    private fun loadDataFromFirebase() {
        if (institution.isEmpty()) return
        val safeInstKey = AppUtils.getSafeKey(institution)
        
        detailListener = database.child("institutions").child(safeInstKey).child("categories").child(categoryTitle)
            .addValueEventListener(object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    if (_binding == null) return
                    checkedData.clear()
                    for (child in snapshot.children) {
                        val num = child.key?.toIntOrNull()
                        val color = child.child("color").value?.toString()
                        if (num != null && color != null) {
                            checkedData[num] = color
                        }
                    }
                    updatePage()
                }

                override fun onCancelled(error: DatabaseError) {
                    if (error.code == DatabaseError.PERMISSION_DENIED) {
                        showToast(getString(R.string.permission_denied))
                    }
                }
            })
    }

    private fun updatePage() {
        val start = (currentPage * pageSize) + 1
        val end = (currentPage + 1) * pageSize
        binding.pageIndicator.text = getString(R.string.page_range, start, end)
        
        val numbers = (start..end).toList()
        val currentColors = checkedData.filterKeys { it in start..end }
        
        adapter.setData(numbers, currentColors)
        
        binding.btnPrev.visibility = if (currentPage > 0) View.VISIBLE else View.GONE
        
        val countOnPage = currentColors.size
        binding.countText.text = getString(R.string.page_count, countOnPage, pageSize)
        binding.btnNext.visibility = if (countOnPage == pageSize && currentPage < 3) View.VISIBLE else View.GONE
    }

    private fun updateNumberStatus(number: Int, isChecked: Boolean) {
        if (institution.isEmpty()) return
        val user = auth.currentUser ?: return
        val safeInstKey = AppUtils.getSafeKey(institution)
        val ref = database.child("institutions").child(safeInstKey).child("categories").child(categoryTitle).child(number.toString())
        
        if (isChecked) {
            val data = mapOf("uid" to user.uid, "color" to currentUserColor)
            ref.setValue(data).addOnCompleteListener {
                context?.let { DashboardWidget.notifyUpdate(it) }
            }
        } else {
            ref.get().addOnSuccessListener { snapshot ->
                if (snapshot.child("uid").value?.toString() == user.uid) {
                    ref.removeValue().addOnCompleteListener {
                        context?.let { DashboardWidget.notifyUpdate(it) }
                    }
                } else {
                    showToast(getString(R.string.only_owner_can_uncheck))
                }
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        detailListener?.let { 
            val safeInstKey = AppUtils.getSafeKey(institution)
            database.child("institutions").child(safeInstKey).child("categories").child(categoryTitle).removeEventListener(it) 
        }
        userListener?.let { database.child("users").child(auth.currentUser?.uid ?: "").removeEventListener(it) }
        _binding = null
    }
}
