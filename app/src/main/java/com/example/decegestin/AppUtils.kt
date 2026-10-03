package com.example.decegestin

import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat
import androidx.core.graphics.toColorInt

object AppUtils {
    val defaultInstitutions = arrayOf("U.E.V.A.A.", "U.E.Y.", "U.E.P.F.C", "U.E.17.A")
    val institutions = defaultInstitutions

    val cargos = arrayOf("Coordinador Distrital", "Coordinador Institucional", "Analista DECE")

    val caseTypes = arrayOf(
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
        "Trata de personas",
        "Necesidades educativas",
        "Intervención en crisis",
        "Conflictos Escolares y de Adaptación",
        "Inestabilidad Emocional Reactiva",
        "Procesos de Duelo y Pérdida",
        "Dificultades Familiares (Leves/Moderadas)",
        "Derivación interinstitucional",
        "Condición médica",
        "Conductual y asistencias",
        "En proceso",
        "Otros"
    )

    val grades = arrayOf(
        "Inicial 1", "Inicial 2", "1ro EGB", "2do EGB", "3ro EGB", "4to EGB",
        "5to EGB", "6to EGB", "7mo EGB", "8vo EGB", "9no EGB", "10mo EGB",
        "1ro BGU", "1ro BT", "2do BGU", "2do BT", "3ro BGU", "3ro BT"
    )

    val parallels = arrayOf("A", "B", "C", "D", "E", "F")

    val countryCodes = arrayOf(
        "+593 (Ecuador)",
        "+57 (Colombia)",
        "+51 (Perú)",
        "+58 (Venezuela)",
        "+1 (EE.UU.)",
        "+34 (España)",
        "+52 (México)",
        "+54 (Argentina)",
        "+56 (Chile)"
    )

    fun formatPhoneNumber(countryCode: String, rawPhone: String): String {
        var cleaned = rawPhone.trim().replace("\\s+".toRegex(), "").replace("-", "")
        if (cleaned.startsWith("0")) {
            cleaned = cleaned.substring(1)
        }
        val code = countryCode.split(" ")[0].trim()
        return if (cleaned.startsWith("+")) cleaned else "$code$cleaned"
    }

    val informeTipos = arrayOf(
        "Caso Riesgo Psicosocial",
        "Rutas y protocolos de violencia",
        "Rutas violencia física",
        "Rutas violencia psicológica",
        "Socialización Violencia Sexual",
        "Rutas violencia digital",
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
        "Socialización ENEIS",
        "Socialización Violencia de género",
        "Socialización resolución de conflictos",
        "Socialización OVP",
        "Socialización discriminación y racismo",
        "Socialización diversidades",
        "Otros"
    )

    val atencionTipos = arrayOf(
        "Individual",
        "Familiar",
        "Pedagógico",
        "Psicológico",
        "Médico",
        "Jurídico",
        "Interdisciplinario",
        "Crisis"
    )

    val informeAudiencia = arrayOf("Docentes", "Padres", "Estudiantes")

    val fullGradesList: List<String> by lazy {
        val list = mutableListOf<String>()
        list.add("Inicial 1")
        list.add("Inicial 2 A")
        list.add("Inicial 2 B")
        
        val egbs = listOf("1 EGB", "2 EGB", "3 EGB", "4 EGB", "5 EGB", "6 EGB", "7 EGB", "8 EGB", "9 EGB", "10 EGB")
        val bgus = listOf("1 BGU", "2 BGU", "3 BGU")
        val paralelos = listOf("A", "B", "C")

        (egbs + bgus).forEach { grade ->
            paralelos.forEach { p ->
                list.add("$grade \"$p\"")
            }
        }
        list
    }

    val daysOfWeek = arrayOf("Lunes", "Martes", "Miércoles", "Jueves", "Viernes")
    val frequencies = arrayOf("Semanal", "Bisemanal", "Mensual")

    val derivadorCategorias = arrayOf("Autoridad", "Docentes", "Representantes", "DECE")

    val defaultDerivadoresCategorias = mapOf(
        "Autoridad" to listOf(
            "Rector/a", "Vicerrector/a", "Inspector/a General", "Subinspector/a", "Director/a"
        ),
        "Docentes" to listOf(
            "Docente", "Docente Tutor"
        ),
        "Representantes" to listOf(
            "Representante Legal", "Padre de Familia", "Madre de Familia", "Tutor/a Legal"
        ),
        "DECE" to listOf(
            "Analista DECE", "Coordinador/a DECE", "Psicólogo/a DECE", "Seguimiento DECE"
        )
    )

    val defaultDerivadores = defaultDerivadoresCategorias.values.flatten().toTypedArray() + arrayOf("Otros")

    fun getSafeKey(name: String): String {
        return name.trim()
            .replace(".", "")
            .replace("#", "")
            .replace("$", "")
            .replace("[", "")
            .replace("]", "")
            .replace(" ", "_") // Reemplazar espacios internos por guion bajo para mayor seguridad
    }

    /**
     * Sanitiza la entrada de texto para prevenir inyecciones y limpiar datos.
     * En NoSQL (Firebase) previene principalmente datos malformados o scripts maliciosos.
     */
    fun sanitizeInput(input: String?): String {
        if (input == null) return ""
        return input.trim()
            .replace("<script", "", ignoreCase = true)
            .replace("</script>", "", ignoreCase = true)
            .replace("javascript:", "", ignoreCase = true)
            .replace("'", "''") // Escape simple quotes (common in SQL, though not strictly needed here)
            .replace("\"", "\"\"")
    }

