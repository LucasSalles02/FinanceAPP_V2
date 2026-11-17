package com.financeapp.nativeapp

import android.os.Bundle
import android.util.Log
import com.financeapp.nativeapp.presentation.security.SecureActivity
import com.financeapp.nativeapp.presentation.security.UnlockScreen
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.produceState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.financeapp.nativeapp.data.local.AppDatabase
import com.financeapp.nativeapp.data.remote.FirebaseRemoteDataSourceImpl
import com.financeapp.nativeapp.data.remote.FinanceAccountManager
import com.financeapp.nativeapp.data.remote.GoogleSignInHelper
import androidx.lifecycle.lifecycleScope
import androidx.credentials.exceptions.GetCredentialCancellationException
import androidx.credentials.exceptions.GetCredentialException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout
import com.financeapp.nativeapp.data.repository.TransactionRepositoryImpl
import com.financeapp.nativeapp.presentation.add.AddTransactionScreen
import com.financeapp.nativeapp.presentation.add.AddViewModel
import com.financeapp.nativeapp.presentation.auth.AccountScreen
import com.financeapp.nativeapp.presentation.home.HomeScreen
import com.financeapp.nativeapp.presentation.home.HomeViewModel
import com.financeapp.nativeapp.presentation.profile.ProfileScreen
import com.financeapp.nativeapp.presentation.reminders.ReminderScreen
import com.financeapp.nativeapp.profile.ProfilePhotoStore
import com.financeapp.nativeapp.planning.FinancialPlanRepository
import com.financeapp.nativeapp.presentation.theme.*
import com.financeapp.nativeapp.sync.ConnectivityObserver
import com.financeapp.nativeapp.sync.NetworkObserver
import com.financeapp.nativeapp.sync.SyncManager
import com.financeapp.nativeapp.sync.OfflineMode

class MainActivity : SecureActivity() {
    private lateinit var accountManager: FinanceAccountManager
    private var offlineMode by mutableStateOf(false)
    private var darkTheme by mutableStateOf(true)
    private var showAccount by mutableStateOf(true)
    private var startWithRegister by mutableStateOf(false)
    private var googleBusy by mutableStateOf(false)
    private var googleMessage by mutableStateOf<String?>(null)
    private var googleLinkedThisSession by mutableStateOf(false)

    /**
     * Mantem a operacao de autenticacao vinculada a Activity, nao ao composable.
     * A UI externa do Google pode executar onPause/onStop na Activity; a
     * SecureActivity protege a janela durante essa etapa sem trocar de tela.
     */
    private fun startGoogleAuthentication(link: Boolean, onSuccess: () -> Unit) {
        if (googleBusy || accountManager.busy) return
        googleBusy = true
        googleMessage = "Abrindo seletor de contas Google..."
        beginExternalCredentialFlow()
        lifecycleScope.launch {
            var stage = "selecionar_conta"
            try {
                // Um provedor Google que nao responde nao pode manter a tela
                // carregando para sempre. O timeout cancela o seletor.
                val token = withTimeout(45_000L) {
                    GoogleSignInHelper.requestIdToken(this@MainActivity)
                }
                stage = "firebase_auth"
                googleMessage = "Validando sua conta no Firebase..."
                // Nao cancelar automaticamente uma operacao de login iniciada
                // no Firebase: ela pode concluir no servidor e trocar o UID.
                // FinanceAccountManager valida e protege a associacao local.
                val ok = if (link) accountManager.linkGoogleToCurrentAccount(token)
                    else accountManager.signInWithGoogle(token)
                if (ok) {
                    googleMessage = if (link) "Conta Google vinculada com sucesso." else null
                    if (link) googleLinkedThisSession = true
                    onSuccess()
                } else {
                    googleMessage = accountManager.error ?: "Nao foi possivel autenticar com o Google."
                }
            } catch (_: GetCredentialCancellationException) {
                googleMessage = "Selecao da conta Google cancelada. Tente novamente."
            } catch (_: TimeoutCancellationException) {
                googleMessage = "O seletor de contas Google nao respondeu em 45 segundos. Tente novamente."
            } catch (e: GetCredentialException) {
                Log.w("FinanceAPPGoogle", "Erro no Credential Manager: ${e.javaClass.simpleName}", e)
                googleMessage = "Nao foi possivel abrir a conta Google (${e.javaClass.simpleName}). Verifique o Google Play Services."
            } catch (e: CancellationException) {
                Log.w("FinanceAPPGoogle", "Autenticacao cancelada pelo ciclo de vida em $stage")
                throw e
            } catch (e: Exception) {
                Log.e("FinanceAPPGoogle", "Falha na etapa $stage", e)
                googleMessage = "Falha em $stage: ${e.localizedMessage ?: e.javaClass.simpleName}"
            } finally {
                finishExternalCredentialFlow()
                googleBusy = false
            }
        }
    }

