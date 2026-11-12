package com.hassandev.problemlab

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import compose.icons.FontAwesomeIcons
import compose.icons.fontawesomeicons.Solid
import compose.icons.fontawesomeicons.solid.Cog
import compose.icons.fontawesomeicons.solid.QuestionCircle
import compose.icons.fontawesomeicons.solid.SignOutAlt
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.auth.handleDeeplinks
import io.github.jan.supabase.auth.status.SessionStatus
import kotlinx.coroutines.launch

// Devient true quand l'utilisateur arrive depuis un lien « mot de passe oublié »
var recoveryEnCours by mutableStateOf(false)

// Contient l'email de l'utilisateur qui doit encore saisir son code de confirmation
var emailAConfirmer by mutableStateOf<String?>(null)

// Identifiant du compte qui vient de terminer le didacticiel (évite de le réafficher tout de suite)
var onboardingTermineId by mutableStateOf<String?>(null)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        traiterLien(intent)
        setContent {
            ProblemLabTheme {
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    RootScreen(modifier = Modifier.padding(innerPadding))
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        traiterLien(intent)
    }

    private fun traiterLien(intent: Intent) {
        if (intent.data?.fragment?.contains("type=recovery") == true) {
            recoveryEnCours = true
        }
        supabase.handleDeeplinks(intent)
    }
}

@Composable
fun RootScreen(modifier: Modifier = Modifier) {
    val sessionStatus by supabase.auth.sessionStatus.collectAsState()
    val emailEnAttente = emailAConfirmer

    when {
        recoveryEnCours -> ResetPasswordScreen(onDone = { recoveryEnCours = false })
        emailEnAttente != null -> VerifyEmailScreen(
            email = emailEnAttente,
            onVerified = { emailAConfirmer = null },
            onBack = { emailAConfirmer = null }
        )
        sessionStatus is SessionStatus.Authenticated -> {
            val user = supabase.auth.currentUserOrNull()
            val didacticielVu = user?.userMetadata?.get("onboarding_done")?.toString() == "true"
            if (user != null && !didacticielVu && onboardingTermineId != user.id) {
                OnboardingScreen(onFinished = { onboardingTermineId = user.id })
            } else {
                HomeWithSidebar(modifier = modifier)
            }
        }
        sessionStatus is SessionStatus.NotAuthenticated -> AuthScreen()
        else -> {
            Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        }
    }
}

// Les écrans accessibles depuis la barre de navigation du bas
sealed class MainTab {
    object Home : MainTab()
    object Observations : MainTab()
    object AgentIa : MainTab()
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeWithSidebar(modifier: Modifier = Modifier) {
    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    val context = LocalContext.current

    val metadata = supabase.auth.currentUserOrNull()?.userMetadata
    val prenomSaisi = metadata?.get("prenom")?.toString()?.trim('"')?.trim()
    val prenomGoogle = (metadata?.get("full_name") ?: metadata?.get("name"))
        ?.toString()?.trim('"')?.trim()?.substringBefore(" ")
    val prenom = listOf(prenomSaisi, prenomGoogle).firstOrNull { !it.isNullOrBlank() } ?: "toi"
    val email = supabase.auth.currentUserOrNull()?.email ?: ""

    // Onglet actif de la barre du bas
    var currentTab by remember { mutableStateOf<MainTab>(MainTab.Home) }
    // Affiche l'écran "Nouvelle observation" par-dessus ; rouvert automatiquement si un brouillon était en cours
    var showNewObservation by rememberSaveable { mutableStateOf(DraftStore.isOpen(context)) }
    // Observation ouverte dans l'écran de détail
    var selectedObservation by remember { mutableStateOf<Observation?>(null) }
    // Observation à présélectionner dans l'Agent IA (utilisée à l'étape suivante)
    var agentPreselectedId by remember { mutableStateOf<String?>(null) }
    // Incrémenté pour forcer le rafraîchissement de la liste après un enregistrement ou une suppression
    var observationsRefreshTrigger by remember { mutableIntStateOf(0) }

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            ModalDrawerSheet(drawerContainerColor = Paper) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(16.dp)
                ) {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                        IconButton(onClick = { scope.launch { drawerState.close() } }) {
                            Icon(Icons.Filled.Close, contentDescription = "Fermer", tint = MossDark)
                        }
                    }

                    Text("Bonjour $prenom", style = MaterialTheme.typography.titleLarge, color = Ink)
                    Text(email, style = MaterialTheme.typography.bodyMedium, color = MossDark)
                    Spacer(modifier = Modifier.height(24.dp))

