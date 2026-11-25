package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.credentials.CredentialManager
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.example.ai.VillageAiReconstructor
import com.example.auth.AuthScreen
import com.example.auth.attemptAutoSignIn
import com.example.auth.signOut
import com.example.data.VillageRepository
import com.example.firebase.configureAppCheck
import com.example.model.GraphicsQuality
import com.example.model.UiState
import com.example.model.UserSettings
import com.example.ui.screens.CharacterSettingsScreen
import com.example.ui.screens.ExploreScreen
import com.example.ui.screens.GraphicsSettingsScreen
import com.example.ui.screens.HomeScreen
import com.example.ui.screens.MapLocationsScreen
import com.example.ui.screens.ScannerScreen
import com.example.ui.screens.VillageBuilderScreen
import com.example.ui.theme.AmberGold
import com.example.ui.theme.MurgiTolaTheme
import com.example.ui.theme.ObsidianBg
import com.example.viewmodel.MurgiTolaViewModel
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.auth
import com.google.firebase.firestore.FirebaseFirestore

const val ROUTE_HOME = "home"
const val ROUTE_EXPLORE = "explore"
const val ROUTE_BUILDER = "village_builder"
const val ROUTE_SCANNER = "scanner_360"
const val ROUTE_MAP = "saved_locations_map"
const val ROUTE_GRAPHICS = "graphics_settings"
const val ROUTE_CHARACTER = "character_settings"

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // STEP 1: Configure Firebase App Check with launch intent before UI or ViewModels
        configureAppCheck(this, intent)

        enableEdgeToEdge()
        setContent {
            MurgiTolaTheme {
                Surface(modifier = Modifier.fillMaxSize(), color = ObsidianBg) {
                    AppNavigation()
                }
            }
        }
    }
}

@Composable
fun AppNavigation() {
    val context = androidx.compose.ui.platform.LocalContext.current
    val scope = rememberCoroutineScope()
    val credentialManager = remember(context) { CredentialManager.create(context) }

    var currentUser by remember { mutableStateOf(Firebase.auth.currentUser) }
    var checkingStartupAuth by remember { mutableStateOf(currentUser == null) }

    DisposableEffect(Unit) {
        val listener = FirebaseAuth.AuthStateListener { auth ->
            currentUser = auth.currentUser
        }
        Firebase.auth.addAuthStateListener(listener)
        onDispose { Firebase.auth.removeAuthStateListener(listener) }
    }

    LaunchedEffect(Unit) {
        if (currentUser == null) {
            attemptAutoSignIn(
                context = context,
                credentialManager = credentialManager,
                onAuthSuccess = {
                    currentUser = Firebase.auth.currentUser
                    checkingStartupAuth = false
                },
                onUnauthenticated = {
                    checkingStartupAuth = false
                },
                scope = scope
            )
        } else {
            checkingStartupAuth = false
        }
    }

    val user = currentUser
    if (checkingStartupAuth) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(ObsidianBg),
            contentAlignment = Alignment.Center
        ) {
            CircularProgressIndicator(color = AmberGold)
        }
    } else if (user == null) {
        AuthScreen(
            onAuthSuccess = { currentUser = Firebase.auth.currentUser }
        )
    } else {
        val uid = user.uid
        val displayName = user.displayName ?: "Murgi Tola Explorer"
        val userEmail = user.email
        androidx.compose.runtime.key(uid) {
            AuthenticatedMurgiTolaApp(
                currentUserId = uid,
                userDisplayName = displayName,
                userEmail = userEmail,
                onSignOut = {
                    signOut(credentialManager, scope) {
                        currentUser = null
                    }
                }
            )
        }
    }
}

