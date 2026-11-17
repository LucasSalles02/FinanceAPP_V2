package com.financeapp.nativeapp.data.remote

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import com.google.firebase.FirebaseNetworkException
import java.util.UUID
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.financeapp.nativeapp.data.local.AppDatabase
import com.google.firebase.auth.EmailAuthProvider
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.auth.UserProfileChangeRequest
import kotlinx.coroutines.tasks.await

/**
 * Mantem a identidade do Firestore ao converter a conta anonima em conta permanente.
 * Uma instalacao com dados locais NAO pode trocar silenciosamente de UID.
 */
class FinanceAccountManager(private val context: Context) {
    private val auth = FirebaseAuth.getInstance()
    private val prefs = context.getSharedPreferences("financeapp_account_owner", Context.MODE_PRIVATE)
    // O convidado local nunca usa o UID de uma conta do Firebase nem pode ler seus dados.
    private val guestPrefs = context.getSharedPreferences("financeapp_local_guest_session", Context.MODE_PRIVATE)

    var localGuestUid by mutableStateOf<String?>(
        if (auth.currentUser == null && guestPrefs.getBoolean("active", false))
            guestPrefs.getString("guest_uid", null) else null
    )
        private set

    private fun activateLocalGuest() {
        check(auth.currentUser == null) { "Uma conta Firebase já está conectada." }
        val uid = guestPrefs.getString("guest_uid", null)?.takeIf { it.startsWith("local_guest_") }
            ?: "local_guest_${UUID.randomUUID()}"
        check(guestPrefs.edit().putString("guest_uid", uid).putBoolean("active", true).commit()) {
            "Não foi possível iniciar o modo offline."
        }
        localGuestUid = uid
        AccountScope.activateLocalGuest(uid)
        com.financeapp.nativeapp.reminders.ReminderSession.update(context, uid)
        com.financeapp.nativeapp.reminders.ReminderScheduler.restorePending(context)
    }

    fun enterLocalGuest(): Boolean {
        error = null
        return runCatching { activateLocalGuest() }.onFailure {
            error = it.localizedMessage ?: "Não foi possível iniciar o modo offline."
        }.isSuccess
    }

    fun exitLocalGuest() {
        if (localGuestUid == null) return
        check(guestPrefs.edit().putBoolean("active", false).commit()) {
            "Não foi possível encerrar o modo offline."
        }
        localGuestUid = null
        AccountScope.clearLocalGuest()
        com.financeapp.nativeapp.reminders.ReminderSession.update(context, auth.currentUser?.uid)
    }

    private fun hasValidatedNetwork(): Boolean {
        val manager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
            ?: return false
        val capabilities = manager.getNetworkCapabilities(manager.activeNetwork) ?: return false
        return capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
            capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
    }

    var user by mutableStateOf(auth.currentUser)
        private set
    var busy by mutableStateOf(false)
        private set
    var error by mutableStateOf<String?>(null)
        private set

    private val listener = FirebaseAuth.AuthStateListener {
        // Um login real encerra a sessão de convidado, sem mesclar os dois históricos.
        if (it.currentUser != null && localGuestUid != null) {
            guestPrefs.edit().putBoolean("active", false).commit()
            localGuestUid = null
        }
        if (it.currentUser == null && localGuestUid != null) {
            AccountScope.activateLocalGuest(localGuestUid!!)
            com.financeapp.nativeapp.reminders.ReminderSession.update(context, localGuestUid)
        } else {
            AccountScope.onAuthChanged(it.currentUser?.uid)
            com.financeapp.nativeapp.reminders.ReminderSession.update(context, it.currentUser?.uid)
        }
        user = it.currentUser
    }

    init {
        // O V9 ja tinha um usuario anonimo; vincule-o ao banco local antes de qualquer troca.
        auth.currentUser?.let { existing ->
            if (prefs.getString("uid", null) == null) prefs.edit().putString("uid", existing.uid).commit()
        }
        if (auth.currentUser == null && localGuestUid != null) {
            AccountScope.activateLocalGuest(localGuestUid!!)
            com.financeapp.nativeapp.reminders.ReminderSession.update(context, localGuestUid)
        } else {
            AccountScope.onAuthChanged(auth.currentUser?.uid)
            auth.currentUser?.let { com.financeapp.nativeapp.reminders.ReminderSession.update(context, it.uid) }
        }
        auth.addAuthStateListener(listener)
    }

