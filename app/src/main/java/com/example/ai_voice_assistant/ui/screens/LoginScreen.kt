package com.example.ai_voice_assistant.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ai_voice_assistant.ui.components.GlassCard
import com.example.ai_voice_assistant.ui.theme.BackgroundEnd
import com.example.ai_voice_assistant.ui.theme.BackgroundStart
import com.google.firebase.auth.FirebaseAuth

@Composable
fun LoginScreen(
    auth: FirebaseAuth?,
    onLoginSuccess: (String) -> Unit,
    onNavigateToRegister: () -> Unit
) {
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var passwordVisible by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var isLoading by remember { mutableStateOf(false) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                brush = Brush.radialGradient(
                    colors = listOf(
                        BackgroundStart,
                        BackgroundEnd
                    )
                )
            ),
        contentAlignment = Alignment.Center
    ) {
        GlassCard(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp),
            cornerRadius = 24.dp
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Text(
                    text = "Welcome Back",
                    fontSize = 28.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )

                if (auth == null) {
                    Text(
                        text = "Firebase is not initialized. Please check your google-services.json.",
                        color = Color.Red,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                OutlinedTextField(
                    value = email,
                    onValueChange = { email = it },
                    label = { Text("Email", color = Color.White.copy(alpha = 0.7f)) },
                    modifier = Modifier.fillMaxWidth(),
                    leadingIcon = { Icon(Icons.Default.Email, contentDescription = null, tint = Color.White) },
                    enabled = auth != null,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White,
                        focusedBorderColor = Color.White,
                        unfocusedBorderColor = Color.White.copy(alpha = 0.5f),
                        cursorColor = Color.White
                    )
                )

                OutlinedTextField(
                    value = password,
                    onValueChange = { password = it },
                    label = { Text("Password", color = Color.White.copy(alpha = 0.7f)) },
                    modifier = Modifier.fillMaxWidth(),
                    leadingIcon = { Icon(Icons.Default.Lock, contentDescription = null, tint = Color.White) },
                    visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                    trailingIcon = {
                        IconButton(onClick = { passwordVisible = !passwordVisible }, enabled = auth != null) {
                            Icon(
                                imageVector = if (passwordVisible) Icons.Default.Visibility else Icons.Default.VisibilityOff,
                                contentDescription = null,
                                tint = Color.White
                            )
                        }
                    },
                    enabled = auth != null,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White,
                        focusedBorderColor = Color.White,
                        unfocusedBorderColor = Color.White.copy(alpha = 0.5f),
                        cursorColor = Color.White
                    )
                )

                if (errorMessage != null) {
                    Text(text = errorMessage!!, color = Color.Red, fontSize = 14.sp)
                }

                Button(
                    onClick = {
                        if (auth == null) return@Button
                        if (email.isBlank() || password.isBlank()) {
                            errorMessage = "Please fill in all fields"
                            return@Button
                        }
                        isLoading = true
                        auth.signInWithEmailAndPassword(email, password)
                            .addOnCompleteListener { task ->
                                isLoading = false
                                if (task.isSuccessful) {
                                    val user = auth.currentUser
                                    if (user?.isEmailVerified == true) {
                                        onLoginSuccess(user.uid)
                                    } else {
                                        errorMessage = "Please verify your email first."
                                        auth.signOut()
                                    }
                                } else {
                                    errorMessage = task.exception?.message ?: "Login failed"
                                }
                            }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp),
                    enabled = !isLoading && auth != null,
                    colors = ButtonDefaults.buttonColors(containerColor = Color.White, contentColor = Color.Black)
                ) {
                    if (isLoading) {
                        CircularProgressIndicator(modifier = Modifier.size(24.dp), color = Color.Black)
                    } else {
                        Text("Login", fontWeight = FontWeight.Bold)
                    }
                }

                TextButton(onClick = onNavigateToRegister, enabled = auth != null) {
                    Text("Don't have an account? Register", color = Color.White)
                }
            }
        }
    }
}
