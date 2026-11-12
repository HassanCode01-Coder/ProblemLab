package com.hassandev.problemlab

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import io.github.jan.supabase.auth.auth
import kotlinx.coroutines.launch

@Composable
fun ResetPasswordScreen(onDone: () -> Unit) {
    var password by remember { mutableStateOf("") }
    var confirm by remember { mutableStateOf("") }
    var loading by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Paper)
            .padding(24.dp),
        verticalArrangement = Arrangement.Center
    ) {
        Text("Nouveau mot de passe", style = MaterialTheme.typography.headlineSmall, color = Ink)
        Spacer(modifier = Modifier.height(8.dp))
        Text("Choisis un nouveau mot de passe (8 caractères minimum).", color = MossDark)
        Spacer(modifier = Modifier.height(24.dp))

        OutlinedTextField(
            value = password,
            onValueChange = { password = it },
            label = { Text("Nouveau mot de passe") },
            visualTransformation = PasswordVisualTransformation(),
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(modifier = Modifier.height(12.dp))
        OutlinedTextField(
            value = confirm,
            onValueChange = { confirm = it },
            label = { Text("Confirmer le mot de passe") },
            visualTransformation = PasswordVisualTransformation(),
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )

        errorMessage?.let {
            Spacer(modifier = Modifier.height(12.dp))
            Text(it, color = Terracotta)
        }

        Spacer(modifier = Modifier.height(20.dp))
        Button(
            onClick = {
                val p = password.trim()
                when {
                    p.length < 8 -> errorMessage = "Le mot de passe doit contenir au moins 8 caractères."
                    p != confirm.trim() -> errorMessage = "Les deux mots de passe ne sont pas identiques."
                    else -> {
                        errorMessage = null
                        loading = true
                        scope.launch {
                            try {
                                supabase.auth.updateUser { this.password = p }
                                onDone()
                            } catch (e: Exception) {
                                android.util.Log.e("PROBLEMLAB_DEBUG", "Erreur nouveau mot de passe", e)
                                errorMessage = "Impossible de changer le mot de passe. Le lien a peut-être expiré, redemande-en un."
                            } finally {
                                loading = false
                            }
                        }
                    }
                }
            },
            enabled = !loading,
            colors = ButtonDefaults.buttonColors(containerColor = Terracotta),
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(if (loading) "Enregistrement..." else "Enregistrer")
        }
    }
}