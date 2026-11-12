package com.hassandev.problemlab

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.jan.supabase.auth.OtpType
import io.github.jan.supabase.auth.auth
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun VerifyEmailScreen(
    email: String,
    onVerified: () -> Unit,
    onBack: () -> Unit
) {
    var code by remember { mutableStateOf("") }
    var loading by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }
    var secondsLeft by remember { mutableStateOf(60) }
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }

    // Compte à rebours avant de pouvoir renvoyer un code
    LaunchedEffect(secondsLeft) {
        if (secondsLeft > 0) {
            delay(1000)
            secondsLeft -= 1
        }
    }

    LaunchedEffect(message) {
        message?.let {
            snackbarHostState.showSnackbar(it)
            message = null
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        containerColor = Paper
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .background(Paper)
                .padding(horizontal = 28.dp),
            verticalArrangement = Arrangement.Center
        ) {
            Text("Confirme ton email", style = MaterialTheme.typography.headlineSmall, color = Ink)
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                "Nous avons envoyé un code à $email. Saisis-le ci-dessous pour confirmer que ce compte t'appartient.",
                color = MossDark
            )
            Spacer(modifier = Modifier.height(24.dp))

            OutlinedTextField(
                value = code,
                onValueChange = { code = it.filter { c -> c.isDigit() }.take(8) },
                label = { Text("Code reçu par email") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                singleLine = true,
                textStyle = LocalTextStyle.current.copy(
                    fontSize = 24.sp,
                    letterSpacing = 6.sp,
                    textAlign = TextAlign.Center
                ),
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(20.dp))

            Button(
                onClick = {
                    loading = true
                    scope.launch {
                        try {
                            supabase.auth.verifyEmailOtp(
                                type = OtpType.Email.SIGNUP,
                                email = email,
                                token = code.trim()
                            )
                            onVerified()
                        } catch (e: Exception) {
                            android.util.Log.e("PROBLEMLAB_DEBUG", "Erreur vérification code", e)
                            message = "Code incorrect ou expiré. Vérifie le code ou demande-en un nouveau."
                        } finally {
                            loading = false
                        }
                    }
                },
                enabled = !loading && code.length >= 6,
                colors = ButtonDefaults.buttonColors(containerColor = Terracotta, contentColor = Paper),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
            ) {
                Text(if (loading) "Vérification..." else "Confirmer", fontWeight = FontWeight.SemiBold)
            }

            TextButton(
                enabled = !loading && secondsLeft == 0,
                onClick = {
                    loading = true
                    scope.launch {
                        try {
                            supabase.auth.resendEmail(OtpType.Email.SIGNUP, email)
                            secondsLeft = 60
                            message = "Un nouveau code vient d'être envoyé."
                        } catch (e: Exception) {
                            android.util.Log.e("PROBLEMLAB_DEBUG", "Erreur renvoi code", e)
                            message = "Impossible de renvoyer le code pour le moment. Réessaie dans quelques minutes."
                        } finally {
                            loading = false
                        }
                    }
                },
                modifier = Modifier.align(androidx.compose.ui.Alignment.CenterHorizontally)
            ) {
                Text(
                    if (secondsLeft > 0) "Renvoyer le code (${secondsLeft}s)" else "Renvoyer le code",
                    color = MossDark
                )
            }

            TextButton(
                onClick = onBack,
                modifier = Modifier.align(androidx.compose.ui.Alignment.CenterHorizontally)
            ) {
                Text("Retour à la connexion", color = MossDark)
            }
        }
    }
}