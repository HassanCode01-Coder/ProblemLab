package com.hassandev.problemlab

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import compose.icons.FontAwesomeIcons
import compose.icons.fontawesomeicons.Solid
import compose.icons.fontawesomeicons.solid.Check
import compose.icons.fontawesomeicons.solid.Envelope
import compose.icons.fontawesomeicons.solid.Eye
import compose.icons.fontawesomeicons.solid.EyeSlash
import compose.icons.fontawesomeicons.solid.Lock
import compose.icons.fontawesomeicons.solid.Phone
import compose.icons.fontawesomeicons.solid.User
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.auth.providers.Google as GoogleProvider
import io.github.jan.supabase.auth.providers.builtin.Email
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

private fun friendlyAuthError(raw: String?): String {
    val msg = raw?.lowercase() ?: ""
    return when {
        "invalid login credentials" in msg -> "Email ou mot de passe incorrect."
        "email not confirmed" in msg -> "Confirme d'abord ton email : regarde dans ta boîte de réception."
        "already registered" in msg || "already exists" in msg -> "Un compte existe déjà avec cet email."
        "rate limit" in msg || "too many" in msg || "seconds" in msg -> "Trop de tentatives. Attends quelques minutes avant de réessayer."
        "invalid" in msg && "email" in msg -> "Adresse email invalide. Vérifie qu'il n'y a pas d'espace ou de faute."
        "password" in msg && ("short" in msg || "weak" in msg || "characters" in msg) -> "Le mot de passe est trop faible ou trop court."
        "network" in msg || "unable to resolve" in msg || "timeout" in msg || "failed to connect" in msg -> "Pas de connexion internet."
        else -> "Une erreur est survenue. Réessaie dans quelques instants."
    }
}

// Critère de mot de passe : (libellé, test)
private data class PasswordRule(val label: String, val check: (String) -> Boolean)

private val passwordRules = listOf(
    PasswordRule("Au moins 8 caractères") { it.length >= 8 },
    PasswordRule("Une majuscule") { it.any { c -> c.isUpperCase() } },
    PasswordRule("Un chiffre") { it.any { c -> c.isDigit() } },
    PasswordRule("Un symbole (!@#$...)") { it.any { c -> !c.isLetterOrDigit() } },
)

private val welcomeTaglines = listOf(
    "Observer. Comprendre. Transformer.",
    "Capture un problème en moins de 20 secondes.",
    "Chaque observation peut devenir une solution.",
    "Repère les problèmes, trouve les opportunités.",
    "De l'observation de terrain à l'idée de projet.",
    "Hassan tu es THE GOAT DEV.",
)