    fun clearError() { error = null }
    // Cada conta acessa somente seus dados filtrados pelo UID, e não o cache de outra.
    fun ownsCurrentUser(): Boolean = user?.uid != null &&
        user?.uid == auth.currentUser?.uid
    fun hasStoredOwner(): Boolean = prefs.getString("uid", null) != null
    fun logout() {
        require(user?.isAnonymous == false) { "Proteja seu acesso criando uma conta antes de sair." }
        androidx.work.WorkManager.getInstance(context)
            .cancelUniqueWork(com.financeapp.nativeapp.sync.SyncWorker.WORK_NAME)
        com.financeapp.nativeapp.reminders.ReminderSession.update(context, null)
        auth.signOut()
        user = null
        AccountScope.onAuthChanged(null)
    }
    fun close() { auth.removeAuthStateListener(listener) }

    private fun claim(uid: String) {
        // Este marcador é o DONO DOS DADOS LEGADOS da V11, não o usuário ativo.
        // Não deve ser sobrescrito ao entrar com outra conta.
        if (prefs.getString("uid", null) == null) {
            check(prefs.edit().putString("uid", uid).commit()) {
                "Falha ao identificar o proprietário do histórico anterior."
            }
        }
        // O login online encerra o modo offline sem migrar registros de outra conta.
        if (localGuestUid != null) {
            guestPrefs.edit().putBoolean("active", false).commit()
            localGuestUid = null
        }
        AccountScope.onAuthChanged(uid)
        com.financeapp.nativeapp.reminders.ReminderSession.update(context, uid)
        // Retomar alarmes pendentes quando o dono da conta ativa mudar.
        com.financeapp.nativeapp.reminders.ReminderScheduler.restorePending(context)
    }

    suspend fun asGuest(): Boolean = runAction {
        val existing = auth.currentUser
        when {
            existing != null -> {
                claim(existing.uid)
                user = existing
            }
            !hasValidatedNetwork() -> activateLocalGuest()
            else -> try {
                val signed = auth.signInAnonymously().await().user
                    ?: error("Firebase não retornou um usuário.")
                claim(signed.uid)
                user = signed
            } catch (_: FirebaseNetworkException) {
                // A opção de convidado funciona mesmo que o servidor esteja inacessível.
                activateLocalGuest()
            }
        }
    }

    suspend fun register(name: String, email: String, password: String): Boolean = runAction {
        check(localGuestUid == null) {
            "Saia do modo offline para criar uma conta. Seus dados locais serão preservados separadamente."
        }
        val n = name.trim()
        val e = email.trim()
        require(n.length >= 2) { "Informe seu nome (pelo menos 2 caracteres)." }
        require(android.util.Patterns.EMAIL_ADDRESS.matcher(e).matches()) { "Informe um e-mail valido." }
        require(password.length >= 6) { "A senha precisa ter pelo menos 6 caracteres." }
        val existing = auth.currentUser
        // O `if` completo precisa ser avaliado ANTES do Elvis. Sem os parenteses,
        // o Kotlin aplica `?:` apenas no ultimo ramo e infere FirebaseUser?,
        // causando erros ao usar registered.uid/updateProfile/reload.
        val registered: FirebaseUser = (if (existing?.isAnonymous == true) {
            // UID preservado: /users/{uid}/transactions continua pertencendo ao mesmo usuario.
            existing.linkWithCredential(EmailAuthProvider.getCredential(e, password)).await().user
        } else if (existing == null) {
            auth.createUserWithEmailAndPassword(e, password).await().user
        } else {
            throw IllegalStateException("Ja existe uma conta cadastrada nesta instalacao.")
        }) ?: kotlin.error("Firebase nao retornou o usuario registrado")
        claim(registered.uid)
        // A vinculacao da conta ja foi concluida; uma falha de nome nao desfaz o cadastro.
        runCatching {
            registered.updateProfile(UserProfileChangeRequest.Builder().setDisplayName(n).build()).await()
            registered.reload().await()
        }
        user = auth.currentUser
    }

