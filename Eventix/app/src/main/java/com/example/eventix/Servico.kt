package com.example.eventix

data class Servico(
    val id: String = "",
    val nome: String = "",
    val tipo: String = "",
    val precoMinimo: Double = 0.0,
    val precoMaximo: Double = 0.0,
    val contacto: String = "",
    val descricao: String = "",
    val empresa: String = "",
    val favorito: Boolean = false
) {
    fun getPrecoFormatado(): String {
        return if (precoMinimo == precoMaximo) {
            "${precoMinimo.toInt()}€"
        } else {
            "${precoMinimo.toInt()}€-${precoMaximo.toInt()}€"
        }
    }

    fun getPrecoMedio(): Double {
        return (precoMinimo + precoMaximo) / 2
    }
}