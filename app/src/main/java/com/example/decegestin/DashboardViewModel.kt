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

    private val _isDistrital = MutableLiveData<Boolean>(false)
    val isDistrital: LiveData<Boolean> = _isDistrital

    private val _distritalRiesgos = MutableLiveData<List<Pair<String, Int>>>()
    val distritalRiesgos: LiveData<List<Pair<String, Int>>> = _distritalRiesgos

    private val _distritalSocializaciones = MutableLiveData<List<Pair<String, Int>>>()
    val distritalSocializaciones: LiveData<List<Pair<String, Int>>> = _distritalSocializaciones

    private var userListener: ValueEventListener? = null
    private var categoriesListener: ValueEventListener? = null
    private var distritalListener: ValueEventListener? = null
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

                val cargo = snapshot.child("cargo").value?.toString() ?: ""
                val role = snapshot.child("role").value?.toString() ?: ""
                val distrital = cargo == "Coordinador Distrital" || cargo.contains("Distrital", ignoreCase = true) || role == "distrital"
                _isDistrital.value = distrital

                if (distrital) {
                    listenToDistritalData()
                } else {
                    val inst = snapshot.child("institution").value?.toString()
                    if (inst != _institution.value) {
                        _institution.value = inst
                        inst?.let { listenToCategories(it) }
                    }
                }
            }
            override fun onCancelled(error: DatabaseError) {}
        })
    }

    private fun listenToDistritalData() {
        distritalListener?.let { database.child("institutions").removeEventListener(it) }

        distritalListener = database.child("institutions").addValueEventListener(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val caseTypeCounts = mutableMapOf<String, Int>()
                val socializacionCounts = mutableMapOf<String, Int>()

                for (instSnapshot in snapshot.children) {
                    // 1. Recorrer formularios para Riesgos psicosociales
                    val formsSnapshot = instSnapshot.child("forms")
                    for (formChild in formsSnapshot.children) {
                        val caseType = formChild.child("caseType").value?.toString()?.trim() ?: continue
                        caseTypeCounts[caseType] = (caseTypeCounts[caseType] ?: 0) + 1
                    }

                    // 2. Recorrer informes técnicos para Socializaciones
                    val informesSnapshot = instSnapshot.child("categories").child("Informes Técnicos")
                    for (informeChild in informesSnapshot.children) {
                        val tipoInforme = informeChild.child("tipoInforme").value?.toString()?.trim() ?: continue
                        socializacionCounts[tipoInforme] = (socializacionCounts[tipoInforme] ?: 0) + 1
                    }
                }

                processDistritalRiesgos(caseTypeCounts)
                processDistritalSocializaciones(socializacionCounts)
            }

            override fun onCancelled(error: DatabaseError) {}
        })
    }

    private fun processDistritalRiesgos(counts: Map<String, Int>) {
        val allowedIndividual = listOf(
            "Violencia sexual",
            "Violencia física",
            "Violencia psicológica",
            "Acoso escolar",
            "Negligencia",
            "Consumo de sustancias licitas",
            "Consumo de sustancias ilícitas",
            "Suicidio",
            "Desapariciones",
            "Embarazo",
            "Maternidad",
            "Paternidad",
            "Trabajo infantil",
            "Tráfico ilícito",
            "Trata de personas"
        )

        val excluded = setOf("Necesidades educativas", "En proceso")
        val lowerToExactIndividual = allowedIndividual.associateBy { it.lowercase().trim() }

        val resultList = mutableListOf<Pair<String, Int>>()
        var otrasAtencionesCount = 0

        counts.forEach { (rawType, count) ->
            if (count <= 0) return@forEach
            val cleanType = rawType.trim()
            if (excluded.any { it.equals(cleanType, ignoreCase = true) }) {
                return@forEach
            }

            val exactMatch = lowerToExactIndividual[cleanType.lowercase()]
            if (exactMatch != null) {
                val current = resultList.find { it.first == exactMatch }
                if (current != null) {
                    val idx = resultList.indexOf(current)
                    resultList[idx] = Pair(exactMatch, current.second + count)
                } else {
                    resultList.add(Pair(exactMatch, count))
                }
            } else {
                otrasAtencionesCount += count
            }
        }

        if (otrasAtencionesCount >= 1) {
            resultList.add(Pair("Otras atenciones", otrasAtencionesCount))
        }

        // Mostrar solo ítems que tengan al menos 1 caso
        val filtered = resultList.filter { it.second >= 1 }
        _distritalRiesgos.value = filtered
    }

    private fun processDistritalSocializaciones(counts: Map<String, Int>) {
        val allowedSocializations = listOf(
            "Rutas y protocolos de violencia",
            "Rutas violencia física",
            "Rutas violencia psicológica",
            "Socialización Violencia Sexual",
            "Recorrido Participativo",
            "Rutas acoso escolar",
            "Rutas de uso y consumo de drogas",
            "Rutas trabajo infantil",
            "Rutas desapariciones",
            "Rutas y protocolos de embarazo, maternidad paternidad",
            "Rutas suicidio e Intentos autolíticos",
            "Rutas maternidad o paternidad temprana",
            "Rutas trata de personas",
            "Rutas tráfico ilícito de migrantes",
            "Rutas violencia digital",
            "Socialización ENEIS"
        )

        val lowerToExactSocialization = allowedSocializations.associateBy { it.lowercase().trim() }

        val resultList = mutableListOf<Pair<String, Int>>()
        var otrosCount = 0

        counts.forEach { (rawTipo, count) ->
            if (count <= 0) return@forEach
            val cleanTipo = rawTipo.trim()

            val exactMatch = lowerToExactSocialization[cleanTipo.lowercase()]
                ?: lowerToExactSocialization.entries.find { (key, _) ->
                    cleanTipo.lowercase().contains(key) || key.contains(cleanTipo.lowercase())
                }?.value

            if (exactMatch != null) {
                val current = resultList.find { it.first == exactMatch }
                if (current != null) {
                    val idx = resultList.indexOf(current)
                    resultList[idx] = Pair(exactMatch, current.second + count)
                } else {
                    resultList.add(Pair(exactMatch, count))
                }
            } else {
                otrosCount += count
            }
        }

        if (otrosCount >= 1) {
            resultList.add(Pair("Otros", otrosCount))
        }

        val filtered = resultList.filter { it.second >= 1 }
        _distritalSocializaciones.value = filtered
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
        distritalListener?.let { database.child("institutions").removeEventListener(it) }
    }
}