@Composable
fun AuthScreen() {
    var prenom by remember { mutableStateOf("") }
    var nom by remember { mutableStateOf("") }
    var telephone by remember { mutableStateOf("") }
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var confirmPassword by remember { mutableStateOf("") }
    var passwordVisible by remember { mutableStateOf(false) }
    var confirmVisible by remember { mutableStateOf(false) }
    var isSignUp by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var loading by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    var taglineIndex by remember { mutableStateOf(0) }

    LaunchedEffect(Unit) {
        while (true) {
            delay(4500)
            taglineIndex = (taglineIndex + 1) % welcomeTaglines.size
        }
    }

    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(errorMessage) {
        errorMessage?.let {
            snackbarHostState.showSnackbar(it)
            errorMessage = null
        }
    }

    val allRulesValid = passwordRules.all { it.check(password) }
    val passwordsMatch = password == confirmPassword && confirmPassword.isNotEmpty()
    val telephoneValide = telephone.count { it.isDigit() } >= 8

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
            Text(text = "PROBLEM LAB", style = MaterialTheme.typography.headlineMedium, color = Ink)

            if (isSignUp) {
                Text(
                    text = "Crée ton compte",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MossDark,
                    modifier = Modifier.padding(top = 4.dp, bottom = 28.dp)
                )
            } else {
                AnimatedContent(
                    targetState = taglineIndex,
                    label = "tagline",
                    transitionSpec = {
                        (fadeIn(
                            animationSpec = androidx.compose.animation.core.tween(
                                700,
                                easing = androidx.compose.animation.core.FastOutSlowInEasing
                            )
                        ) + slideInVertically(
                            animationSpec = androidx.compose.animation.core.tween(
                                700,
                                easing = androidx.compose.animation.core.FastOutSlowInEasing
                            )
                        ) { it / 4 })
                            .togetherWith(
                                fadeOut(animationSpec = androidx.compose.animation.core.tween(400))
                            )
                    }
                ) { index ->
                    Text(
                        text = welcomeTaglines[index],
                        style = MaterialTheme.typography.bodyLarge,
                        color = MossDark,
                        modifier = Modifier.padding(top = 4.dp, bottom = 28.dp)
                    )
                }
            }

            if (isSignUp) {
                OutlinedTextField(
                    value = prenom,
                    onValueChange = { prenom = it; errorMessage = null },
                    label = { Text("Prénom") },
                    leadingIcon = {
                        Icon(
                            FontAwesomeIcons.Solid.User,
                            contentDescription = null,
                            tint = Terracotta,
                            modifier = Modifier.size(20.dp)
                        )
                    },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(10.dp))
                OutlinedTextField(
                    value = nom,
                    onValueChange = { nom = it; errorMessage = null },
                    label = { Text("Nom") },
                    leadingIcon = {
                        Icon(
                            FontAwesomeIcons.Solid.User,
                            contentDescription = null,
                            tint = Terracotta,
                            modifier = Modifier.size(20.dp)
                        )
                    },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(10.dp))
                OutlinedTextField(
                    value = telephone,
                    onValueChange = {
                        telephone = it.filter { c -> c.isDigit() || c == '+' || c == ' ' }
                        errorMessage = null
                    },
                    label = { Text("Téléphone") },
                    leadingIcon = {
                        Icon(
                            FontAwesomeIcons.Solid.Phone,
                            contentDescription = null,
                            tint = Terracotta,
                            modifier = Modifier.size(20.dp)
                        )
                    },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(10.dp))
            }

            OutlinedTextField(
                value = email,
                onValueChange = { email = it; errorMessage = null },
                label = { Text("Email") },
                leadingIcon = {
                    Icon(
                        FontAwesomeIcons.Solid.Envelope,
                        contentDescription = null,
                        tint = Terracotta,
                        modifier = Modifier.size(20.dp)
                    )
                },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(modifier = Modifier.height(10.dp))

            OutlinedTextField(
                value = password,
                onValueChange = { password = it; errorMessage = null },
                label = { Text("Mot de passe") },
                leadingIcon = {
                    Icon(
                        FontAwesomeIcons.Solid.Lock,
                        contentDescription = null,
                        tint = Terracotta,
                        modifier = Modifier.size(20.dp)
                    )
                },
                trailingIcon = {
                    IconButton(onClick = { passwordVisible = !passwordVisible }) {
                        Icon(
                            if (passwordVisible) FontAwesomeIcons.Solid.EyeSlash else FontAwesomeIcons.Solid.Eye,
                            contentDescription = null,
                            tint = MossDark,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                },
                visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )

            // Critères de mot de passe, visibles seulement à l'inscription
            if (isSignUp && password.isNotEmpty()) {
                Spacer(modifier = Modifier.height(8.dp))
                passwordRules.forEach { rule ->
                    val ok = rule.check(password)
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(vertical = 2.dp)
                    ) {
                        Icon(
                            FontAwesomeIcons.Solid.Check,
                            contentDescription = null,
                            tint = if (ok) Color(0xFF4C7A3F) else Color(0xFFBFBAA8),
                            modifier = Modifier.size(12.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            rule.label,
                            style = MaterialTheme.typography.bodyMedium,
                            color = if (ok) MossDark else Color(0xFFBFBAA8)
                        )
                    }
                }
            }

            if (isSignUp) {
                Spacer(modifier = Modifier.height(10.dp))
                OutlinedTextField(
                    value = confirmPassword,
                    onValueChange = { confirmPassword = it; errorMessage = null },
                    label = { Text("Confirmer le mot de passe") },
                    leadingIcon = {
                        Icon(
                            FontAwesomeIcons.Solid.Lock,
                            contentDescription = null,
                            tint = Terracotta,
                            modifier = Modifier.size(20.dp)
                        )
                    },
                    trailingIcon = {
                        IconButton(onClick = { confirmVisible = !confirmVisible }) {
                            Icon(
                                if (confirmVisible) FontAwesomeIcons.Solid.EyeSlash else FontAwesomeIcons.Solid.Eye,
                                contentDescription = null,
                                tint = MossDark,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    },
                    visualTransformation = if (confirmVisible) VisualTransformation.None else PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    singleLine = true,
                    isError = confirmPassword.isNotEmpty() && !passwordsMatch,
                    modifier = Modifier.fillMaxWidth()
                )
                if (confirmPassword.isNotEmpty() && !passwordsMatch) {
                    Text(
                        "Les mots de passe ne correspondent pas.",
                        color = Color(0xFFB23A2F),
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            Button(
                onClick = {
                    val cleanEmail = email.trim()
                    val cleanPassword = password.trim()
                    val cleanPrenom = prenom.trim()
                    val cleanNom = nom.trim()
                    val cleanTelephone = telephone.trim()
                    errorMessage = null
                    loading = true
                    scope.launch {
                        try {
                            if (isSignUp) {
                                supabase.auth.signUpWith(Email) {
                                    this.email = cleanEmail
                                    this.password = cleanPassword
                                    data = buildJsonObject {
                                        put("prenom", cleanPrenom)
                                        put("nom", cleanNom)
                                        put("telephone", cleanTelephone)
                                    }
                                }
                                if (supabase.auth.currentSessionOrNull() == null) {
                                    emailAConfirmer = cleanEmail
                                }
                                isSignUp = false
                            } else {
                                supabase.auth.signInWith(Email) {
                                    this.email = cleanEmail
                                    this.password = cleanPassword
                                }
                            }
                        } catch (e: Exception) {
                            android.util.Log.e("PROBLEMLAB_DEBUG", "Erreur auth", e)
                            errorMessage = friendlyAuthError(e.message)
                        } finally {
                            loading = false
                        }
                    }
                },
                enabled = !loading && email.isNotBlank() && password.isNotBlank() &&
                        (!isSignUp || (prenom.isNotBlank() && nom.isNotBlank() && telephoneValide && allRulesValid && passwordsMatch)),
                colors = ButtonDefaults.buttonColors(containerColor = Terracotta, contentColor = Paper),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
            ) {
                Text(if (isSignUp) "Créer le compte" else "Se connecter", fontWeight = FontWeight.SemiBold)
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 4.dp),
                horizontalArrangement = Arrangement.Center
            ) {
                Text(if (isSignUp) "J'ai déjà un compte" else "Pas de compte ?", color = MossDark)
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = if (isSignUp) "Se connecter" else "Créer un compte",
                    color = Terracotta,
                    fontWeight = FontWeight.SemiBold,
                    textDecoration = TextDecoration.Underline,
                    modifier = Modifier.clickable { isSignUp = !isSignUp; errorMessage = null }
                )
            }

            if (!isSignUp) {
                TextButton(
                    enabled = !loading,
                    onClick = {
                        val cleanEmail = email.trim()
                        if (cleanEmail.isBlank()) {
                            errorMessage = "Entre ton email d'abord, puis clique sur ce lien."
                        } else {
                            loading = true
                            scope.launch {
                                try {
                                    supabase.auth.resetPasswordForEmail(cleanEmail)
                                    errorMessage = "Si ce compte existe, un email vient d'être envoyé. Pense à vérifier tes spams."
                                } catch (e: Exception) {
                                    android.util.Log.e("PROBLEMLAB_DEBUG", "Erreur mot de passe oublié", e)
                                    errorMessage = friendlyAuthError(e.message)
                                } finally {
                                    loading = false
                                }
                            }
                        }
                    }
                ) {
                    Text("Mot de passe oublié ?", color = MossDark)
                }
            }

            Spacer(modifier = Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                HorizontalDivider(modifier = Modifier.weight(1f))
                Text("  ou  ", color = MossDark, style = MaterialTheme.typography.bodyMedium)
                HorizontalDivider(modifier = Modifier.weight(1f))
            }
            Spacer(modifier = Modifier.height(8.dp))

            OutlinedButton(
                onClick = {
                    scope.launch {
                        try {
                            supabase.auth.signInWith(GoogleProvider)
                        } catch (e: Exception) {
                            android.util.Log.e("PROBLEMLAB_DEBUG", "Erreur Google", e)
                            errorMessage = friendlyAuthError(e.message)
                        }
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
            ) {
                Image(
                    painterResource(R.drawable.ic_google_logo),
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(10.dp))
                Text("Continuer avec Google", color = Ink)
            }
        }
    }
}