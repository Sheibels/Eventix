package com.example.eventix

data class User(
    val id: String = "",
    val nome: String = "",
    val email: String = "",
    val role: String = "utilizador"
)

enum class UserRole(val value: String) {
    UTILIZADOR("utilizador"),
    GESTOR("gestor")
}