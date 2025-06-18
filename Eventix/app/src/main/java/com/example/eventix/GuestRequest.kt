package com.example.eventix

data class GuestRequest(
    val id: String = "",
    val remetente: String = "",
    val destinatario: String = "",
    val nomeRemetente: String = "",
    val nomeDestinatario: String = "",
    val emailRemetente: String = "",
    val emailDestinatario: String = "",
    val estado: String = ""
)