    override fun onDestroy() {
        if (::accountManager.isInitialized) accountManager.close()
        super.onDestroy()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Configuração de Dependências Manuais (Service Locator Pattern)
        // Em um app de produção usaríamos Hilt (Dagger)
        accountManager = FinanceAccountManager(applicationContext)
        offlineMode = OfflineMode.isEnabled(this)
        darkTheme = ThemePreferences.isDark(this)
        val database = AppDatabase.getDatabase(this)
        val remoteSource = FirebaseRemoteDataSourceImpl()
        val networkObserver = ConnectivityObserver(this)
        val syncManager = SyncManager(this, networkObserver)
        val repository = TransactionRepositoryImpl(
            database.transactionDao(), remoteSource,
            database.pendingDeletionDao(), database.financePlanDao(),
            onLocalChange = syncManager::enqueueSync
        )
        // Mantem a sincronizacao online e das exclusoes de parcelas.
        // A primeira abertura da nova versao inicia o envio dos registros locais,
        // inclusive os que foram marcados como sincronizados no Firebase antigo.
        syncManager.enqueueSync()
        val financialPlans = FinancialPlanRepository(database.financePlanDao(), repository) {
            syncManager.enqueueSync()
        }

        setContent {
            FinanceAppTheme(darkTheme = darkTheme) {
                val isOnline by networkObserver.observe().collectAsState(
                    initial = NetworkObserver.Status.Unavailable
                )

                val current = accountManager.user
                val localGuestUid = accountManager.localGuestUid
                val activeUid = current?.uid ?: localGuestUid
                if (!unlocked) {
                    UnlockScreen(message, pending, credentialAvailable,
                        onUnlock = ::authenticate, onCredential = ::authenticateCredential)
                } else if (activeUid == null ||
                    (current != null && !accountManager.ownsCurrentUser()) ||
                    (showAccount && current?.isAnonymous == true)) {
                    AccountScreen(
                        account = accountManager,
                        onConnected = { showAccount = false; syncManager.enqueueSync() },
                        onContinueGuest = { showAccount = false; syncManager.enqueueSync() },
                        onBack = if (current?.isAnonymous == true) ({ showAccount = false }) else null,
                        startWithRegister = startWithRegister,
                        googleBusy = googleBusy,
                        googleMessage = googleMessage,
                        onGoogleLogin = {
                            startGoogleAuthentication(link = false) {
                                showAccount = false
                                syncManager.enqueueSync()
                            }
                        }
                    )
                } else key(activeUid) { FinanceAppNavigation(
                    repository = repository,
                    financialPlans = financialPlans,
                    isOnline = isOnline == NetworkObserver.Status.Available,
                    user = current,
                    localGuestUid = localGuestUid,
                    account = accountManager,
                    offlineMode = offlineMode,
                    darkTheme = darkTheme,
                    onDarkThemeChange = { enabled ->
                        ThemePreferences.setDark(this@MainActivity, enabled)
                        darkTheme = enabled
                    },
                    onPhotoPickerOpening = ::beginExternalCredentialFlow,
                    onPhotoPickerClosed = ::finishExternalCredentialFlow,
                    onOfflineModeChange = { enabled ->
                        OfflineMode.setEnabled(this@MainActivity, enabled)
                        offlineMode = enabled
                        if (!enabled) syncManager.enqueueSync()
                    },
                    onCreateAccount = { startWithRegister = true; showAccount = true },
                    onSync = syncManager::enqueueSync,
                    onLogout = {
                        if (localGuestUid != null) accountManager.exitLocalGuest()
                        else accountManager.logout()
                        googleMessage = null
                        googleLinkedThisSession = false
                        showAccount = true
                        startWithRegister = false
                    },
                    googleBusy = googleBusy,
                    googleMessage = googleMessage,
                    googleLinkedThisSession = googleLinkedThisSession,
                    onLinkGoogle = { startGoogleAuthentication(link = true) {} }
                ) }
            }
        }
    }
}

sealed class Screen(val route: String, val label: String, val icon: androidx.compose.ui.graphics.vector.ImageVector) {
    object Home : Screen("home", "Extrato", Icons.Default.Home)
    object Profile : Screen("profile", "Perfil", Icons.Default.Person)
    object Reminders : Screen("reminders", "Lembretes", Icons.Default.Notifications)
    object Add : Screen("add", "Nova Transação", Icons.Default.Add) // Não mostrado na NavBar
}

