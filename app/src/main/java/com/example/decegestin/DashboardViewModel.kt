package com.example.decegestin

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.*

class DashboardViewModel : ViewModel() {

    private val database = FirebaseDatabase.getInstance().reference
    private val auth = FirebaseAuth.getInstance()

    private val _userInitials = MutableLiveData<String>()
    val userInitials: LiveData<String> = _userInitials

    private val _userColor = MutableLiveData<Int>()
    val userColor: LiveData<Int> = _userColor

    private val _institution = MutableLiveData<String?>()
    val institution: LiveData<String?> = _institution

    private val _categoryStats = MutableLiveData<Map<String, Pair<Int, Int>>>()
    val categoryStats: LiveData<Map<String, Pair<Int, Int>>> = _categoryStats

    private var userListener: ValueEventListener? = null
    private var categoriesListener: ValueEventListener? = null
    private var currentCategoriesRef: DatabaseReference? = null

    fun startListening(context: android.content.Context) {
        val user = auth.currentUser ?: return
        
        userListener = database.child("users").child(user.uid).addValueEventListener(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                if (!snapshot.exists()) return
                
                val initials = snapshot.child("initials").value?.toString() 
                    ?: user.displayName?.split(" ")?.filter { it.isNotEmpty() }?.mapNotNull { it.firstOrNull() }?.take(2)?.joinToString("")?.uppercase()
                    ?: "U"
                _userInitials.value = initials

                val colorData = snapshot.child("profileColor").value?.toString()
                _userColor.value = AppUtils.parseColor(colorData, context)

                val inst = snapshot.child("institution").value?.toString()
                if (inst != _institution.value) {
                    _institution.value = inst
                    inst?.let { listenToCategories(it) }
                }
            }
            override fun onCancelled(error: DatabaseError) {}
        })
    }

    private fun listenToCategories(inst: String) {
        val safeKey = AppUtils.getSafeKey(inst)
        categoriesListener?.let { currentCategoriesRef?.removeEventListener(it) }
        
        currentCategoriesRef = database.child("institutions").child(safeKey).child("categories")
        categoriesListener = currentCategoriesRef?.addValueEventListener(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val stats = mutableMapOf<String, Pair<Int, Int>>()
                val categories = listOf("Oficios", "Informes Técnicos", "Reporte de Violencia", "Derivación MSP", "Derivación UDAI")
                
                categories.forEach { cat ->
                    val catSnapshot = snapshot.child(cat)
                    val checkedNumbers = mutableListOf<Int>()
                    for (child in catSnapshot.children) {
                        child.key?.toIntOrNull()?.let { checkedNumbers.add(it) }
                    }
                    val lastMarked = checkedNumbers.maxOrNull() ?: 0
                    stats[cat] = Pair(lastMarked, checkedNumbers.size)
                }
                _categoryStats.value = stats
            }
            override fun onCancelled(error: DatabaseError) {}
        })
    }

    override fun onCleared() {
        super.onCleared()
        userListener?.let { database.child("users").child(auth.currentUser?.uid ?: "").removeEventListener(it) }
        categoriesListener?.let { currentCategoriesRef?.removeEventListener(it) }
    }
}