    fun parseColor(colorData: String?, context: Context): Int {
        if (colorData == null) return ContextCompat.getColor(context, R.color.avatar_blue)
        
        return if (colorData.startsWith("#")) {
            try {
                colorData.toColorInt()
            } catch (_: Exception) {
                ContextCompat.getColor(context, R.color.avatar_blue)
            }
        } else {
            val resId = when (colorData) {
                "red" -> R.color.avatar_red
                "green" -> R.color.avatar_green
                "orange" -> R.color.avatar_orange
                "purple" -> R.color.avatar_purple
                else -> R.color.avatar_blue
            }
            ContextCompat.getColor(context, resId)
        }
    }

    private val userRequestTimestamps = mutableMapOf<String, MutableList<Long>>()
    private const val MAX_REQUESTS_PER_MINUTE = 10

    fun canMakeRequest(userId: String): Boolean {
        val now = System.currentTimeMillis()
        val timestamps = userRequestTimestamps.getOrPut(userId) { mutableListOf() }
        
        // Eliminar timestamps de más de un minuto
        timestamps.removeAll { now - it > 60000 }
        
        return if (timestamps.size < MAX_REQUESTS_PER_MINUTE) {
            timestamps.add(now)
            true
        } else {
            false
        }
    }

    fun scheduleExactAlarm(context: Context, id: Int, timeInMillis: Long, intent: Intent) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as android.app.AlarmManager
        val pendingIntent = android.app.PendingIntent.getBroadcast(
            context, id, intent, 
            android.app.PendingIntent.FLAG_UPDATE_CURRENT or android.app.PendingIntent.FLAG_IMMUTABLE
        )

        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
            if (alarmManager.canScheduleExactAlarms()) {
                alarmManager.setExactAndAllowWhileIdle(android.app.AlarmManager.RTC_WAKEUP, timeInMillis, pendingIntent)
            } else {
                alarmManager.setAndAllowWhileIdle(android.app.AlarmManager.RTC_WAKEUP, timeInMillis, pendingIntent)
            }
        } else {
            alarmManager.setExactAndAllowWhileIdle(android.app.AlarmManager.RTC_WAKEUP, timeInMillis, pendingIntent)
        }
    }

    fun loadInstitutions(database: com.google.firebase.database.FirebaseDatabase, onResult: (List<String>) -> Unit) {
        database.reference.child("app_config").child("institutions").get().addOnSuccessListener { snapshot ->
            val list = mutableListOf<String>()
            if (snapshot.exists()) {
                for (child in snapshot.children) {
                    val name = child.value?.toString()
                    if (!name.isNullOrEmpty()) list.add(name)
                }
            }
            if (list.isEmpty()) {
                list.addAll(defaultInstitutions)
                val seedMap = mutableMapOf<String, String>()
                defaultInstitutions.forEach { seedMap[getSafeKey(it)] = it }
                database.reference.child("app_config").child("institutions").setValue(seedMap)
            }
            onResult(list.sorted())
        }.addOnFailureListener {
            onResult(defaultInstitutions.toList())
        }
    }

    fun addInstitution(database: com.google.firebase.database.FirebaseDatabase, name: String, onComplete: (Boolean, String?) -> Unit) {
        val cleanName = sanitizeInput(name)
        if (cleanName.isEmpty()) {
            onComplete(false, "Nombre inválido")
            return
        }
        val key = getSafeKey(cleanName)
        database.reference.child("app_config").child("institutions").child(key).setValue(cleanName)
            .addOnSuccessListener { onComplete(true, null) }
            .addOnFailureListener { onComplete(false, it.message) }
    }

    fun deleteInstitution(database: com.google.firebase.database.FirebaseDatabase, name: String, onComplete: (Boolean, String?) -> Unit) {
        val key = getSafeKey(name)
        database.reference.child("app_config").child("institutions").child(key).removeValue()
            .addOnSuccessListener { onComplete(true, null) }
            .addOnFailureListener { onComplete(false, it.message) }
    }

    fun authorizeCedula(
        database: com.google.firebase.database.FirebaseDatabase,
        idCard: String,
        cargo: String,
        institution: String,
        authorizedBy: String,
        onComplete: (Boolean, String?) -> Unit
    ) {
        val cleanCedula = sanitizeInput(idCard)
        if (cleanCedula.isEmpty()) {
            onComplete(false, "Cédula inválida")
            return
        }
        val data = mapOf(
            "idCard" to cleanCedula,
            "cargo" to cargo,
            "institution" to institution,
            "active" to true,
            "authorizedBy" to authorizedBy,
            "timestamp" to com.google.firebase.database.ServerValue.TIMESTAMP
        )
        database.reference.child("authorized_cedulas").child(cleanCedula).setValue(data)
            .addOnSuccessListener { onComplete(true, null) }
            .addOnFailureListener { onComplete(false, it.message) }
    }

    fun revokeCedula(database: com.google.firebase.database.FirebaseDatabase, idCard: String, onComplete: (Boolean, String?) -> Unit) {
        val cleanCedula = sanitizeInput(idCard)
        database.reference.child("authorized_cedulas").child(cleanCedula).removeValue()
            .addOnSuccessListener { onComplete(true, null) }
            .addOnFailureListener { onComplete(false, it.message) }
    }
}