@Composable
fun FinanceAppNavigation(
    repository: TransactionRepositoryImpl,
    financialPlans: FinancialPlanRepository,
    isOnline: Boolean,
    user: com.google.firebase.auth.FirebaseUser?,
    localGuestUid: String?,
    account: FinanceAccountManager,
    offlineMode: Boolean,
    darkTheme: Boolean,
    onDarkThemeChange: (Boolean) -> Unit,
    onPhotoPickerOpening: () -> Unit,
    onPhotoPickerClosed: () -> Unit,
    onOfflineModeChange: (Boolean) -> Unit,
    onCreateAccount: () -> Unit,
    onSync: () -> Unit,
    onLogout: () -> Unit,
    googleBusy: Boolean,
    googleMessage: String?,
    googleLinkedThisSession: Boolean,
    onLinkGoogle: () -> Unit
) {
    val navController = rememberNavController()
    // Um único estado de foto por conta. A alteração no Perfil atualiza também o Extrato.
    // A navegação já é recriada com key(current.uid) ao trocar de usuário.
    val photoContext = LocalContext.current
    val profileUid = user?.uid ?: localGuestUid
    var photoRevision by remember { mutableIntStateOf(0) }
    val profilePhoto by produceState<ImageBitmap?>(
        initialValue = null,
        key1 = profileUid,
        key2 = photoRevision
    ) {
        value = profileUid?.let { ProfilePhotoStore.load(photoContext, it)?.asImageBitmap() }
    }

    // Instanciando ViewModels através das fábricas manuais
    val homeViewModel: HomeViewModel = viewModel(
        factory = HomeViewModel.Factory(repository)
    )

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        bottomBar = {
            NavigationBar(
                containerColor = MaterialTheme.colorScheme.background,
                contentColor = MaterialTheme.colorScheme.onSurface,
                tonalElevation = 0.dp
            ) {
                val navBackStackEntry by navController.currentBackStackEntryAsState()
                val currentDestination = navBackStackEntry?.destination
                
                val items = listOf(Screen.Home, Screen.Reminders, Screen.Profile)
                
                items.forEach { screen ->
                    val selected = currentDestination?.hierarchy?.any { it.route == screen.route } == true
                    NavigationBarItem(
                        icon = {
                            Icon(
                                screen.icon,
                                contentDescription = screen.label
                            )
                        },
                        label = {
                            Text(
                                screen.label,
                                fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal
                            )
                        },
                        selected = selected,
                        onClick = {
                            navController.navigate(screen.route) {
                                popUpTo(navController.graph.findStartDestination().id) {
                                    saveState = true
                                }
                                launchSingleTop = true
                                restoreState = true
                            }
                        },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = MaterialTheme.colorScheme.primary,
                            selectedTextColor = MaterialTheme.colorScheme.primary,
                            indicatorColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
                            unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                            unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    )
                }
            }
        }
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = Screen.Home.route,
            modifier = Modifier.padding(innerPadding)
        ) {
            composable(Screen.Home.route) {
                HomeScreen(
                    viewModel = homeViewModel,
                    financialPlans = financialPlans,
                    isOnline = isOnline && !offlineMode,
                    userName = if (localGuestUid != null) "Convidado offline"
                        else user?.displayName ?: user?.email?.substringBefore('@') ?: "Usuário",
                    profilePhoto = profilePhoto,
                    onNavigateToProfile = {
                        navController.navigate(Screen.Profile.route) {
                            popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                            launchSingleTop = true
                            restoreState = true
                        }
                    },
                    onNavigateToAdd = { navController.navigate(Screen.Add.route) }
                )
            }
            
            composable(Screen.Reminders.route) {
                ReminderScreen()
            }

            composable(Screen.Profile.route) {
                ProfileScreen(
                    viewModel = homeViewModel,
                    isOnline = isOnline,
                    user = user,
                    localGuestUid = localGuestUid,
                    profilePhoto = profilePhoto,
                    onPhotoChanged = { photoRevision++ },
                    account = account,
                    offlineMode = offlineMode,
                    darkTheme = darkTheme,
                    onDarkThemeChange = onDarkThemeChange,
                    onPhotoPickerOpening = onPhotoPickerOpening,
                    onPhotoPickerClosed = onPhotoPickerClosed,
                    onOfflineModeChange = onOfflineModeChange,
                    onCreateAccount = onCreateAccount,
                    onSync = onSync,
                    onLogout = onLogout,
                    googleBusy = googleBusy,
                    googleMessage = googleMessage,
                    googleLinkedThisSession = googleLinkedThisSession,
                    onLinkGoogle = onLinkGoogle
                )
            }
            
            composable(Screen.Add.route) {
                val addViewModel: AddViewModel = viewModel(
                    factory = AddViewModel.Factory(repository, financialPlans)
                )
                AddTransactionScreen(
                    viewModel = addViewModel,
                    onBack = { navController.popBackStack() },
                    onPlanSaved = { navController.popBackStack() }
                )
            }
        }
    }
}