    suspend fun login(email: String, password: String): Boolean = runAction {
        check(localGuestUid == null) { "Saia do modo offline antes de entrar em uma conta." }
        val e = email.trim()
        require(e.isNotEmpty() && password.isNotBlank()) { "Informe e-mail e senha." }
        val current = auth.currentUser
        if (current?.isAnonymous == true) {
            val uid = current.uid
            val database = AppDatabase.getDatabase(context)
            val hasLocalData = database.transactionDao().countTransactions(uid) > 0 ||
                database.financePlanDao().countPlans(uid) > 0 ||
                database.pendingDeletionDao().pending(uid).isNotEmpty() ||
                com.financeapp.nativeapp.reminders.ReminderStore.list(context).isNotEmpty()
            require(!hasLocalData) {
                "Existem dados na conta temporária. Use Criar conta para preservá-los antes de entrar em outra."
            }
        } else if (current != null) {
            error("Esta conta já está conectada. Saia antes de entrar em outra.")
        }
        val newUser = auth.signInWithEmailAndPassword(e, password).await().user
            ?: error("O Firebase não retornou o usuário.")
        claim(newUser.uid)
        user = newUser
    }

    /**
     * Na conta anonima, VINCULA o Google para preservar o UID e todos os documentos.
     * Em uma instalacao nova, permite login Google, mas jamais mistura caches de UIDs distintos.
     */
    suspend fun signInWithGoogle(idToken: String): Boolean = runAction {
        require(idToken.isNotBlank()) { "Nenhuma conta Google foi selecionada." }
        val current = auth.currentUser
        require(current == null || current.isAnonymous) {
            "Sua conta já está conectada. Use Vincular Google no Perfil."
        }
        if (current?.isAnonymous == true) {
            // Vincular ao convidado protege os seus registros e preserva o UID.
            val linked = current.linkWithCredential(
                GoogleAuthProvider.getCredential(idToken, null)
            ).await().user ?: error("O Firebase não retornou a conta vinculada.")
            claim(linked.uid)
            user = auth.currentUser
        } else {
            // Conta diferente ganha seu próprio conjunto de registros locais.
            val signed = auth.signInWithCredential(
                GoogleAuthProvider.getCredential(idToken, null)
            ).await().user ?: error("O Firebase não retornou a conta Google.")
            claim(signed.uid)
            user = auth.currentUser
        }
    }

    /**
     * Para quem ja usa e-mail/senha: associa Google ao MESMO UID, sem trocar de conta.
     * Se a Conta Google ja estiver vinculada a outro usuario, nao migra os dados automaticamente.
     */
    suspend fun linkGoogleToCurrentAccount(idToken: String): Boolean = runAction {
        val existing = auth.currentUser ?: error("Entre em sua conta antes de vincular o Google.")
        require(!existing.isAnonymous) { "Use 'Continuar com Google' para proteger sua conta temporaria." }
        require(ownsCurrentUser()) { "A conta atual nao corresponde aos dados deste aparelho." }
        require(existing.providerData.none { it.providerId == GoogleAuthProvider.PROVIDER_ID }) {
            "Sua conta ja esta vinculada ao Google."
        }
        val linked = existing.linkWithCredential(
            GoogleAuthProvider.getCredential(idToken, null)
        ).await().user ?: error("Nao foi possivel vincular a conta Google.")
        claim(linked.uid)
        // A vinculacao ja foi concluida. Falha ao recarregar perfil nao a desfaz.
        runCatching { linked.reload().await() }
        user = auth.currentUser
    }

    suspend fun resetPassword(email: String): Boolean = runAction {
        require(android.util.Patterns.EMAIL_ADDRESS.matcher(email.trim()).matches()) { "Informe um e-mail valido." }
        auth.sendPasswordResetEmail(email.trim()).await()
    }

    private suspend fun runAction(block: suspend () -> Unit): Boolean {
        if (busy) return false
        busy = true
        error = null
        return try {
            block()
            true
        } catch (e: Exception) {
            error = when (e) {
                is com.google.firebase.auth.FirebaseAuthUserCollisionException ->
                    "Esta conta ja esta associada a outro usuario. Para proteger seus dados, nao misturamos as contas."
                is com.google.firebase.auth.FirebaseAuthWeakPasswordException ->
                    "Escolha uma senha mais forte."
                is com.google.firebase.auth.FirebaseAuthInvalidCredentialsException ->
                    "E-mail ou senha invalidos."
                is FirebaseNetworkException ->
                    "Sem conexão com o Firebase. Se já estava conectado, volte sem sair da conta; caso contrário, use 'Continuar como convidado' para acessar o modo offline."
                else -> e.localizedMessage ?: "Nao foi possivel concluir. Confira sua conexao."
            }
            false
        } finally {
            busy = false
        }
    }
}
