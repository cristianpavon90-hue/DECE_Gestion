package com.example.decegestin

data class EducandoRecord(
    val id: String = "",
    val primerApellido: String = "",
    val segundoApellido: String = "",
    val primerNombre: String = "",
    val segundoNombre: String = "",
    val anio: Int = 0,
    val calificacion: Int = 0,
    val createdBy: String = "",
    val timestamp: Long = System.currentTimeMillis()
) {
    val nombreCompleto: String
        get() = "$primerApellido $segundoApellido, $primerNombre $segundoNombre".trim().replace(Regex("\\s+"), " ")
}
