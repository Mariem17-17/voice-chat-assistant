package com.example.ai_voice_assistant.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
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
fun RegisterScreen(
    auth: FirebaseAuth?,
    onRegisterSuccess: () -> Unit,
    onNavigateToLogin: () -> Unit
) {
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var confirmPassword by remember { mutableStateOf("") }
    var passwordVisible by remember { mutableStateOf(false) }
    var consentChecked by remember { mutableStateOf(false) }
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
                .padding(24.dp)
                .verticalScroll(rememberScrollState()),
            cornerRadius = 24.dp
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Text(
                    text = "Create Account",
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

                OutlinedTextField(
                    value = confirmPassword,
                    onValueChange = { confirmPassword = it },
                    label = { Text("Confirm Password", color = Color.White.copy(alpha = 0.7f)) },
                    modifier = Modifier.fillMaxWidth(),
                    leadingIcon = { Icon(Icons.Default.Lock, contentDescription = null, tint = Color.White) },
                    visualTransformation = PasswordVisualTransformation(),
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

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Checkbox(
                        checked = consentChecked,
                        onCheckedChange = { consentChecked = it },
                        enabled = auth != null,
                        colors = CheckboxDefaults.colors(checkmarkColor = Color.Black, checkedColor = Color.White)
                    )
                    Text(
                        text = "I agree to the Privacy Policy (GDPR)",
                        color = Color.White,
                        fontSize = 12.sp
                    )
                }

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
                        if (password != confirmPassword) {
                            errorMessage = "Passwords do not match"
                            return@Button
                        }
                        if (!consentChecked) {
                            errorMessage = "You must agree to the Privacy Policy"
                            return@Button
                        }
                        isLoading = true
                        auth.createUserWithEmailAndPassword(email, password)
                            .addOnCompleteListener { task ->
                                if (task.isSuccessful) {
                                    auth.currentUser?.sendEmailVerification()
                                        ?.addOnCompleteListener {
                                            isLoading = false
                                            onRegisterSuccess()
                                        }
                                } else {
                                    isLoading = false
                                    errorMessage = task.exception?.message ?: "Registration failed"
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
                        Text("Register", fontWeight = FontWeight.Bold)
                    }
                }

                TextButton(onClick = onNavigateToLogin, enabled = auth != null) {
                    Text("Already have an account? Login", color = Color.White)
                }
            }
        }
    }
}
