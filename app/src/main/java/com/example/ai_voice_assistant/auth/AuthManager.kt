package com.example.ai_voice_assistant.auth

import com.google.android.gms.tasks.Task
import com.google.firebase.auth.AuthResult
import com.google.firebase.auth.FirebaseAuth

class AuthManager {
    private val auth: FirebaseAuth = FirebaseAuth.getInstance()

    fun registerUser(email: String, password: String): Task<AuthResult> {
        return auth.createUserWithEmailAndPassword(email, password)
            .addOnSuccessListener { result ->
                result.user?.sendEmailVerification()
            }
    }

    fun loginUser(email: String, password: String): Task<AuthResult> {
        return auth.signInWithEmailAndPassword(email, password)
    }

    fun deleteUserAccount(): Task<Void>? {
        return auth.currentUser?.delete()
    }
}
