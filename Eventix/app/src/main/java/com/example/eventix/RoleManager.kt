package com.example.eventix

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore

object RoleManager {
    private val auth = FirebaseAuth.getInstance()
    private val db = FirebaseFirestore.getInstance()

    fun checkUserRole(callback: (UserRole) -> Unit) {
        val userId = auth.currentUser?.uid ?: return

        db.collection("utilizadores").document(userId)
            .get()
            .addOnSuccessListener { document ->
                if (document.exists()) {
                    val role = document.getString("role") ?: "utilizador"
                    val userRole = when (role) {
                        "gestor" -> UserRole.GESTOR
                        else -> UserRole.UTILIZADOR
                    }
                    callback(userRole)
                } else {
                    callback(UserRole.UTILIZADOR)
                }
            }
            .addOnFailureListener {
                callback(UserRole.UTILIZADOR)
            }
    }

    fun isManager(callback: (Boolean) -> Unit) {
        checkUserRole { role ->
            callback(role == UserRole.GESTOR)
        }
    }
}