@Composable
private fun AuthenticatedMurgiTolaApp(
    currentUserId: String,
    userDisplayName: String,
    userEmail: String?,
    onSignOut: () -> Unit
) {
    val viewModel: MurgiTolaViewModel = viewModel(
        key = currentUserId,
        factory = viewModelFactory {
            initializer {
                val app = checkNotNull(this[APPLICATION_KEY]) {
                    "APPLICATION_KEY missing from CreationExtras"
                }
                val databaseId = app.getString(R.string.firestore_database_id)
                val db = FirebaseFirestore.getInstance(databaseId)
                val repository = VillageRepository(db = db, appContext = app)
                val aiReconstructor = VillageAiReconstructor()
                MurgiTolaViewModel(
                    repository = repository,
                    aiReconstructor = aiReconstructor,
                    currentUserId = currentUserId,
                    userDisplayName = userDisplayName
                )
            }
        }
    )

    val navController = rememberNavController()
    val snackbarHostState = remember { SnackbarHostState() }

    val settingsState by viewModel.userSettingsState.collectAsStateWithLifecycle()
    val villagesState by viewModel.villagesState.collectAsStateWithLifecycle()
    val activeLocations by viewModel.activeLocations.collectAsStateWithLifecycle()
    val activeVillageId by viewModel.activeVillageId.collectAsStateWithLifecycle()
    val selectedExploreLocationId by viewModel.selectedExploreLocationId.collectAsStateWithLifecycle()
    val scannerState by viewModel.scannerState.collectAsStateWithLifecycle()
    val actionMessage by viewModel.actionMessage.collectAsStateWithLifecycle()

    LaunchedEffect(actionMessage) {
        val msg = actionMessage
        if (!msg.isNullOrBlank()) {
            snackbarHostState.showSnackbar(msg)
            viewModel.clearMessage()
        }
    }

    val settings = (settingsState as? UiState.Success)?.data ?: UserSettings(
        userId = currentUserId,
        displayName = userDisplayName
    )
    val villages = (villagesState as? UiState.Success)?.data ?: emptyList()
    val activeVillage = villages.firstOrNull { it.id == activeVillageId } ?: villages.firstOrNull()

    Scaffold(
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
        containerColor = ObsidianBg
    ) { _ ->
        NavHost(
            navController = navController,
            startDestination = ROUTE_HOME,
            modifier = Modifier.fillMaxSize()
        ) {
            composable(ROUTE_HOME) {
                HomeScreen(
                    activeVillage = activeVillage,
                    locations = activeLocations,
                    settings = settings,
                    userEmail = userEmail,
                    onExploreClick = {
                        viewModel.setExploreLocation(null)
                        navController.navigate(ROUTE_EXPLORE)
                    },
                    onCreateVillageClick = { navController.navigate(ROUTE_BUILDER) },
                    onScanLocationClick = { navController.navigate(ROUTE_SCANNER) },
                    onSavedLocationsClick = { navController.navigate(ROUTE_MAP) },
                    onGraphicsSettingsClick = { navController.navigate(ROUTE_GRAPHICS) },
                    onCharacterSettingsClick = { navController.navigate(ROUTE_CHARACTER) },
                    onSignOutClick = onSignOut
                )
            }

            composable(ROUTE_EXPLORE) {
                ExploreScreen(
                    locations = activeLocations,
                    initialLocationId = selectedExploreLocationId,
                    settings = settings,
                    onSettingsUpdate = { updated -> viewModel.updateSettings(updated) },
                    onLocationExplored = { locId -> viewModel.markExplored(locId) },
                    onBack = { navController.popBackStack() }
                )
            }

            composable(ROUTE_BUILDER) {
                VillageBuilderScreen(
                    villages = villages,
                    activeVillageId = activeVillageId,
                    activeLocations = activeLocations,
                    onSelectVillage = { id -> viewModel.selectVillage(id) },
                    onCreateVillage = { name, desc -> viewModel.createVillage(name, desc) },
                    onRenameVillage = { id, name, desc -> viewModel.renameVillage(id, name, desc) },
                    onConnectLocations = { a, b -> viewModel.connectLocations(a, b) },
                    onOpenScanner = { navController.navigate(ROUTE_SCANNER) },
                    onOpenExplore = {
                        viewModel.setExploreLocation(null)
                        navController.navigate(ROUTE_EXPLORE)
                    },
                    onBack = { navController.popBackStack() }
                )
            }

            composable(ROUTE_SCANNER) {
                ScannerScreen(
                    scannerState = scannerState,
                    existingLocations = activeLocations,
                    maxTextureSize = GraphicsQuality.fromRaw(settings.graphicsQuality).maxTextureSize,
                    onMediaPicked = { uris, mediaType, maxDim ->
                        viewModel.onPhotoUrisSelected(uris, mediaType, maxDim)
                    },
                    onCameraPhotoCaptured = { bmp -> viewModel.onCapturedCameraBitmap(bmp) },
                    onAnalyzeWithAi = { name -> viewModel.runAiSceneReconstruction(name) },
                    onSaveLocation = { name, category, notes, isHouse, isFav, panoOnly, connectId ->
                        viewModel.saveScannedLocation(
                            name = name,
                            category = category,
                            notes = notes,
                            markedAsHouse = isHouse,
                            favorite = isFav,
                            panoramaOnly = panoOnly,
                            connectToLocationId = connectId
                        )
                    },
                    onClearScanner = { viewModel.clearScannerMedia() },
                    onExploreSaved = {
                        viewModel.setExploreLocation(activeLocations.lastOrNull()?.id)
                        navController.navigate(ROUTE_EXPLORE)
                    },
                    onBack = { navController.popBackStack() }
                )
            }

            composable(ROUTE_MAP) {
                MapLocationsScreen(
                    locations = activeLocations,
                    onExploreLocation = { loc ->
                        viewModel.setExploreLocation(loc.id)
                        navController.navigate(ROUTE_EXPLORE)
                    },
                    onToggleHouse = { loc -> viewModel.toggleHouseMark(loc) },
                    onToggleFavorite = { loc -> viewModel.toggleFavorite(loc) },
                    onDeleteLocation = { loc -> viewModel.deleteLocation(loc.id) },
                    onBack = { navController.popBackStack() }
                )
            }

            composable(ROUTE_GRAPHICS) {
                GraphicsSettingsScreen(
                    settings = settings,
                    onUpdateSettings = { updated, msg -> viewModel.updateSettings(updated, msg) },
                    onBack = { navController.popBackStack() }
                )
            }

            composable(ROUTE_CHARACTER) {
                CharacterSettingsScreen(
                    settings = settings,
                    onUpdateSettings = { updated, msg -> viewModel.updateSettings(updated, msg) },
                    onBack = { navController.popBackStack() }
                )
            }
        }
    }
}
