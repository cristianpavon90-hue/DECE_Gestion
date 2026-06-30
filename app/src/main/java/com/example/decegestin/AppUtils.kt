package com.example.decegestin

import android.content.Context
import android.graphics.Color
import androidx.core.content.ContextCompat

object AppUtils {
    val institutions = arrayOf("U.E.V.A.A.", "U.E.Y.", "U.E.P.F.C", "U.E.17.A")

    val caseTypes = arrayOf(
        "Consumo de sustancias",
        "Ideación suicida",
        "Necesidades educativas",
        "Intervención en crisis",
        "Conflictos Escolares y de Adaptación",
        "Violencia sexual",
        "Inestabilidad Emocional Reactiva",
        "Procesos de Duelo y Pérdida",
        "Dificultades Familiares (Leves/Moderadas)",
        "Violencia y Vulneración de Derechos (Fase de Detección y Ruta)",
        "Derivación interinstitucional",
        "Desaparición",
        "Condición médica",
        "Embarazo",
        "Maternidad",
        "Paternidad",
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

    val daysOfWeek = arrayOf("Lunes", "Martes", "Miércoles", "Jueves", "Viernes")
    val frequencies = arrayOf("Semanal", "Bisemanal", "Mensual")

    val defaultDerivadores = arrayOf(
        "Alicia Palacios", "Amada Palacios", "Ana del Rocio Vargas", "Ana Romero",
        "Angelita Heredia", "Carmita Bayas", "Cristian Pavón", "Cristina Sánchez",
        "Danilo Montes De Oca", "Diana Castillo", "Diana Martinez", "Dorila Sánchez",
        "Edwin Ati", "Emma Valdivieso", "Fernando Naranjo", "Gabriel Ojeda",
        "Gabriela Velastegui", "Gilberto Cherrez", "Gladys López", "Gloria Inca",
        "Holguer Suque", "Isabel Cayambe", "Ítalo Sánchez", "Jaime Cuvi",
        "Janeth Aguirre", "Jenny Urquizo", "Jhennyfer Lopez", "Juan Carlos Amancha",
        "Juan Carlos Castillo", "Karen Jacome", "Lady Manobanda", "Lucia Yambay",
        "Lupe Quiguiri", "Luz Peña", "Marcelo López", "María Fernanda Arroba",
        "María Tuza", "Marivel Miranda", "Martha Fiallos", "Mayra Cherrez",
        "Mery Ortiz", "Norma Toaquiza", "Oswaldo Vargas", "Paola Naranjo",
        "Paulina Abarca", "Ricardo López", "Robinson Puma", "Silvia Chavarrea",
        "Sonia Panata", "Valeria Ballesteros", "Verónica Once", "Victoria Tigse",
        "William Flores", "Ximena Alvarado", "Zoila Villacrés", "Representante", "Seguimiento", "Otros"
    )

    fun getSafeKey(name: String): String {
        return name.trim()
            .replace(".", "")
            .replace("#", "")
            .replace("$", "")
            .replace("[", "")
            .replace("]", "")
            .replace(" ", "_") // Reemplazar espacios internos por guion bajo para mayor seguridad
    }

    fun parseColor(colorData: String?, context: Context): Int {
        if (colorData == null) return ContextCompat.getColor(context, R.color.avatar_blue)
        
        return if (colorData.startsWith("#")) {
            try {
                Color.parseColor(colorData)
            } catch (e: Exception) {
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
}