                    NavigationDrawerItem(
                        label = { Text("Paramètres") },
                        icon = { Icon(FontAwesomeIcons.Solid.Cog, contentDescription = null, modifier = Modifier.size(18.dp)) },
                        selected = false,
                        onClick = { /* à définir plus tard */ }
                    )
                    NavigationDrawerItem(
                        label = { Text("FAQ") },
                        icon = { Icon(FontAwesomeIcons.Solid.QuestionCircle, contentDescription = null, modifier = Modifier.size(18.dp)) },
                        selected = false,
                        onClick = { /* à définir plus tard */ }
                    )

                    Spacer(modifier = Modifier.weight(1f))

                    NavigationDrawerItem(
                        label = { Text("Se déconnecter") },
                        icon = { Icon(FontAwesomeIcons.Solid.SignOutAlt, contentDescription = null, modifier = Modifier.size(18.dp), tint = Terracotta) },
                        selected = false,
                        onClick = {
                            salutationUserId = null
                            scope.launch { supabase.auth.signOut() }
                        }
                    )
                }
            }
        }
    ) {
        if (showNewObservation) {
            // Écran plein écran, sans barre du bas, avec bouton retour intégré
            NewObservationScreen(
                onBack = {
                    showNewObservation = false
                    observationsRefreshTrigger++
                }
            )
        } else if (selectedObservation != null) {
            ObservationDetailScreen(
                observation = selectedObservation!!,
                onBack = { selectedObservation = null },
                onDeleted = {
                    selectedObservation = null
                    observationsRefreshTrigger++
                },
                modifier = modifier
            )
        } else {
            Scaffold(
                modifier = modifier.fillMaxSize(),
                topBar = {
                    TopAppBar(
                        title = { },
                        navigationIcon = {
                            IconButton(onClick = { scope.launch { drawerState.open() } }) {
                                Icon(Icons.Filled.Menu, contentDescription = "Menu", tint = Terracotta)
                            }
                        },
                        colors = TopAppBarDefaults.topAppBarColors(containerColor = Paper)
                    )
                },
                bottomBar = {
                    NavigationBar(containerColor = Paper) {
                        NavigationBarItem(
                            selected = currentTab == MainTab.Home,
                            onClick = { currentTab = MainTab.Home },
                            icon = { Icon(Icons.Filled.Home, contentDescription = "Accueil") },
                            label = { Text("Accueil") },
                            colors = NavigationBarItemDefaults.colors(
                                selectedIconColor = Terracotta,
                                selectedTextColor = Terracotta,
                                indicatorColor = Paper
                            )
                        )
                        NavigationBarItem(
                            selected = currentTab == MainTab.Observations,
                            onClick = { currentTab = MainTab.Observations },
                            icon = { Icon(Icons.AutoMirrored.Filled.List, contentDescription = "Observations") },
                            label = { Text("Observations") },
                            colors = NavigationBarItemDefaults.colors(
                                selectedIconColor = Terracotta,
                                selectedTextColor = Terracotta,
                                indicatorColor = Paper
                            )
                        )
                        NavigationBarItem(
                            selected = currentTab == MainTab.AgentIa,
                            onClick = { currentTab = MainTab.AgentIa },
                            icon = { Icon(Icons.Filled.Star, contentDescription = "Agent IA") },
                            label = { Text("Agent IA") },
                            colors = NavigationBarItemDefaults.colors(
                                selectedIconColor = Terracotta,
                                selectedTextColor = Terracotta,
                                indicatorColor = Paper
                            )
                        )
                    }
                },
                floatingActionButton = {
                    if (currentTab == MainTab.Home) {
                        FloatingActionButton(
                            onClick = { showNewObservation = true },
                            containerColor = Terracotta
                        ) {
                            Icon(Icons.Filled.Add, contentDescription = "Nouvelle observation", tint = Paper)
                        }
                    }
                }
            ) { innerPadding ->
                Box(
                    modifier = Modifier
                        .padding(innerPadding)
                        .background(Paper)
                        .fillMaxSize()
                ) {
                    when (currentTab) {
                        MainTab.AgentIa -> AgentIaScreen(
                            preselectedObservationId = agentPreselectedId,
                            onPreselectionConsumed = { agentPreselectedId = null },
                            onOpenObservation = { selectedObservation = it }
                        )
                        MainTab.Home -> HomeScreen(
                            prenom = prenom,
                            onOpenObservation = { selectedObservation = it },
                            onSeeAll = { currentTab = MainTab.Observations }
                        )
                        MainTab.Observations -> ObservationsListScreen(
                            onAddClick = { showNewObservation = true },
                            refreshTrigger = observationsRefreshTrigger,
                            onOpenObservation = { selectedObservation = it }
                        )
                    }
                }
            }
        }
    }
}